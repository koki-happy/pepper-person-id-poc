param(
    [string]$ResultsDirectory = "results/windows-speaker-benchmark",
    [double]$ScoreTolerance = 0.002,
    [switch]$SkipBenchmarkRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location $repoRoot
try {
    if (-not $SkipBenchmarkRun) {
        & ".\scripts\windows\run-speaker-benchmark.ps1"
        if ($LASTEXITCODE -ne 0) {
            throw "Speaker benchmark failed with exit code $LASTEXITCODE"
        }
    }

    $predictionsPath = Join-Path $repoRoot "$ResultsDirectory/predictions.csv"
    if (-not (Test-Path -LiteralPath $predictionsPath -PathType Leaf)) {
        throw "Missing predictions: $predictionsPath"
    }
    $predictions = @(Import-Csv -LiteralPath $predictionsPath)
    $references = @(
        @{ Model = "CAM++ Chinese-English Common Advanced"; Utterance = "fangjun-test"; Score = 0.8086963295936584 },
        @{ Model = "CAM++ Chinese-English Common Advanced"; Utterance = "leijun-unknown"; Score = 0.16119252145290375 }
    )

    foreach ($reference in $references) {
        $matches = @($predictions | Where-Object {
            $_.model_name -eq $reference.Model -and $_.utterance_id -eq $reference.Utterance
        })
        if ($matches.Count -ne 1) {
            throw "Expected one prediction for $($reference.Model) / $($reference.Utterance), found $($matches.Count)."
        }
        $difference = [Math]::Abs([double]$matches[0].highest_score - [double]$reference.Score)
        if ($difference -gt $ScoreTolerance) {
            throw "Windows reference regression for $($reference.Model) / $($reference.Utterance): difference=$difference, tolerance=$ScoreTolerance"
        }
    }
    Write-Host "All 2 Windows smoke scores match the pinned reference within $ScoreTolerance."
} finally {
    Pop-Location
}
