param(
    [switch]$SkipDownloads,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([System.Environment]::OSVersion.Platform -ne [System.PlatformID]::Win32NT) {
    throw "The primary benchmark path must run on native Windows."
}
if (-not [Environment]::Is64BitOperatingSystem) {
    throw "ONNX Runtime Java requires Windows x64 for this benchmark."
}
if ($null -eq (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java is not available on PATH. Install a JDK compatible with the Gradle wrapper."
}

Push-Location $repoRoot
try {
    & java -version
    & ".\gradlew.bat" --version
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle wrapper verification failed."
    }

    if (-not $SkipDownloads) {
        & ".\scripts\windows\download-speaker-models.ps1"
        & ".\scripts\windows\download-speaker-smoke-data.ps1"
        & ".\scripts\windows\download-android-speaker-runtime.ps1"
        & ".\scripts\windows\verify-android-speaker-runtime.ps1"
    }

    if (-not $SkipTests) {
        & ".\gradlew.bat" ":speaker-core:test" ":speaker-benchmark:test" ":app:testDebugUnitTest"
        if ($LASTEXITCODE -ne 0) {
            throw "Project verification failed."
        }
    }
} finally {
    Pop-Location
}

Write-Host "Windows Kotlin/Gradle speaker benchmark environment is ready."
