param(
    [string]$Python = "python",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$xml = Join-Path $repoRoot "app/src/main/assets/models/face-reidentification-retail-0095.xml"
$bin = Join-Path $repoRoot "app/src/main/assets/models/face-reidentification-retail-0095.bin"
$output = Join-Path $repoRoot "app/src/main/assets/models/face-reidentification-retail-0095.onnx"
$workDirectory = Join-Path $repoRoot "datasets/0095-conversion"
$venv = Join-Path $workDirectory "venv"
$venvPython = Join-Path $venv "Scripts/python.exe"
$converter = Join-Path $venv "Scripts/openvino2onnx.exe"
$expected = @{
    Xml = "6CF60C341452155E35C467510C6C50A96ADE5B2BD8F88C5A90902E905D8A80C3"
    Bin = "21319B95E54181857F99E22DC32EC89770ECA2969A1432CFA1594BFFC94EDD62"
    Onnx = "861D2EDC47214F19FE973F97A05B2BD8BA61E103279FE232F0365534903DD589"
}

if ((Get-FileHash -Algorithm SHA256 -LiteralPath $xml).Hash -ne $expected.Xml) {
    throw "Unexpected 0095 XML SHA-256"
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $bin).Hash -ne $expected.Bin) {
    throw "Unexpected 0095 BIN SHA-256"
}
if ((Test-Path -LiteralPath $output) -and
    ((Get-FileHash -Algorithm SHA256 -LiteralPath $output).Hash -eq $expected.Onnx) -and
    -not $Force) {
    Write-Host "Verified app/src/main/assets/models/face-reidentification-retail-0095.onnx"
    exit 0
}

New-Item -ItemType Directory -Force $workDirectory | Out-Null
if (-not (Test-Path -LiteralPath $venvPython)) {
    & $Python -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)"
    if ($LASTEXITCODE -ne 0) {
        throw "Python 3.11 or newer is required. Pass -Python with a compatible python.exe path."
    }
    & $Python -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw "Failed to create the 0095 conversion virtual environment" }
}
& $venvPython -m pip install `
    openvino==2025.4.1 `
    openvino2onnx==1.1.0 `
    onnx==1.17.0 `
    onnxruntime==1.27.0 `
    opencv-python-headless==5.0.0.93 `
    numpy==2.1.3
if ($LASTEXITCODE -ne 0) { throw "Failed to install pinned 0095 conversion dependencies" }

$temporary = Join-Path $workDirectory "face-reidentification-retail-0095.onnx"
& $converter $xml $temporary --opset-version 13 --infer-shapes
if ($LASTEXITCODE -ne 0) { throw "Failed to convert 0095 IR to ONNX" }
& $venvPython (Join-Path $PSScriptRoot "validate-0095-conversion.py") `
    --ir $xml `
    --onnx $temporary `
    --face-assets (Join-Path $repoRoot "app/src/androidTest/assets/face-test") `
    --detector (Join-Path $repoRoot "app/src/main/assets/models/face_detection_yunet_2026may.onnx") `
    --output (Join-Path $workDirectory "validation.json")
if ($LASTEXITCODE -ne 0) { throw "0095 conversion validation failed" }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $temporary).Hash -ne $expected.Onnx) {
    throw "Unexpected converted 0095 ONNX SHA-256"
}
Copy-Item -LiteralPath $temporary -Destination $output -Force
Write-Host "Converted and validated app/src/main/assets/models/face-reidentification-retail-0095.onnx"
