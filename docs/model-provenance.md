# Model provenance

モデル本体はライセンス、ABI、APIレベル、実機性能を確認してから取得し、Gitにはコミットしない。

## YuNet face detection

- Model: `face_detection_yunet_2026may.onnx`
- Official source: https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
- Download: https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_detection_yunet/face_detection_yunet_2026may.onnx
- License: MIT（OpenCV Zooの`models/face_detection_yunet/LICENSE`）
- Size: 229,738 bytes
- SHA-256: `EBAFCE4E3C118D6554634BE5C27AB333B4C047A9A8C3FAF1D7CF93101C22F0F0`
- Local path: `app/src/main/assets/models/face_detection_yunet_2026may.onnx`

PowerShell:

```powershell
New-Item -ItemType Directory -Force app/src/main/assets/models | Out-Null
Invoke-WebRequest -UseBasicParsing `
  'https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_detection_yunet/face_detection_yunet_2026may.onnx' `
  -OutFile app/src/main/assets/models/face_detection_yunet_2026may.onnx
Get-FileHash -Algorithm SHA256 app/src/main/assets/models/face_detection_yunet_2026may.onnx
```

## OpenCV Android runtime

- Artifact: `org.opencv:opencv:5.0.0`
- License: Apache License 2.0
- Distribution: Maven Central AAR
- Verified AAR ABIs: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`
- Native library: `libopencv_java5.so`
- Verified build target: Android API 23 application

OpenCV 5.0.0とYuNet `2026may`が旧Pepperでロードまたは推論できない場合のみ、OpenCV 4.xとYuNet `2023mar`をフォールバック候補として検証する。

## SFace face recognition

- Model: `face_recognition_sface_2021dec.onnx`
- Official source: https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface
- Download: https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx
- License: Apache License 2.0（OpenCV Zooの`models/face_recognition_sface/LICENSE`）
- Architecture: MobileFaceNet trained with SFace loss
- Size: 38,696,353 bytes
- SHA-256: `0BA9FBFA01B5270C96627C4EF784DA859931E02F04419C829E83484087C34E79`
- Local path: `app/src/main/assets/models/face_recognition_sface_2021dec.onnx`

PowerShell:

```powershell
Invoke-WebRequest -UseBasicParsing `
  'https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx' `
  -OutFile app/src/main/assets/models/face_recognition_sface_2021dec.onnx
Get-FileHash -Algorithm SHA256 app/src/main/assets/models/face_recognition_sface_2021dec.onnx
```

登録時に保存するのはSFace特徴量、モデル名、personId、表示名、登録日時、サンプル数だけとし、生の顔画像や切り出し画像は保存しない。

## face-reidentification-retail-0095

- Official specification: https://github.com/openvinotoolkit/open_model_zoo/blob/master/models/intel/face-reidentification-retail-0095/README.md
- Official distribution metadata: https://github.com/openvinotoolkit/open_model_zoo/blob/master/models/intel/face-reidentification-retail-0095/model.yml
- License: Apache License 2.0
- Architecture: MobileNet V2 based embedding network
- Input: OpenVINO IR FP32, BGR `1x3x128x128`; raw `0..255` pixels because preprocessing is embedded in the IR
- Output: 256 dimensions
- XML: 237,585 bytes, SHA-256 `6CF60C341452155E35C467510C6C50A96ADE5B2BD8F88C5A90902E905D8A80C3`
- BIN: 4,427,256 bytes, SHA-256 `21319B95E54181857F99E22DC32EC89770ECA2969A1432CFA1594BFFC94EDD62`
- Converted ONNX: opset 13, 4,485,877 bytes, SHA-256 `861D2EDC47214F19FE973F97A05B2BD8BA61E103279FE232F0365534903DD589`
- Android runtime: OpenCV 5.0.0 DNN CPU backend

公式配布はONNXではなくOpenVINO IRのXMLとBINである。公式Android AARにはOpenVINO DNN pluginがなくIRを直接ロードできないため、第三者製`openvino2onnx` 1.1.0でopset 13 ONNXへ変換する。OpenVINOはIRからONNXへの逆変換を公式サポートしていないため、変換処理は実験的な派生物として扱う。

`convert-0095-to-onnx.ps1`はPython仮想環境と変換ツールを固定し、元IRと変換ONNXをゼロ・255・乱数入力およびLombard GRID実顔3枚で比較する。OpenVINO Runtime対ONNX Runtime/OpenCV 5.0.0の最大絶対誤差は`5.2e-6`以下、embedding cosineは`0.99999999998`以上だった。実顔スコアは元IRが同一`0.9548381`・別人物`0.2921425`、OpenCV ONNXが同一`0.9548381`・別人物`0.2921425`である。受入条件は最大絶対誤差`1e-4`以下、embedding cosine `0.9999`以上、実顔スコア差`1e-4`以下、閾値0.60の判定一致で、変換物のSHA-256も検証する。変換前処理にはBGR/RGBチャンネル反転と`/255`が埋め込まれているため、Android側では128x128 BGRを追加正規化せず入力する。

初回変換にはPython 3.11以上が必要である。既定の`python`が古い場合は、`setup-local-inference-assets.ps1 -Python C:\path\to\python.exe`のように指定する。

## sherpa-onnx Android runtime

- Version: 1.13.4
- Artifact: `sherpa-onnx-static-link-onnxruntime-1.13.4.aar`
- Official source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4
- License: Apache License 2.0
- Size: 37,631,864 bytes
- SHA-256: `DC5AC19A28DEE3BFFC5E5A5D50CB6AFA977703FC4A7EE535A308506990FDD295`
- AAR minSdk metadata: 21
- Verified ABI: `armeabi-v7a`
- ARMv7 native library: single static `libsherpa-onnx-jni.so`

ARMv7 ELFはARMv7-A、Thumb-2、VFPv3、NEONを使用し、Pepper `LPT_200AR`の命令セットと一致する。Pepper API 23ではJNIロード、CAM++、ERes2Net、Silero VAD初期化に成功した。

x86用AARには別の`libonnxruntime.so`が含まれ、API 23に存在しない`__write_chk`を参照する。このためAPI 23 x86エミュレータでは話者モデルをロードできない。これはARMv7 Pepperには発生しないが、エミュレータ上のネイティブ話者推論は未対応とする。

## Silero VAD

- Model: `silero_vad.onnx`
- Official distribution: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
- Upstream: https://github.com/snakers4/silero-vad
- License: MIT
- Size: 643,854 bytes
- SHA-256: `9E2449E1087496D8D4CABA907F23E0BD3F78D91FA552479BB9C23AC09CBB1FD6`

## 3D-Speaker CAM++

- Model: `3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx`
- Official distribution: https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
- Upstream: https://github.com/modelscope/3D-Speaker
- License: Apache License 2.0
- Size: 29,596,978 bytes
- SHA-256: `357A834F702B80161E5B981182C038E18553C1F2CA752ED6CEC2052365D4129B`
- Input: 16 kHz mono normalized PCM
- Output: 512 dimensions

## 3D-Speaker ERes2Net

- Model: `3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx`
- Official distribution: https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
- Upstream: https://github.com/modelscope/3D-Speaker
- License: Apache License 2.0
- Size: 26,485,263 bytes
- SHA-256: `C59158379255AD66E161679CCA6AF8D52D51E389E3224AB7D7A7BAAE295C2DB5`
- Input: 16 kHz mono normalized PCM
- Output: 192 dimensions

バイナリはGitへコミットしない。初回セットアップは次を実行し、スクリプトで取得する公式配布物のSHA-256を検証する。動画から生成する顔ベンチPNGは、ハッシュ検証済み動画の1.0秒位置から再生成する。

```powershell
.\scripts\setup-local-inference-assets.ps1
```
