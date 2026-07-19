[CmdletBinding()]
param(
    [string]$ProjectRoot = "",
    [string]$Abi = "armeabi-v7a",
    [switch]$SkipModelConversion
)

$ErrorActionPreference = "Stop"
$ProjectRoot = if ($ProjectRoot) { $ProjectRoot } else { Join-Path $PSScriptRoot "..\.." }
$ProjectRoot = (Resolve-Path $ProjectRoot).Path
if ($Abi -notin @("armeabi-v7a", "arm64-v8a")) {
    throw "Supported ABIs are armeabi-v7a (Pepper) and arm64-v8a (smartphone validation)"
}
$downloads = Join-Path $ProjectRoot "build\face-native-downloads"
$thirdParty = Join-Path $ProjectRoot "app\src\main\cpp\third_party"
$jniLibs = Join-Path $ProjectRoot "app\src\main\jniLibs\$Abi"
$assets = Join-Path $ProjectRoot "app\src\main\assets\models"
New-Item -ItemType Directory -Force $downloads, $thirdParty, $jniLibs, $assets | Out-Null

function Get-Package([string]$Url, [string]$Name) {
    $archive = Join-Path $downloads $Name
    if (-not (Test-Path $archive)) { Invoke-WebRequest $Url -OutFile $archive }
    $destination = Join-Path $downloads ([IO.Path]::GetFileNameWithoutExtension($Name))
    if (-not (Test-Path $destination)) { Expand-Archive $archive $destination }
    return $destination
}

$ncnnPackage = Get-Package `
    "https://github.com/Tencent/ncnn/archive/refs/tags/20260526.zip" `
    "ncnn-20260526-source.zip"
$ncnnRoot = Get-ChildItem $ncnnPackage -Directory | Select-Object -First 1
$androidSdk = if ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
$ndkRoot = if ($env:ANDROID_NDK_HOME) {
    $env:ANDROID_NDK_HOME
} else {
    (Get-ChildItem (Join-Path $androidSdk "ndk") -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
}
$cmake = Get-ChildItem (Join-Path $androidSdk "cmake") -Recurse -Filter cmake.exe |
    Sort-Object FullName -Descending |
    Select-Object -First 1
if (-not $cmake) { throw "Android SDK CMake was not found under $androidSdk" }
$ninja = Join-Path $cmake.Directory.FullName "ninja.exe"
if (-not (Test-Path $ninja)) { throw "ninja.exe was not found next to $($cmake.FullName)" }
$ncnnBuild = Join-Path $downloads "ncnn-20260526-$Abi-no-openmp"
$ncnnInstall = Join-Path $ncnnBuild "install"
& $cmake.FullName `
    -S $ncnnRoot.FullName `
    -B $ncnnBuild `
    -G Ninja `
    "-DCMAKE_MAKE_PROGRAM=$ninja" `
    "-DCMAKE_TOOLCHAIN_FILE=$(Join-Path $ndkRoot 'build\cmake\android.toolchain.cmake')" `
    "-DANDROID_ABI=$Abi" `
    -DANDROID_PLATFORM=android-23 `
    -DANDROID_ARM_NEON=ON `
    -DCMAKE_BUILD_TYPE=Release `
    -DNCNN_SHARED_LIB=ON `
    -DNCNN_OPENMP=OFF `
    -DNCNN_VULKAN=OFF `
    -DNCNN_BUILD_TOOLS=OFF `
    -DNCNN_BUILD_EXAMPLES=OFF `
    -DNCNN_BUILD_BENCHMARK=OFF `
    -DNCNN_BUILD_TESTS=OFF `
    "-DCMAKE_INSTALL_PREFIX=$ncnnInstall"
if ($LASTEXITCODE -ne 0) { throw "ncnn configuration failed for $Abi" }
& $cmake.FullName --build $ncnnBuild --target install --parallel
if ($LASTEXITCODE -ne 0) { throw "ncnn build failed for $Abi" }

$ncnnTarget = Join-Path $thirdParty "ncnn\$Abi"
New-Item -ItemType Directory -Force $ncnnTarget | Out-Null
Copy-Item (Join-Path $ncnnInstall "include") $ncnnTarget -Recurse -Force
Copy-Item (Join-Path $ncnnInstall "lib") $ncnnTarget -Recurse -Force
Copy-Item (Join-Path $ncnnInstall "lib\libncnn.so") $jniLibs -Force

$mnnPackage = Get-Package `
    "https://github.com/alibaba/MNN/releases/download/3.5.0/mnn_3.5.0_android_armv7_armv8_cpu_opencl_vulkan.zip" `
    "mnn-3.5.0-android.zip"
$mnnRoot = Get-ChildItem $mnnPackage -Directory | Select-Object -First 1
$mnnTarget = Join-Path $thirdParty "mnn\$Abi"
New-Item -ItemType Directory -Force $mnnTarget | Out-Null
Copy-Item (Join-Path $mnnRoot.FullName "$Abi\libMNN.so") $mnnTarget -Force
Copy-Item (Join-Path $mnnRoot.FullName "$Abi\libMNN.so") $jniLibs -Force
Copy-Item (Join-Path $mnnRoot.FullName "$Abi\libc++_shared.so") $jniLibs -Force

$mnnSource = Get-Package "https://github.com/alibaba/MNN/archive/refs/tags/3.5.0.zip" "mnn-3.5.0-source.zip"
$mnnSourceRoot = Get-ChildItem $mnnSource -Directory | Select-Object -First 1
$mnnIncludeTarget = Join-Path $thirdParty "mnn\include"
New-Item -ItemType Directory -Force $mnnIncludeTarget | Out-Null
Copy-Item (Join-Path $mnnSourceRoot.FullName "include\MNN") $mnnIncludeTarget -Recurse -Force

if (-not $SkipModelConversion) {
    $pnnxWindows = Get-Package `
        "https://github.com/pnnx/pnnx/releases/download/20260526/pnnx-20260526-windows.zip" `
        "pnnx-20260526-windows.zip"
    $pnnx = Get-ChildItem $pnnxWindows -Recurse -Filter pnnx.exe | Select-Object -First 1
    if (-not $pnnx) { throw "pnnx.exe was not found in the pnnx release" }

    $mnnPython = Join-Path $ProjectRoot "build\mnn-python"
    $mnnConvertPath = Join-Path $mnnPython "bin\mnnconvert.exe"
    if (-not (Test-Path $mnnConvertPath)) {
        py -3.10 -m pip install --disable-pip-version-check --target $mnnPython "MNN==3.5.0"
        if ($LASTEXITCODE -ne 0) { throw "MNN 3.5.0 converter installation failed" }
    }

    $models = @(
        "face_recognition_sface_2021dec.onnx",
        "face-reidentification-retail-0095.onnx"
    )
    foreach ($name in $models) {
        $source = Join-Path $assets $name
        if (-not (Test-Path $source)) { throw "Missing face model: $source" }
        $base = [IO.Path]::GetFileNameWithoutExtension($name)
        $inputShape = if ($base -eq "face_recognition_sface_2021dec") { "[1,3,112,112]" } else { "[1,3,128,128]" }
        & $pnnx.FullName $source `
            "inputshape=$inputShape" `
            "ncnnparam=$(Join-Path $assets "$base.ncnn.param")" `
            "ncnnbin=$(Join-Path $assets "$base.ncnn.bin")" `
            "pnnxparam=$(Join-Path $downloads "$base.pnnx.param")" `
            "pnnxbin=$(Join-Path $downloads "$base.pnnx.bin")" `
            "pnnxpy=$(Join-Path $downloads "$base.pnnx.py")" `
            "ncnnpy=$(Join-Path $downloads "$base.ncnn.py")" `
            fp16=0
        if ($LASTEXITCODE -ne 0) { throw "ncnn conversion failed for $name" }
        $priorPythonPath = $env:PYTHONPATH
        $env:PYTHONPATH = if ($priorPythonPath) { "$mnnPython;$priorPythonPath" } else { $mnnPython }
        $mnnOutput = Join-Path $assets "$base.mnn"
        & $mnnConvertPath -f ONNX --modelFile $source --MNNModel $mnnOutput --bizCode MNN
        $env:PYTHONPATH = $priorPythonPath
        if (-not (Test-Path $mnnOutput) -or (Get-Item $mnnOutput).Length -eq 0L) {
            throw "MNN conversion failed for $name"
        }
    }
}

Set-Content -LiteralPath (Join-Path $thirdParty "ready.marker") -Value "ncnn=20260526 (OpenMP disabled)`nMNN=3.5.0`nabi=$Abi"
Write-Host "Native face runtimes prepared for $Abi"
