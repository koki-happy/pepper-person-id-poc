param(
    [string]$Python = "python",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$workDirectory = Join-Path $repoRoot "build/wespeaker-resnet34-lm"
$source = Join-Path $workDirectory "voxceleb_resnet34_LM.onnx"
$output = Join-Path $repoRoot "app/src/benchmark/assets/models/wespeaker_en_voxceleb_resnet34_LM.onnx"
$localSource = Join-Path $repoRoot "models/voxceleb_resnet34_LM.onnx"
$venv = Join-Path $workDirectory "venv"
$venvPython = Join-Path $venv "Scripts/python.exe"
$sourceUrl = "https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM/resolve/f0c48c298fd835726c27956a5d617bad7115627e/voxceleb_resnet34_LM.onnx?download=true"
$expected = @{
    Source = "7BB2F06E9DF17CDF1EF14EE8A15AB08ED28E8D0EF5054EE135741560DF2EC068"
    Output = "DF0CEC64C3BBA5DBC3637E50C4259DE348A24124F4BB399F413FA1D4B44BA605"
}

if ((Test-Path -LiteralPath $output -PathType Leaf) -and
    ((Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash -eq $expected.Output) -and
    -not $Force) {
    Write-Host "Verified app/src/benchmark/assets/models/wespeaker_en_voxceleb_resnet34_LM.onnx"
    exit 0
}

New-Item -ItemType Directory -Force -Path $workDirectory | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $localSource) | Out-Null
$validLocalSource = (Test-Path -LiteralPath $localSource -PathType Leaf) -and
    ((Get-FileHash -LiteralPath $localSource -Algorithm SHA256).Hash -eq $expected.Source)
if (-not $validLocalSource -or $Force) {
    $temporary = "$source.download"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    Invoke-WebRequest -UseBasicParsing -Uri $sourceUrl -OutFile $temporary
    if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $expected.Source) {
        Remove-Item -LiteralPath $temporary -Force
        throw "Unexpected WeSpeaker source SHA-256"
    }
    Move-Item -LiteralPath $temporary -Destination $localSource -Force
}
Copy-Item -LiteralPath $localSource -Destination $source -Force

if (-not (Test-Path -LiteralPath $venvPython -PathType Leaf)) {
    & $Python -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)"
    if ($LASTEXITCODE -ne 0) {
        throw "Python 3.10 or newer is required. Pass -Python with a compatible python.exe path."
    }
    & $Python -m venv $venv
    if ($LASTEXITCODE -ne 0) { throw "Failed to create the WeSpeaker preparation environment" }
}

& $venvPython -m pip install `
    onnx==1.17.0 `
    numpy==2.1.3 `
    protobuf==5.29.5
if ($LASTEXITCODE -ne 0) { throw "Failed to install pinned WeSpeaker preparation dependencies" }

& $venvPython (Join-Path $PSScriptRoot "speaker/prepare-wespeaker-resnet34-lm.py") `
    --source $source `
    --output $output
if ($LASTEXITCODE -ne 0) { throw "Failed to prepare the WeSpeaker Android artifact" }

Write-Host "Prepared and verified app/src/benchmark/assets/models/wespeaker_en_voxceleb_resnet34_LM.onnx"
