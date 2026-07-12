param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$artifacts = @(
    @{
        RelativePath = "app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.4/sherpa-onnx-static-link-onnxruntime-1.13.4.aar"
        Sha256 = "DC5AC19A28DEE3BFFC5E5A5D50CB6AFA977703FC4A7EE535A308506990FDD295"
    },
    @{
        RelativePath = "app/src/main/assets/models/silero_vad.onnx"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
        Sha256 = "9E2449E1087496D8D4CABA907F23E0BD3F78D91FA552479BB9C23AC09CBB1FD6"
    },
    @{
        RelativePath = "app/src/main/assets/models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"
        Sha256 = "357A834F702B80161E5B981182C038E18553C1F2CA752ED6CEC2052365D4129B"
    },
    @{
        RelativePath = "app/src/main/assets/models/3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        Url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        Sha256 = "C59158379255AD66E161679CCA6AF8D52D51E389E3224AB7D7A7BAAE295C2DB5"
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

Write-Host "Local inference assets are ready. Binary files remain ignored by Git."
