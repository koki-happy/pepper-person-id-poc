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
