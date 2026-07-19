param([switch]$Force)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$destination = Join-Path $repoRoot "app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"
$url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"
$expectedSha256 = "DC5AC19A28DEE3BFFC5E5A5D50CB6AFA977703FC4A7EE535A308506990FDD295"
$expectedSize = 37631864L

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destination) | Out-Null
$valid = (Test-Path -LiteralPath $destination -PathType Leaf) -and
    ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -eq $expectedSha256) -and
    ((Get-Item -LiteralPath $destination).Length -eq $expectedSize)
if ($valid -and -not $Force) {
    Write-Host "Verified $destination"
    return
}

$temporary = "$destination.download"
Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
try {
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $temporary
    $actualHash = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash
    $actualSize = (Get-Item -LiteralPath $temporary).Length
    if ($actualHash -ne $expectedSha256 -or $actualSize -ne $expectedSize) {
        throw "Android speaker runtime verification failed: hash=$actualHash size=$actualSize"
    }
    Move-Item -LiteralPath $temporary -Destination $destination -Force
} catch {
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    throw
}

Write-Host "Downloaded and verified $destination"
