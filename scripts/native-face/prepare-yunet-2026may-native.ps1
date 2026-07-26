[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$SourceOnnx,
    [string]$OutputDirectory = (
        Join-Path $PSScriptRoot '..\..\app\src\benchmark\assets\models'
    ),
    [string]$WorkDirectory = (
        Join-Path $env:TEMP 'pepper-person-id-yunet-native-tools'
    )
)

$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourceOnnx).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
$work = [IO.Path]::GetFullPath($WorkDirectory)
$expectedSourceHash = 'ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0'
$sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $source).Hash.ToLowerInvariant()
if ($sourceHash -ne $expectedSourceHash) {
    throw "Unexpected YuNet source SHA-256=$sourceHash; expected=$expectedSourceHash; no fallback"
}

New-Item -ItemType Directory -Force -Path $output,$work | Out-Null
$mnnPackages = Join-Path $work 'mnn-python-packages'
$onnxPackages = Join-Path $work 'onnx-python-packages'
$wheelDirectory = Join-Path $work 'wheels'
New-Item -ItemType Directory -Force -Path $mnnPackages,$onnxPackages,$wheelDirectory | Out-Null

$requirements = @(
    @{
        Name = 'MNN'
        Version = '3.5.0'
        Wheel = 'mnn-3.5.0-cp310-cp310-win_amd64.whl'
        Sha256 = '9052bfbcd8c345b2c7984298eff5fee0db2c08f34bba0aae878da3504ae8760f'
    },
    @{
        Name = 'onnx'
        Version = '1.17.0'
        Wheel = 'onnx-1.17.0-cp310-cp310-win_amd64.whl'
        Sha256 = 'dfd777d95c158437fda6b34758f0877d15b89cbe9ff45affbedc519b35345cf9'
    }
)
foreach ($requirement in $requirements) {
    $wheel = Join-Path $wheelDirectory $requirement.Wheel
    if (-not (Test-Path -LiteralPath $wheel)) {
        & py -3.10 -m pip download --no-deps --only-binary=:all: `
            --dest $wheelDirectory "$($requirement.Name)==$($requirement.Version)"
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to download $($requirement.Name)==$($requirement.Version)"
        }
    }
    $wheelHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $wheel).Hash.ToLowerInvariant()
    if ($wheelHash -ne $requirement.Sha256) {
        throw "Unexpected $($requirement.Wheel) SHA-256=$wheelHash; no fallback"
    }
}
& py -3.10 -m pip install --no-deps --upgrade --target $mnnPackages (
    Join-Path $wheelDirectory $requirements[0].Wheel
)
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to install the pinned MNN conversion tool'
}
& py -3.10 -m pip install --no-deps --upgrade --target $onnxPackages (
    Join-Path $wheelDirectory $requirements[1].Wheel
)
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to install the pinned ONNX shape tool'
}

$fixedOnnx = Join-Path $work 'face_detection_yunet_2026may_320.onnx'
$oldPythonPath = $env:PYTHONPATH
try {
    $env:PYTHONPATH = $onnxPackages
    & py -3.10 (Join-Path $PSScriptRoot 'fix_onnx_input.py') `
        --input $source --output $fixedOnnx --size 320
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to create the fixed 320x320 ONNX intermediary'
    }
} finally {
    $env:PYTHONPATH = $oldPythonPath
}

$fixedOnnxHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $fixedOnnx).Hash.ToLowerInvariant()
if ($fixedOnnxHash -ne '72b0268d3e022e680f582ef95aaefc6e54387897727719357516af52da6c36ad') {
    throw "Unexpected fixed YuNet ONNX SHA-256=$fixedOnnxHash; no fallback"
}

$pnnxZip = Join-Path $work 'pnnx-20260526-windows.zip'
$pnnxUrl = 'https://github.com/pnnx/pnnx/releases/download/20260526/pnnx-20260526-windows.zip'
if (-not (Test-Path -LiteralPath $pnnxZip)) {
    Invoke-WebRequest -UseBasicParsing $pnnxUrl -OutFile $pnnxZip
}
$pnnxZipHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $pnnxZip).Hash.ToLowerInvariant()
if ($pnnxZipHash -ne '4e188e7606c887ac550820322f34b144140df877b73292b25e48a2ba38f297df') {
    throw "Unexpected pnnx archive SHA-256=$pnnxZipHash; no fallback"
}
$pnnxDirectory = Join-Path $work 'pnnx-20260526'
if (-not (Test-Path -LiteralPath (Join-Path $pnnxDirectory 'pnnx.exe'))) {
    $expanded = Join-Path $work 'pnnx-expanded'
    Expand-Archive -Force -LiteralPath $pnnxZip -DestinationPath $expanded
    New-Item -ItemType Directory -Force -Path $pnnxDirectory | Out-Null
    Copy-Item -Force -LiteralPath (
        Join-Path $expanded 'pnnx-20260526-windows\pnnx.exe'
    ) -Destination (Join-Path $pnnxDirectory 'pnnx.exe')
}

$ncnnParam = Join-Path $output 'face_detection_yunet_2026may_320.ncnn.param'
$ncnnBin = Join-Path $output 'face_detection_yunet_2026may_320.ncnn.bin'
$pnnxScratch = Join-Path $work 'pnnx-output'
New-Item -ItemType Directory -Force -Path $pnnxScratch | Out-Null
& (Join-Path $pnnxDirectory 'pnnx.exe') $fixedOnnx `
    'inputshape=[1,3,320,320]f32' 'fp16=0' 'optlevel=2' `
    "pnnxparam=$(Join-Path $pnnxScratch 'yunet.pnnx.param')" `
    "pnnxbin=$(Join-Path $pnnxScratch 'yunet.pnnx.bin')" `
    "pnnxpy=$(Join-Path $pnnxScratch 'yunet_pnnx.py')" `
    "pnnxonnx=$(Join-Path $pnnxScratch 'yunet.pnnx.onnx')" `
    "ncnnparam=$ncnnParam" "ncnnbin=$ncnnBin" `
    "ncnnpy=$(Join-Path $pnnxScratch 'yunet_ncnn.py')"
if ($LASTEXITCODE -ne 0) {
    throw 'pnnx YuNet conversion failed'
}

$compression = Join-Path $work 'mnn-compression.json'
Set-Content -Encoding ascii -NoNewline -LiteralPath $compression -Value (
    '{"version":"3.5.0","mnnUuid":"ebafce4e-3c11-4d65-9463-4be5c27ab333"}'
)
$mnnModel = Join-Path $output 'face_detection_yunet_2026may_320.mnn'
$oldPythonPath = $env:PYTHONPATH
try {
    $env:PYTHONPATH = $mnnPackages
    & (Join-Path $mnnPackages 'bin\mnnconvert.exe') `
        -f ONNX --modelFile $fixedOnnx --MNNModel $mnnModel `
        --bizCode YuNet2026May --keepInputFormat --optimizeLevel 1 `
        --compressionParamsFile $compression
    $mnnExitCode = $LASTEXITCODE
} finally {
    $env:PYTHONPATH = $oldPythonPath
}
if ($mnnExitCode -notin @(0, -1073741819) -or -not (Test-Path -LiteralPath $mnnModel)) {
    throw "MNNConvert YuNet conversion failed with exit=$mnnExitCode"
}

$expectedOutputs = @{
    'face_detection_yunet_2026may_320.ncnn.param' =
        'f3b6ec99c4773da6edc0950f0fb1d6d728982114114e87a673780eb243b72bbf'
    'face_detection_yunet_2026may_320.ncnn.bin' =
        '8faa696d61ad6bf5c13ed5d6c8ae35e376c660968db53d723a329020856ca5c9'
    'face_detection_yunet_2026may_320.mnn' =
        '31cb825bbff3cfe1535cc40dc27e72c3614c8efcc0f3ae00beb1b557f5f04b69'
}
foreach ($entry in $expectedOutputs.GetEnumerator()) {
    $path = Join-Path $output $entry.Key
    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($actual -ne $entry.Value) {
        throw "Unexpected generated artifact=$($entry.Key) SHA-256=$actual; no fallback"
    }
}
