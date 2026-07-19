[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$OnnxRuntimeVersion = "1.27.0",
    [string]$AndroidAbi = "armeabi-v7a",
    [int]$AndroidApi = 23,
    [string]$NdkVersion = "28.2.13676358"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = if ($ProjectRoot) { $ProjectRoot } else { Join-Path $PSScriptRoot "..\.." }
$ProjectRoot = (Resolve-Path $ProjectRoot).Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$latestSdkManager = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
$sdkManager = if (Test-Path $latestSdkManager) {
    Get-Item $latestSdkManager
} else {
    Get-ChildItem (Join-Path $sdk "cmdline-tools") -Recurse -Filter sdkmanager.bat |
        Sort-Object FullName -Descending | Select-Object -First 1
}
if (-not $sdkManager) { throw "sdkmanager.bat was not found under $sdk" }
& $sdkManager.FullName "ndk;$NdkVersion" "cmake;3.31.6"
if ($LASTEXITCODE -ne 0) { throw "Android NDK/CMake/Ninja installation failed" }
$androidCmakeBin = Join-Path $sdk "cmake\3.31.6\bin"
if (-not (Test-Path (Join-Path $androidCmakeBin "cmake.exe"))) {
    throw "Android SDK CMake 3.31.6 was not found at $androidCmakeBin"
}
$env:PATH = "$androidCmakeBin;$env:PATH"

$work = Join-Path $ProjectRoot "build\onnxruntime-android-$OnnxRuntimeVersion"
$source = Join-Path $work "source"
$venv = Join-Path $work "venv"
$opsConfig = Join-Path $work "face-models.required_operators.config"
New-Item -ItemType Directory -Force $work | Out-Null
if (-not (Test-Path (Join-Path $source ".git"))) {
    git clone --recursive --depth 1 --branch "v$OnnxRuntimeVersion" https://github.com/microsoft/onnxruntime.git $source
    if ($LASTEXITCODE -ne 0) { throw "ONNX Runtime source clone failed" }
}

if (-not (Test-Path (Join-Path $venv "Scripts\python.exe"))) { py -3.10 -m venv $venv }
$python = Join-Path $venv "Scripts\python.exe"
& $python -m pip install --disable-pip-version-check "onnx==1.21.0" "flatbuffers==25.12.19"
if ($LASTEXITCODE -ne 0) { throw "onnx package installation failed" }
# build.bat resolves Python from PATH; keep the reduced-build tooling in this isolated venv.
$env:PATH = "$(Join-Path $venv 'Scripts');$env:PATH"
$models = @(
    (Join-Path $ProjectRoot "app\src\main\assets\models\face_recognition_sface_2021dec.onnx"),
    (Join-Path $ProjectRoot "app\src\main\assets\models\face-reidentification-retail-0095.onnx")
)
foreach ($model in $models) { if (-not (Test-Path $model)) { throw "Missing face model: $model" } }
& $python (Join-Path $ProjectRoot "scripts\generate-onnx-ops-config.py") --output $opsConfig @models
if ($LASTEXITCODE -ne 0) { throw "Reduced operator config generation failed" }

$javaCandidates = @(
    $env:JAVA_HOME,
    (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")
) + @(Get-ChildItem (Join-Path $env:ProgramFiles "Java") -Directory -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty FullName)
$validJavaHome = $javaCandidates | Where-Object {
    $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) -and (Test-Path (Join-Path $_ "lib\jvm.cfg"))
} | Select-Object -First 1
if (-not $validJavaHome) { throw "A complete JDK with lib\jvm.cfg is required to build ONNX Runtime" }
$env:JAVA_HOME = $validJavaHome
$env:PATH = "$(Join-Path $validJavaHome 'bin');$env:PATH"
$buildBat = Join-Path $source "build.bat"
& $buildBat `
    --config MinSizeRel `
    --android `
    --android_sdk_path $sdk `
    --android_ndk_path (Join-Path $sdk "ndk\$NdkVersion") `
    --android_abi $AndroidAbi `
    --android_api $AndroidApi `
    --cmake_generator Ninja `
    --build_java `
    --disable_ml_ops `
    --include_ops_by_config $opsConfig `
    --skip_tests `
    --parallel
if ($LASTEXITCODE -ne 0) { throw "ONNX Runtime Android build failed" }

$aar = Get-ChildItem (Join-Path $source "build") -Recurse -Filter "*.aar" |
    Where-Object { $_.FullName -match "android" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $aar) { throw "Built ONNX Runtime AAR was not found" }
$destination = Join-Path $ProjectRoot "app\libs\onnxruntime-mobile-$OnnxRuntimeVersion.aar"
Copy-Item -LiteralPath $aar.FullName -Destination $destination -Force
Write-Host "ONNX Runtime AAR: $destination"
Write-Host "Reduced operators: $opsConfig"
