param(
    [string]$ResultsDirectory = "results/windows-speaker-benchmark",
    [string]$AndroidResultsDirectory = "results/android-speaker-parity",
    [string]$SummaryDirectory = "results/speaker-parity-verification",
    [string]$Config = "config/speaker-benchmark.json",
    [double]$ScoreTolerance = 0.002,
    [double]$EmbeddingNormTolerance = 0.002,
    [double]$EmbeddingNormRelativeTolerance = 0.001,
    [double]$MinimumEmbeddingCosine = 0.999,
    [switch]$SkipBenchmarkRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Resolve-RepoPath([string]$Path) {
    if ([IO.Path]::IsPathRooted($Path)) {
        return [IO.Path]::GetFullPath($Path)
    }
    return [IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Get-ObjectProperty($Object, [string]$Name) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw "Missing JSON property '$Name'."
    }
    return $property.Value
}

function Get-OptionalObjectProperty($Object, [string]$Name) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Get-OnlyScore($Scores, [string]$Description) {
    $properties = @($Scores.PSObject.Properties)
    if ($properties.Count -ne 1) {
        throw "$Description must contain exactly one enrolled-speaker score; found $($properties.Count)."
    }
    return [PSCustomObject]@{
        SpeakerId = [string]$properties[0].Name
        Score = [double]$properties[0].Value
    }
}

function Test-NullablePrediction($Value) {
    return ($null -ne $Value -and -not [string]::IsNullOrWhiteSpace([string]$Value))
}

function Get-EmbeddingCosine($Left, $Right, [string]$Description) {
    $leftValues = @($Left)
    $rightValues = @($Right)
    if ($leftValues.Count -eq 0 -or $leftValues.Count -ne $rightValues.Count) {
        throw "$Description embedding lengths differ or are empty: Windows=$($leftValues.Count), Android=$($rightValues.Count)."
    }
    [double]$dot = 0
    [double]$leftSquared = 0
    [double]$rightSquared = 0
    for ($index = 0; $index -lt $leftValues.Count; $index++) {
        [double]$leftValue = $leftValues[$index]
        [double]$rightValue = $rightValues[$index]
        if ([double]::IsNaN($leftValue) -or [double]::IsInfinity($leftValue) -or
            [double]::IsNaN($rightValue) -or [double]::IsInfinity($rightValue)) {
            throw "$Description embedding contains a non-finite value at index $index."
        }
        $dot += $leftValue * $rightValue
        $leftSquared += $leftValue * $leftValue
        $rightSquared += $rightValue * $rightValue
    }
    if ($leftSquared -le 0 -or $rightSquared -le 0) {
        throw "$Description embedding has a zero norm."
    }
    return $dot / [Math]::Sqrt($leftSquared * $rightSquared)
}

if ($ScoreTolerance -lt 0 -or $EmbeddingNormTolerance -lt 0 -or $EmbeddingNormRelativeTolerance -lt 0) {
    throw "Tolerances must be zero or greater."
}
if ($MinimumEmbeddingCosine -lt -1 -or $MinimumEmbeddingCosine -gt 1) {
    throw "MinimumEmbeddingCosine must be between -1 and 1."
}

$androidDirectory = Resolve-RepoPath $AndroidResultsDirectory
if (-not (Test-Path -LiteralPath $androidDirectory -PathType Container)) {
    throw "Android parity results are required, including with -SkipBenchmarkRun: $androidDirectory"
}
$androidFiles = @(Get-ChildItem -LiteralPath $androidDirectory -Filter "*.json" -File)
if ($androidFiles.Count -eq 0) {
    throw "Android parity results are required, including with -SkipBenchmarkRun: no JSON files in $androidDirectory"
}
$androidRecords = @($androidFiles | ForEach-Object {
    try {
        Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
    } catch {
        throw "Invalid Android parity JSON '$($_.FullName)': $($_.Exception.Message)"
    }
})

Push-Location $repoRoot
try {
    if (-not $SkipBenchmarkRun) {
        & ".\scripts\windows\run-speaker-benchmark.ps1" -Config $Config
        if ($LASTEXITCODE -ne 0) {
            throw "Speaker benchmark failed with exit code $LASTEXITCODE"
        }
    }

    $windowsParityPath = Resolve-RepoPath (Join-Path $ResultsDirectory "windows-parity.json")
    if (-not (Test-Path -LiteralPath $windowsParityPath -PathType Leaf)) {
        throw "Missing Windows parity report: $windowsParityPath"
    }
    $windowsRecords = @((Get-Content -LiteralPath $windowsParityPath -Raw | ConvertFrom-Json).records)

    $configPath = Resolve-RepoPath $Config
    $configDocument = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    $configDirectory = Split-Path -Parent $configPath
    $manifestPath = [IO.Path]::GetFullPath((Join-Path $configDirectory ([string]$configDocument.manifestPath)))
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Missing benchmark manifest: $manifestPath"
    }
    $manifestDirectory = Split-Path -Parent $manifestPath
    $manifest = @(Import-Csv -LiteralPath $manifestPath)

    $modelRegistryPath = Join-Path $repoRoot "config/models.json"
    $modelRegistry = @((Get-Content -LiteralPath $modelRegistryPath -Raw | ConvertFrom-Json).models)
    $requiredAndroidModelIds = @(
        "campplus-en",
        "campplus-zh-en",
        "eres2net-en",
        "speakernet-m",
        "titanet-s"
    )
    $cases = New-Object 'System.Collections.Generic.List[object]'
    foreach ($modelId in $requiredAndroidModelIds) {
        $registryMatches = @($modelRegistry | Where-Object { $_.id -eq $modelId })
        if ($registryMatches.Count -ne 1) {
            throw "Expected one model registry entry for $modelId, found $($registryMatches.Count)."
        }
        $registryModel = $registryMatches[0]
        $windowsModelMatches = @($windowsRecords | Where-Object {
            ([string]$_.modelSha256).ToLowerInvariant() -eq ([string]$registryModel.sha256).ToLowerInvariant()
        })
        $windowsModelNames = @($windowsModelMatches | Select-Object -ExpandProperty modelName -Unique)
        if ($windowsModelNames.Count -ne 1) {
            throw "Could not map modelId=$modelId to exactly one Windows model name by registry SHA-256."
        }
        foreach ($utteranceId in @("fangjun-test", "leijun-unknown")) {
            $cases.Add([PSCustomObject]@{
                ModelId = $modelId
                ModelName = [string]$windowsModelNames[0]
                ModelSha256 = ([string]$registryModel.sha256).ToLowerInvariant()
                UtteranceId = $utteranceId
            })
        }
    }

    $failures = New-Object 'System.Collections.Generic.List[string]'
    $rows = New-Object 'System.Collections.Generic.List[object]'

    foreach ($case in $cases) {
        $manifestMatches = @($manifest | Where-Object { $_.utterance_id -eq $case.UtteranceId })
        if ($manifestMatches.Count -ne 1) {
            throw "Expected one manifest row for $($case.UtteranceId), found $($manifestMatches.Count)."
        }
        $wavPath = [IO.Path]::GetFullPath((Join-Path $manifestDirectory ([string]$manifestMatches[0].path)))
        if (-not (Test-Path -LiteralPath $wavPath -PathType Leaf)) {
            throw "Missing parity WAV: $wavPath"
        }
        $wavSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $wavPath).Hash.ToLowerInvariant()

        $windowsMatches = @($windowsRecords | Where-Object {
            $_.modelName -eq $case.ModelName -and $_.utteranceId -eq $case.UtteranceId
        })
        if ($windowsMatches.Count -ne 1) {
            throw "Expected one Windows record for $($case.ModelName) / $($case.UtteranceId), found $($windowsMatches.Count)."
        }
        $windows = $windowsMatches[0]

        $androidMatches = @($androidRecords | Where-Object {
            (Get-ObjectProperty $_ "modelId") -eq $case.ModelId -and
                ([string](Get-ObjectProperty $_ "queryWavSha256")).ToLowerInvariant() -eq $wavSha256
        })
        if ($androidMatches.Count -ne 1) {
            throw "Expected one Android record for modelId=$($case.ModelId), utterance=$($case.UtteranceId), queryWavSha256=$wavSha256; found $($androidMatches.Count)."
        }
        $android = $androidMatches[0]

        $windowsScore = Get-OnlyScore (Get-ObjectProperty $windows "speakerScores") "Windows speakerScores"
        $androidScore = Get-OnlyScore (Get-ObjectProperty $android "speakerScores") "Android speakerScores"
        $scoreDifference = [Math]::Abs($windowsScore.Score - $androidScore.Score)
        $normDifference = [Math]::Abs(
            [double](Get-ObjectProperty $windows "embeddingNorm") -
            [double](Get-ObjectProperty $android "embeddingNorm")
        )
        $normScale = [Math]::Max(
            [Math]::Abs([double](Get-ObjectProperty $windows "embeddingNorm")),
            [Math]::Abs([double](Get-ObjectProperty $android "embeddingNorm"))
        )
        $normAllowedDifference = $EmbeddingNormTolerance + ($EmbeddingNormRelativeTolerance * $normScale)
        $embeddingCosine = Get-EmbeddingCosine `
            (Get-ObjectProperty $windows "embedding") `
            (Get-ObjectProperty $android "embedding") `
            "$($case.ModelId) / $($case.UtteranceId)"
        $durationDifference = [Math]::Abs(
            [double](Get-ObjectProperty $windows "durationSec") -
            [double](Get-ObjectProperty $android "durationSec")
        )
        $thresholdDifference = [Math]::Abs(
            [double](Get-ObjectProperty $windows "threshold") -
            [double](Get-ObjectProperty $android "threshold")
        )
        $marginDifference = [Math]::Abs(
            [double](Get-ObjectProperty $windows "margin") -
            [double](Get-ObjectProperty $android "margin")
        )
        $windowsIsUnknown = [bool](Get-ObjectProperty $windows "isUnknown")
        $androidIsUnknown = [bool](Get-ObjectProperty $android "isUnknown")
        $windowsPrediction = Get-OptionalObjectProperty $windows "predictedSpeakerId"
        $androidPrediction = Get-OptionalObjectProperty $android "predictedSpeakerId"
        $windowsHasPrediction = Test-NullablePrediction $windowsPrediction
        $androidHasPrediction = Test-NullablePrediction $androidPrediction

        $checks = [ordered]@{
            modelSha256 = ([string](Get-ObjectProperty $windows "modelSha256")).ToLowerInvariant() -eq $case.ModelSha256 -and
                ([string](Get-ObjectProperty $android "modelSha256")).ToLowerInvariant() -eq $case.ModelSha256
            queryWavSha256 = ([string](Get-ObjectProperty $android "queryWavSha256")).ToLowerInvariant() -eq $wavSha256
            sampleRate = [int](Get-ObjectProperty $windows "sampleRate") -eq [int](Get-ObjectProperty $android "sampleRate")
            numSamples = [long](Get-ObjectProperty $windows "numSamples") -eq [long](Get-ObjectProperty $android "numSamples")
            duration = $durationDifference -le 0.000000001
            embeddingDim = [int](Get-ObjectProperty $windows "embeddingDim") -eq [int](Get-ObjectProperty $android "embeddingDim")
            embeddingNorm = $normDifference -le $normAllowedDifference
            embeddingCosine = $embeddingCosine -ge $MinimumEmbeddingCosine
            score = $scoreDifference -le $ScoreTolerance
            threshold = $thresholdDifference -le 0.000001
            margin = $marginDifference -le 0.000001
            isUnknown = $windowsIsUnknown -eq $androidIsUnknown
            prediction = ($windowsHasPrediction -eq $androidHasPrediction) -and
                ($windowsHasPrediction -eq (-not $windowsIsUnknown)) -and
                ($androidHasPrediction -eq (-not $androidIsUnknown)) -and
                ((-not $windowsHasPrediction) -or ([string]$windowsPrediction -eq $windowsScore.SpeakerId)) -and
                ((-not $androidHasPrediction) -or ([string]$androidPrediction -eq $androidScore.SpeakerId))
        }

        $failedChecks = @($checks.GetEnumerator() | Where-Object { -not $_.Value } | ForEach-Object { $_.Key })
        if ($failedChecks.Count -gt 0) {
            $failures.Add("$($case.ModelId) / $($case.UtteranceId): $($failedChecks -join ', ')")
        }
        $rows.Add([PSCustomObject]@{
            model_id = $case.ModelId
            model_name = $case.ModelName
            utterance_id = $case.UtteranceId
            query_wav_sha256 = $wavSha256
            model_sha256_match = $checks.modelSha256
            query_wav_sha256_match = $checks.queryWavSha256
            sample_rate_match = $checks.sampleRate
            num_samples_match = $checks.numSamples
            duration_difference = $durationDifference
            embedding_dimension_match = $checks.embeddingDim
            windows_embedding_norm = [double](Get-ObjectProperty $windows "embeddingNorm")
            android_embedding_norm = [double](Get-ObjectProperty $android "embeddingNorm")
            embedding_norm_difference = $normDifference
            embedding_norm_allowed_difference = $normAllowedDifference
            embedding_cosine = $embeddingCosine
            minimum_embedding_cosine = $MinimumEmbeddingCosine
            windows_score = $windowsScore.Score
            android_score = $androidScore.Score
            score_difference = $scoreDifference
            threshold_match = $checks.threshold
            margin_match = $checks.margin
            windows_prediction = [string]$windowsPrediction
            android_prediction = [string]$androidPrediction
            prediction_match = $checks.prediction
            is_unknown_match = $checks.isUnknown
            passed = $failedChecks.Count -eq 0
        })
    }

    $summaryPath = Resolve-RepoPath $SummaryDirectory
    New-Item -ItemType Directory -Force -Path $summaryPath | Out-Null
    $csvPath = Join-Path $summaryPath "comparisons.csv"
    $jsonPath = Join-Path $summaryPath "summary.json"
    $rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding UTF8
    $summary = [ordered]@{
        verifiedAtUtc = [DateTime]::UtcNow.ToString("o")
        passed = $failures.Count -eq 0
        scoreTolerance = $ScoreTolerance
        embeddingNormTolerance = $EmbeddingNormTolerance
        embeddingNormRelativeTolerance = $EmbeddingNormRelativeTolerance
        minimumEmbeddingCosine = $MinimumEmbeddingCosine
        windowsParityPath = $windowsParityPath
        androidResultsDirectory = $androidDirectory
        comparedRecordCount = $rows.Count
        failures = $failures.ToArray()
        comparisons = $rows.ToArray()
    }
    $utf8WithoutBom = New-Object Text.UTF8Encoding($false)
    [IO.File]::WriteAllText($jsonPath, (($summary | ConvertTo-Json -Depth 8) + [Environment]::NewLine), $utf8WithoutBom)

    if ($failures.Count -gt 0) {
        throw "Windows/Android parity failed. See $jsonPath. Failures: $($failures -join '; ')"
    }
    Write-Host "Windows/Android parity passed for $($rows.Count) records. Summary: $jsonPath"
} finally {
    Pop-Location
}
