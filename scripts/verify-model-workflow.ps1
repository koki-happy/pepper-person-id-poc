[CmdletBinding()]
param(
    [switch]$Strict
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
$catalogPath = Join-Path $repoRoot 'config/models.json'
$workflowDoc = Join-Path $repoRoot 'docs/model-workflow.md'
$activePyannoteAdapter = Join-Path $repoRoot 'app/src/main/java/com/example/pepper_person_id_poc/infrastructure/speaker/SherpaPyannoteSegmentationEngine.kt'
$assetDirectory = Join-Path $repoRoot 'app/src/benchmark/assets/models'

$errors = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()

function Add-Error([string]$Message) {
    $errors.Add($Message)
}

function Add-Warning([string]$Message) {
    $warnings.Add($Message)
}

if (-not (Test-Path -LiteralPath $catalogPath -PathType Leaf)) {
    Add-Error "Missing model catalog: $catalogPath"
} else {
    $catalog = Get-Content -LiteralPath $catalogPath -Raw | ConvertFrom-Json

    if ($catalog.schemaVersion -ne 2) {
        Add-Error "config/models.json schemaVersion must be 2"
    }

    $artifactsById = @{}
    foreach ($artifact in @($catalog.artifacts)) {
        $artifactId = [string]$artifact.artifactId
        if ([string]::IsNullOrWhiteSpace($artifactId)) {
            Add-Error 'Every artifact must have a nonblank artifactId'
            continue
        }
        if ($artifactsById.ContainsKey($artifactId)) {
            Add-Error "Duplicate artifactId: $artifactId"
        } else {
            $artifactsById[$artifactId] = $artifact
        }

        if ($null -ne $artifact.conversion) {
            $sourceArtifactId = [string]$artifact.conversion.sourceArtifactId
            if (-not $artifactsById.ContainsKey($sourceArtifactId) -and
                -not (@($catalog.artifacts).artifactId -contains $sourceArtifactId)) {
                Add-Error "$artifactId conversion source is not cataloged: $sourceArtifactId"
            }
            if ([string]::IsNullOrWhiteSpace([string]$artifact.conversion.tool)) {
                Add-Error "$artifactId conversion tool is missing"
            }
            $environmentDigest = [string]$artifact.conversion.environmentDigest
            if ([string]::IsNullOrWhiteSpace($environmentDigest) -or
                $environmentDigest -eq 'UNRECORDED' -or
                $environmentDigest -like 'See *') {
                Add-Warning "$artifactId conversion environment is not directly recorded"
            }
            if ($null -eq $artifact.conversion.convertedAt) {
                Add-Warning "$artifactId conversion timestamp is missing"
            }
        }
    }

    $pyannote = @($catalog.artifacts | Where-Object {
        $_.artifactId -eq 'pyannote-segmentation-3.0-sherpa-onnx-fp32'
    })
    if ($pyannote.Count -ne 1) {
        Add-Error 'Expected exactly one Pyannote catalog artifact'
    } else {
        foreach ($abi in @('armeabi-v7a', 'arm64-v8a')) {
            $pair = @($pyannote[0].runtimeCompatibility | Where-Object {
                $_.runtimeId -eq 'sherpa-onnx-1.13.4-android-cpu' -and $_.abi -eq $abi
            })
            if ($pair.Count -ne 1 -or $pair[0].status -notin @('BUILDABLE', 'VERIFIED')) {
                Add-Error "Pyannote sherpa runtime pair is missing or not buildable for $abi"
            }
        }
    }
}

if (-not (Test-Path -LiteralPath $workflowDoc -PathType Leaf)) {
    Add-Error "Missing workflow document: $workflowDoc"
}

if (-not (Test-Path -LiteralPath $activePyannoteAdapter -PathType Leaf)) {
    Add-Warning 'Current SherpaPyannoteSegmentationEngine.kt is not present in the working tree'
} else {
    $adapter = Get-Content -LiteralPath $activePyannoteAdapter -Raw
    foreach ($requiredConstant in @(
        'ARTIFACT_ID = "pyannote-segmentation-3.0-sherpa-onnx-fp32"',
        'RUNTIME_ID = "sherpa-onnx-1.13.4-android-cpu"',
        'MODEL_FILENAME = "pyannote-segmentation-3.0.onnx"'
    )) {
        if ($adapter -notmatch [regex]::Escape($requiredConstant)) {
            Add-Error "Active Pyannote adapter does not declare: $requiredConstant"
        }
    }
}

$redimnetFiles = @()
foreach ($directory in @(
    $assetDirectory,
    (Join-Path $repoRoot 'models')
)) {
    if (Test-Path -LiteralPath $directory -PathType Container) {
        $redimnetFiles += @(Get-ChildItem -LiteralPath $directory -Recurse -File -Filter 'redimnet2*')
    }
}
if ($redimnetFiles.Count -gt 0) {
    Add-Warning 'ReDimNet2 files exist locally but have no cataloged reproducible source/conversion path'
}

if (Get-Command git -ErrorAction SilentlyContinue) {
    $trackedBinaryFiles = @(& git -C $repoRoot ls-files | Where-Object {
        $_ -match '(?i)\.(onnx|pt|pth|bin|tflite|nemo|aar|so)$'
    })
    if ($trackedBinaryFiles.Count -gt 0) {
        Add-Error "Model/runtime binary files must remain outside Git: $($trackedBinaryFiles -join ', ')"
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}

if ($warnings.Count -gt 0) {
    $warnings | ForEach-Object { Write-Warning $_ }
    if ($Strict) {
        Write-Error 'Model workflow verification failed in Strict mode because known provenance gaps remain.'
        exit 1
    }
}

Write-Host ("Model workflow verification passed. warnings={0}" -f $warnings.Count)
