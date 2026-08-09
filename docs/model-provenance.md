# Model provenance

モデル本体はライセンス、ABI、APIレベル、実機性能を確認してから取得し、Gitにはコミットしない。
モデル別の取得・変換・検証手順は`docs/model-workflow.md`を正本とする。

## YuNet face detection

- Model: `face_detection_yunet_2026may.onnx`
- Official source: https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet
- Download: https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_detection_yunet/face_detection_yunet_2026may.onnx
- License: MIT（OpenCV Zooの`models/face_detection_yunet/LICENSE`）
- Size: 229,738 bytes
- SHA-256: `EBAFCE4E3C118D6554634BE5C27AB333B4C047A9A8C3FAF1D7CF93101C22F0F0`
- Local path: `app/src/benchmark/assets/models/face_detection_yunet_2026may.onnx`

PowerShell:

```powershell
New-Item -ItemType Directory -Force app/src/benchmark/assets/models | Out-Null
Invoke-WebRequest -UseBasicParsing `
  'https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_detection_yunet/face_detection_yunet_2026may.onnx' `
  -OutFile app/src/benchmark/assets/models/face_detection_yunet_2026may.onnx
Get-FileHash -Algorithm SHA256 app/src/benchmark/assets/models/face_detection_yunet_2026may.onnx
```

### YuNet 2023mar INT8

- Model: `face_detection_yunet_2023mar_int8.onnx`
- Official distribution: https://huggingface.co/opencv/face_detection_yunet/blob/main/face_detection_yunet_2023mar_int8.onnx
- License: MIT
- Size: 100,416 bytes
- SHA-256: `321AA5A6AFABF7ECC46A3D06BFAB2B579DC96EB5C3BE7EDD365FA04502AD9294`
- Local path: `app/src/benchmark/assets/models/face_detection_yunet_2023mar_int8.onnx`
- Runtime: OpenCV `FaceDetectorYN`

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
- Local path: `app/src/benchmark/assets/models/face_recognition_sface_2021dec.onnx`

PowerShell:

```powershell
Invoke-WebRequest -UseBasicParsing `
  'https://github.com/opencv/opencv_zoo/raw/refs/heads/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx' `
  -OutFile app/src/benchmark/assets/models/face_recognition_sface_2021dec.onnx
Get-FileHash -Algorithm SHA256 app/src/benchmark/assets/models/face_recognition_sface_2021dec.onnx
```

### SFace 2021dec INT8

- Model: `face_recognition_sface_2021dec_int8.onnx`
- Official distribution: https://huggingface.co/opencv/face_recognition_sface/blob/main/face_recognition_sface_2021dec_int8.onnx
- License: Apache License 2.0
- Architecture: INT8 quantized MobileFaceNet trained with SFace loss
- Input: float `1x3x112x112`; quantization is contained in the graph
- Output: float 128 dimensions
- Size: 9,896,933 bytes
- SHA-256: `2B0E941E6F16CC048C20AEE0C8E31F569118F65D702914540F7BFDC14048D78A`
- Local path: `app/src/benchmark/assets/models/face_recognition_sface_2021dec_int8.onnx`

匿名SFace特徴量はアプリセッション中のメモリだけで扱い、生の顔画像、切り出し画像、特徴量をファイルへ保存しない。

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

ARMv7 ELFはARMv7-A、Thumb-2、VFPv3、NEONを使用し、Pepper `LPT_200AR`の命令セットと一致する。Pepper API 23ではJNIロード、CAM++ Chinese-English、Silero VAD初期化に成功した。

このstatic-link AARでは`jni/armeabi-v7a/libonnxruntime.so`を別置きしない。AAR内の`jni/armeabi-v7a/libsherpa-onnx-jni.so`だけでPepper実機推論済みである。2つの`.so`を前提とするshared-link構成へ置き換えず、`scripts/windows/verify-android-speaker-runtime.ps1`でAARのSHA-256、ARMv7 entry、Gradle AAR metadataを検証する。

x86用AARには別の`libonnxruntime.so`が含まれ、API 23に存在しない`__write_chk`を参照する。このためAPI 23 x86エミュレータでは話者モデルのロードに失敗した。API 28 x86ではinstrumentation 2テストが成功し、Android統合済み3モデル × 2 dataset × 2 queryの12 JSONを回収済みである。このうち中国語WAVの6件はWindows結果との自動parity検証にも合格した。これはAndroid x86経路の証拠であり、Pepper API 23 / ARMv7のparityや性能の代替ではない。

## Silero VAD

- Model: `silero_vad.onnx`
- Official distribution: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
- Upstream: https://github.com/snakers4/silero-vad
- License: MIT
- Size: 643,854 bytes
- SHA-256: `9E2449E1087496D8D4CABA907F23E0BD3F78D91FA552479BB9C23AC09CBB1FD6`

## pyannote segmentation 3.0

- Runtime artifact: `pyannote-segmentation-3.0.onnx`
- Official sherpa-onnx distribution: https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2
- Upstream: https://huggingface.co/pyannote/segmentation-3.0
- Conversion scripts: https://github.com/k2-fsa/sherpa-onnx/tree/master/scripts/pyannote/segmentation
- License: MIT（配布archive内`LICENSE`）
- Archive SHA-256: `24615EE884C897D9D2BA09BB4D30DA6BB1B15E685065962DB5B02E76E4996488`
- Model size: 5,992,913 bytes
- Model SHA-256: `220AD67CA923BEF2FA91F2390C786097BF305BCEB5E261D4AF67B38E938E1079`
- Input: 16 kHz mono float PCM, `[1, 1, 160000]`
- Output: 589 frames × 7 powerset classes, receptive-field step 270 samples
- Android runtime: sherpa-onnx 1.13.4 static-link AAR（CPU、ARMv7対応）。Pepperでは共有ONNX Runtimeを使用しない。
- Pepper status: API 23 / ARMv7でAPKを再インストール後、`MainActivity`の前面維持とカメラ・話者識別画面の表示を確認。実音声を用いた話者区間の精度評価は別途必要。

モデル本体はGitへコミットせず、archiveとモデルの両SHA-256を照合して
`app/src/benchmark/assets/models/pyannote-segmentation-3.0.onnx`へ配置する。

## LiteRT変換済みYuNet / SFace / face-0095

正本ONNXを変更せず、`scripts/litert/requirements.lock`と
`scripts/litert/Dockerfile`で固定した`onnx2tf 1.28.2`環境から再生成する。
生成物はGitへコミットせず、benchmark assetsへ配置する。

```powershell
py -3 scripts/litert/convert_models.py `
  app/src/benchmark/assets/models/face_detection_yunet_2026may.onnx `
  app/src/benchmark/assets/models/face_detection_yunet_2026may_320.tflite `
  --source-sha256 ebafce4e3c118d6554634be5c27ab333b4c047a9a8c3faf1d7cf93101c22f0f0 `
  --input-shape input:1,3,320,320 `
  --manifest scripts/litert/manifests/yunet-2026may-320-litert.json

py -3 scripts/litert/convert_models.py `
  app/src/benchmark/assets/models/face_recognition_sface_2021dec.onnx `
  app/src/benchmark/assets/models/face_recognition_sface_2021dec.tflite `
  --source-sha256 0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79 `
  --manifest scripts/litert/manifests/sface-2021dec-litert.json

py -3 scripts/litert/convert_models.py `
  app/src/benchmark/assets/models/face-reidentification-retail-0095.onnx `
  app/src/benchmark/assets/models/face-reidentification-retail-0095.tflite `
  --source-sha256 861d2edc47214f19fe973f97a05b2bd8ba61e103279fe232f0365534903dd589 `
  --manifest scripts/litert/manifests/face-0095-litert.json
```

同じ固定環境で各変換を2回実行し、2回とも下表のsizeとSHA-256が一致した。
公開する変換証跡は`scripts/litert/manifests/`の正規化manifestと本方針だけで、
一時ディレクトリ、変換物、比較入力・出力はGitへ含めない。

| Artifact | Tensor contract | Size | SHA-256 |
|---|---|---:|---|
| `yunet-2026may-litert-fp32-320` | `input [1,3,320,320]` → `Identity..Identity_11`; anchors 1600/400/100 | 238,836 | `E9EF1BEE56DB8D5AEA88EDBA67515FC182D56EEB36ACFBF8B1DFF7F77934885D` |
| `sface-2021dec-litert-fp32` | `data [1,3,112,112]` → `Identity [1,128]` | 38,549,364 | `0859A63B74C8373CE47464D1834678193786282AAD35911491C0505F2CACBA1E` |
| `face-0095-litert-fp32` | `0 [1,3,128,128]` raw BGR `0..255` → `Identity [1,1,1,256]` | 4,475,508 | `6AD2A160AB016B84A55442DCB2CD1D36B684AA9E8324357411B14A77756C68D8` |

YuNetの`Identity_11`だけは変換時の`10x10x10` transpose `(2,0,1)`を含む。
デコード前に`LiteRtYuNetFaceDetector`が逆置換`(1,2,0)`を行うことが契約であり、
raw tensorのままONNX相当とは扱わない。逆置換後のkps32最大絶対誤差は
`1.31e-5`、その他11出力は`6.8e-6`以下。SFaceの固定入力に対する正規化
embedding cosineは`0.99999999988`。いずれもARM64 AndroidとPepperでの
実機受入が終わるまでは`BUILDABLE`であり`VERIFIED`ではない。
face-0095の固定入力に対する正規化embedding cosineは`0.99999994`、
最大絶対誤差は`2.17e-7`であり、`LiteRt0095EmbeddingEngine`が出力を
256要素へflattenしてL2正規化する。

## 3D-Speaker CAM++ Chinese-English

- Model: `3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx`
- Official model page: https://modelscope.cn/models/iic/speech_campplus_sv_zh_en_16k-common_advanced
- Upstream: https://github.com/modelscope/3D-Speaker
- Model weight license: Apache-2.0（ModelScope公式モデルページ）
- Commercial use: 許可
- Size: 28,281,164 bytes
- SHA-256: `AA3CFC16963A10586A9393F5035D6D6B57E98D358B347F80C2A30BF4F00CEBA2`
- Input: 16 kHz mono normalized PCM
- Output: 192 dimensions

## Windows speaker benchmark model catalog

Windows用の正本は`config/models.json`である。CAM++ Chinese-EnglishはModelScope公式モデルページ、WeSpeakerはmodel cardのrevision `f0c48c298fd835726c27956a5d617bad7115627e`を根拠とする。いずれもダウンロード後にサイズとSHA-256を検証する。

| ID | ファイル | 追加元commit | サイズ | SHA-256 | 次元 |
|---|---|---|---:|---|---:|
| `campplus-zh-en` | `3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx` | `8be2a75c9ed7a590538b268e46fbb65e1aa9d208` | 28,281,164 | `AA3CFC16963A10586A9393F5035D6D6B57E98D358B347F80C2A30BF4F00CEBA2` | 192 |
| `wespeaker-resnet34-lm` | `voxceleb_resnet34_LM.onnx` | `f0c48c298fd835726c27956a5d617bad7115627e` | 26,530,309 | `7BB2F06E9DF17CDF1EF14EE8A15AB08ED28E8D0EF5054EE135741560DF2EC068` | 256 |

現行カタログではCAM++ Chinese-EnglishとWeSpeaker ResNet34-LMを`releaseModelIds`として許諾確認済みである。VoxCeleb等のデータ条件、NOTICE/SBOM、Pepper実機受入は別ゲートである。

RyuseiNetは今回の過去比較と現行設定2モデルの対象外である。追加学習を行わず、重みの取得・変換・統合・比較も行っていない。

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
