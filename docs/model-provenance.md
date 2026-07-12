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
