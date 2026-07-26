param(
    [switch]$Force,
    [string]$Python = "python"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$artifacts = @(
    @{
        RelativePath = "app/src/benchmark/assets/models/face_detection_yunet_2026may.onnx"
        Url = "https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_detection_yunet/face_detection_yunet_2026may.onnx"
        Sha256 = "EBAFCE4E3C118D6554634BE5C27AB333B4C047A9A8C3FAF1D7CF93101C22F0F0"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/face_recognition_sface_2021dec.onnx"
        Url = "https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx"
        Sha256 = "0BA9FBFA01B5270C96627C4EF784DA859931E02F04419C829E83484087C34E79"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/face_recognition_sface_2021dec_int8.onnx"
        Url = "https://huggingface.co/opencv/face_recognition_sface/resolve/main/face_recognition_sface_2021dec_int8.onnx"
        Sha256 = "2B0E941E6F16CC048C20AEE0C8E31F569118F65D702914540F7BFDC14048D78A"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/face_detection_yunet_2023mar_int8.onnx"
        Url = "https://huggingface.co/opencv/face_detection_yunet/resolve/main/face_detection_yunet_2023mar_int8.onnx"
        Sha256 = "321AA5A6AFABF7ECC46A3D06BFAB2B579DC96EB5C3BE7EDD365FA04502AD9294"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/face-reidentification-retail-0095.xml"
        Url = "https://storage.openvinotoolkit.org/repositories/open_model_zoo/2023.0/models_bin/1/face-reidentification-retail-0095/FP32/face-reidentification-retail-0095.xml"
        Sha256 = "6CF60C341452155E35C467510C6C50A96ADE5B2BD8F88C5A90902E905D8A80C3"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/face-reidentification-retail-0095.bin"
        Url = "https://storage.openvinotoolkit.org/repositories/open_model_zoo/2023.0/models_bin/1/face-reidentification-retail-0095/FP32/face-reidentification-retail-0095.bin"
        Sha256 = "21319B95E54181857F99E22DC32EC89770ECA2969A1432CFA1594BFFC94EDD62"
    },
    @{
        RelativePath = "app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"
        Sha256 = "DC5AC19A28DEE3BFFC5E5A5D50CB6AFA977703FC4A7EE535A308506990FDD295"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/silero_vad.onnx"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
        Sha256 = "9E2449E1087496D8D4CABA907F23E0BD3F78D91FA552479BB9C23AC09CBB1FD6"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"
        Sha256 = "357A834F702B80161E5B981182C038E18553C1F2CA752ED6CEC2052365D4129B"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx"
        Url = "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/0743f301363dec56491a490f6d6cbc9d67f9a3bf/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx?download=true"
        Sha256 = "AA3CFC16963A10586A9393F5035D6D6B57E98D358B347F80C2A30BF4F00CEBA2"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        Sha256 = "C59158379255AD66E161679CCA6AF8D52D51E389E3224AB7D7A7BAAE295C2DB5"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/nemo_en_speakerverification_speakernet.onnx"
        Url = "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/0743f301363dec56491a490f6d6cbc9d67f9a3bf/nemo_en_speakerverification_speakernet.onnx?download=true"
        Sha256 = "D204DC8AAC0014B8543F05FC8E310510C7022BC65B6452C203EC205EF7A66B23"
    },
    @{
        RelativePath = "app/src/benchmark/assets/models/nemo_en_titanet_small.onnx"
        Url = "https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/0743f301363dec56491a490f6d6cbc9d67f9a3bf/nemo_en_titanet_small.onnx?download=true"
        Sha256 = "AD4A1802485D8B34C722D2A9D04249662F2ECE5D28A7A039063CA22F515A789E"
    },
    @{
        RelativePath = "app/src/androidTest/assets/speaker-test/fangjun-sr-1.wav"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/fangjun-sr-1.wav"
        Sha256 = "33C24061180224D2350143EE19E3AF031446995C676BD25996325D34BB20A4D5"
    },
    @{
        RelativePath = "app/src/androidTest/assets/speaker-test/fangjun-test-sr-1.wav"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/fangjun-test-sr-1.wav"
        Sha256 = "9175E523081BF6A630CE72A55B05F92148EAAFAF58CBBBE743686CD81C50848E"
    },
    @{
        RelativePath = "app/src/androidTest/assets/speaker-test/leijun-test-sr-1.wav"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/leijun-test-sr-1.wav"
        Sha256 = "36CDA04EE4D10E38095DE73B77C99E8E7C54347232967A9CDE4CF54F7D496BAB"
    },
    @{
        RelativePath = "app/src/androidTest/assets/speaker-test/lombard-s22-plain.wav"
        Url = "https://spandh.dcs.shef.ac.uk/avlombard/samples/s22_p_sgbe5s.wav"
        Sha256 = "D20870F17D2DA0818EE56A4D1EC04D9F327D641C62D874445D8BF67CFF0EBEB3"
    },
    @{
        RelativePath = "app/src/androidTest/assets/speaker-test/lombard-s22-lombard.wav"
        Url = "https://spandh.dcs.shef.ac.uk/avlombard/samples/s22_l_sgbe5s.wav"
        Sha256 = "9603C6644AA3FD6A01F00C308A490AB648A4E7C2373DDF2F34CB9175B37CA27D"
    },
    @{
        RelativePath = "app/src/androidTest/assets/speaker-test/lombard-s16-plain.wav"
        Url = "https://spandh.dcs.shef.ac.uk/avlombard/samples/s16_p_bgah2n.wav"
        Sha256 = "FE5A3BEA6FBF846CAF326885FF3A7E3B2A90746903571748EEA1026E0AB26F0F"
    },
    @{
        RelativePath = "datasets/lombard-grid/samples/s22_p.mov"
        Url = "https://spandh.dcs.shef.ac.uk/avlombard/samples/s22_p_sgbe5s.mov"
        Sha256 = "A3B283956A36BD6CB4084D20C3A8BDB84324CB0E7C3557F73B0E9409916002AC"
    },
    @{
        RelativePath = "datasets/lombard-grid/samples/s22_l.mov"
        Url = "https://spandh.dcs.shef.ac.uk/avlombard/samples/s22_l_sgbe5s.mov"
        Sha256 = "59588A014518AA3909ECC1FAE843751C5A860009500FEC7AAD2F44CEF9E2BD54"
    },
    @{
        RelativePath = "datasets/lombard-grid/samples/s16_p.mov"
        Url = "https://spandh.dcs.shef.ac.uk/avlombard/samples/s16_p_bgah2n.mov"
        Sha256 = "9AEB5333C71F21B71E124D9C62FF4559A0F83257467B677DE33603853C8761FA"
    }
)

foreach ($artifact in $artifacts) {
    $destination = Join-Path $repoRoot $artifact.RelativePath
    $destinationDirectory = Split-Path -Parent $destination
    New-Item -ItemType Directory -Force $destinationDirectory | Out-Null

    $validExistingFile = (Test-Path -LiteralPath $destination) -and
        ((Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash -eq $artifact.Sha256)
    if ($validExistingFile -and -not $Force) {
        Write-Host "Verified $($artifact.RelativePath)"
        continue
    }

    $temporary = "$destination.download"
    Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue
    Write-Host "Downloading $($artifact.RelativePath)"
    Invoke-WebRequest -Uri $artifact.Url -OutFile $temporary -UseBasicParsing
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $temporary).Hash
    if ($actualHash -ne $artifact.Sha256) {
        Remove-Item -LiteralPath $temporary -Force
        throw "SHA-256 mismatch for $($artifact.RelativePath): $actualHash"
    }
    Move-Item -LiteralPath $temporary -Destination $destination -Force
}

& (Join-Path $PSScriptRoot "litert/prepare-android-assets.ps1") -Python $Python
if ($LASTEXITCODE -ne 0) {
    throw "Failed to prepare the YuNet/SFace/0095 LiteRT Android assets"
}

$yunetNativeArtifacts = @{
    "face_detection_yunet_2026may_320.ncnn.param" =
        "F3B6EC99C4773DA6EDC0950F0FB1D6D728982114114E87A673780EB243B72BBF"
    "face_detection_yunet_2026may_320.ncnn.bin" =
        "8FAA696D61AD6BF5C13ED5D6C8AE35E376C660968DB53D723A329020856CA5C9"
    "face_detection_yunet_2026may_320.mnn" =
        "31CB825BBFF3CFE1535CC40DC27E72C3614C8EFCC0F3AE00BEB1B557F5F04B69"
}
$yunetNativeAssets = Join-Path $repoRoot "app/src/benchmark/assets/models"
$prepareYuNetNative = $false
foreach ($entry in $yunetNativeArtifacts.GetEnumerator()) {
    $path = Join-Path $yunetNativeAssets $entry.Key
    if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or
        (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash -ne $entry.Value) {
        $prepareYuNetNative = $true
        break
    }
}
if ($prepareYuNetNative) {
    & (Join-Path $PSScriptRoot "native-face/prepare-yunet-2026may-native.ps1") `
        -SourceOnnx (Join-Path $yunetNativeAssets "face_detection_yunet_2026may.onnx") `
        -OutputDirectory $yunetNativeAssets
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to prepare the exact YuNet ncnn/MNN assets"
    }
}

$ffmpeg = Get-Command ffmpeg -ErrorAction SilentlyContinue
if ($null -eq $ffmpeg) {
    throw "ffmpeg is required to create the ignored Lombard GRID face benchmark frames"
}

$faceFrames = @(
    @{ Video = "s22_p.mov"; Image = "lombard-s22-plain.png" },
    @{ Video = "s22_l.mov"; Image = "lombard-s22-lombard.png" },
    @{ Video = "s16_p.mov"; Image = "lombard-s16-plain.png" }
)
$faceAssetDirectory = Join-Path $repoRoot "app/src/androidTest/assets/face-test"
New-Item -ItemType Directory -Force $faceAssetDirectory | Out-Null
foreach ($frame in $faceFrames) {
    $video = Join-Path $repoRoot "datasets/lombard-grid/samples/$($frame.Video)"
    $image = Join-Path $faceAssetDirectory $frame.Image
    & $ffmpeg.Source -loglevel error -y -ss 1.0 -i $video -frames:v 1 -update 1 $image
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $image)) {
        throw "Failed to extract $($frame.Image)"
    }
}

& (Join-Path $PSScriptRoot "prepare-wespeaker-resnet34-lm.ps1") -Python $Python -Force:$Force
if ($LASTEXITCODE -ne 0) {
    throw "Failed to prepare the WeSpeaker ResNet34-LM Android model"
}

& (Join-Path $PSScriptRoot "convert-0095-to-onnx.ps1") -Python $Python -Force:$Force
if ($LASTEXITCODE -ne 0) {
    throw "Failed to prepare the converted 0095 ONNX model"
}

Write-Host "Local inference assets are ready. Binary files remain ignored by Git."
