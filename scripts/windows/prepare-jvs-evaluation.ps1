param(
    [string]$Archive = "data/downloads/jvs_ver1.zip",
    [string]$Output = "data/audio/jvs-evaluation",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$gradleArguments = @(
    ":speaker-benchmark:prepareJvs",
    "-PjvsArchive=$Archive",
    "-PjvsOutput=$Output"
)
if ($Force) {
    $gradleArguments += "-PjvsForce=true"
}

Push-Location $repoRoot
try {
    & ".\gradlew.bat" @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "JVS evaluation preparation failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
