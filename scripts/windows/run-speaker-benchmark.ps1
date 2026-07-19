param(
    [string]$Config = "config/speaker-benchmark.json",
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$arguments = "--config `"$Config`""
if ($ValidateOnly) {
    $arguments += " --validate-only"
}

Push-Location $repoRoot
try {
    & ".\gradlew.bat" ":speaker-benchmark:run" "--args=$arguments"
    if ($LASTEXITCODE -ne 0) {
        throw "Speaker benchmark failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
