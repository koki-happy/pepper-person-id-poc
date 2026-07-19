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
- ARMv7 native library: single static `libsherpa-onnx-jni.so`（ONNX Runtimeを静的リンク）

ARMv7 ELFはARMv7-A、Thumb-2、VFPv3、NEONを使用し、Pepper `LPT_200AR`の命令セットと一致する。Pepper API 23ではJNIロード、CAM++、ERes2Net、Silero VAD初期化に成功した。

このstatic-link AARでは`jni/armeabi-v7a/libonnxruntime.so`を別置きしない。AAR内の`jni/armeabi-v7a/libsherpa-onnx-jni.so`だけでPepper実機推論済みである。2つの`.so`を前提とするshared-link構成へ置き換えず、`scripts/windows/verify-android-speaker-runtime.ps1`でAARのSHA-256、ARMv7 entry、Gradle AAR metadataを検証する。

x86用AARには別の`libonnxruntime.so`が含まれ、API 23に存在しない`__write_chk`を参照する。このためAPI 23 x86エミュレータでは話者モデルのロードに失敗した。API 28 x86ではinstrumentation 2テストが成功し、Android統合済み3モデル × 2 dataset × 2 queryの12 JSONを回収済みである。このうち中国語WAVの6件はWindows結果との自動parity検証にも合格した。これはAndroid x86経路の証拠であり、Pepper API 23 / ARMv7のparityや性能の代替ではない。

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
- Code/runtime license: Apache License 2.0
- Model weight license: 未確認
- Commercial use: 未確認
- Size: 29,596,978 bytes
- SHA-256: `357A834F702B80161E5B981182C038E18553C1F2CA752ED6CEC2052365D4129B`
- Input: 16 kHz mono normalized PCM
- Output: 512 dimensions

## 3D-Speaker ERes2Net

- Model: `3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx`
- Official distribution: https://github.com/k2-fsa/sherpa-onnx/releases/tag/speaker-recongition-models
- Upstream: https://github.com/modelscope/3D-Speaker
- Code/runtime license: Apache License 2.0
- Model weight license: 未確認
- Commercial use: 未確認
- Size: 26,485,263 bytes
- SHA-256: `C59158379255AD66E161679CCA6AF8D52D51E389E3224AB7D7A7BAAE295C2DB5`
- Input: 16 kHz mono normalized PCM
- Output: 192 dimensions

## Windows speaker benchmark model catalog

Windows用の正本は`config/models.json`である。配布リポジトリ全体をrevision `0743f301363dec56491a490f6d6cbc9d67f9a3bf`へ固定し、ダウンロード後にサイズとSHA-256を両方検証する。

| ID | ファイル | 追加元commit | サイズ | SHA-256 | 次元 |
|---|---|---|---:|---|---:|
| `campplus-en` | `3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx` | `8be2a75c9ed7a590538b268e46fbb65e1aa9d208` | 29,596,978 | `357A834F702B80161E5B981182C038E18553C1F2CA752ED6CEC2052365D4129B` | 512 |
| `campplus-zh-en` | `3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx` | `8be2a75c9ed7a590538b268e46fbb65e1aa9d208` | 28,281,164 | `AA3CFC16963A10586A9393F5035D6D6B57E98D358B347F80C2A30BF4F00CEBA2` | 192 |
| `eres2net-en` | `3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx` | `8be2a75c9ed7a590538b268e46fbb65e1aa9d208` | 26,485,263 | `C59158379255AD66E161679CCA6AF8D52D51E389E3224AB7D7A7BAAE295C2DB5` | 192 |
| `speakernet-m` | `nemo_en_speakerverification_speakernet.onnx` | `0743f301363dec56491a490f6d6cbc9d67f9a3bf` | 23,411,863 | `D204DC8AAC0014B8543F05FC8E310510C7022BC65B6452C203EC205EF7A66B23` | 256 |
| `titanet-s` | `nemo_en_titanet_small.onnx` | `2b697b2295172ca9cfe4881d5d45beb721bc84fc` | 40,257,283 | `AD4A1802485D8B34C722D2A9D04249662F2ECE5D28A7A039063CA22F515A789E` | 192 |

3D-SpeakerとNeMoのコード／変換ツールのApache-2.0を、配布ONNX重みの個別ライセンスとして扱わない。5ファイルすべてについて`weightLicense`と`commercialUse`は`UNVERIFIED`を維持し、確認前は本番候補にしない。

RyuseiNetは今回の5モデル本比較の対象外である。追加学習を行わず、重みの取得・変換・統合・比較も行っていない。候補数を5つへ固定し、前処理とWindows/Android接続の検証範囲を広げないための判断である。

## ONNX Runtime Java for Windows

- Artifact: `com.microsoft.onnxruntime:onnxruntime:1.20.0`
- Distribution: Maven Central
- Runtime: Windows x64 CPU
- License: MIT

1.13.1、1.16.3、1.18.0、1.20.0のDLLロードを同じJava 17プロセスで確認し、1.20.0まで成功した。1.21.0、1.22.0、1.26.0はこのWindows 11端末で`DLL initialization routine failed`となったため採用しない。モデル推論、5モデルのmetadata検査、スコアparityまで成功した1.20.0をVersion Catalogへ固定する。

バイナリはGitへコミットしない。話者ベンチマークとAndroid話者runtimeはKotlin／Gradle経路だけで準備できる。

```powershell
.\scripts\windows\bootstrap.ps1
```

既存の顔モデル`face-reidentification-retail-0095`をOpenVINO IRから再変換するときだけ、従来の顔資産スクリプトを使用する。この既存Python変換は新しい話者ベンチマーク経路には含めない。動画から生成する顔ベンチPNGは、ハッシュ検証済み動画の1.0秒位置から再生成する。

```powershell
.\scripts\setup-local-inference-assets.ps1
```
