param(
    [string]$Aar = "app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$aarPath = Join-Path $repoRoot $Aar
$expectedSha256 = "DC5AC19A28DEE3BFFC5E5A5D50CB6AFA977703FC4A7EE535A308506990FDD295"
$expectedSize = 37631864L

if (-not (Test-Path -LiteralPath $aarPath -PathType Leaf)) {
    throw "sherpa-onnx AAR is missing: $aarPath. Run scripts/windows/download-android-speaker-runtime.ps1."
}
$actualHash = (Get-FileHash -LiteralPath $aarPath -Algorithm SHA256).Hash
$actualSize = (Get-Item -LiteralPath $aarPath).Length
if ($actualHash -ne $expectedSha256 -or $actualSize -ne $expectedSize) {
    throw "sherpa-onnx AAR verification failed: hash=$actualHash size=$actualSize"
}

$entries = @(& jar tf $aarPath)
$required = "jni/armeabi-v7a/libsherpa-onnx-jni.so"
if ($required -notin $entries) {
    throw "Verified AAR does not contain $required"
}
if ("jni/armeabi-v7a/libonnxruntime.so" -in $entries) {
    throw "Expected the static-link AAR, but found a separate ARMv7 libonnxruntime.so"
}

Push-Location $repoRoot
try {
    & ".\gradlew.bat" ":app:checkDebugAarMetadata"
    if ($LASTEXITCODE -ne 0) {
        throw "Android AAR metadata compatibility check failed."
    }
} finally {
    Pop-Location
}

Write-Host "Verified the pinned AAR hash, ARMv7 static-link entry, and Gradle minSdk 23 metadata compatibility."
Write-Host "A separate libonnxruntime.so is intentionally not required for this artifact."
Write-Host "This packaging check does not replace loading and inference on a physical API 23/ARMv7 Pepper."
