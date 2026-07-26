# pepper-person-id-poc

Pepper（Android 6.0 / API 23 / armeabi-v7a）向けの、顔識別と話者識別のPoCです。複数人の顔をTrack IDで追跡し、登録人物ごとの1対N識別と、未登録人物の一時ID識別を行います。

## 画面項目

| 項目 | 内容 |
|---|---|
| 端末診断 | API、ABI、カメラ、マイク、メモリ、ネットワークを確認します。 |
| モデル選択 | 顔検出、顔特徴量、推論基盤、話者モデルを選択します。 |
| 人物登録 | `personId`に正面・左右の顔特徴量を登録します。 |
| リアルタイム顔検出+登録人物識別 | 複数顔を検出・追跡し、登録人物と1対N照合します。 |
| リアルタイム顔検出+特徴量抽出 | 未登録顔ごとに最大20特徴量を取得し、一時人物IDを表示します。 |
| 声登録 | `personId`に声特徴量を登録します。 |
| 話者識別 | 発話を登録話者と1対N照合します。 |
| 未登録リアルタイム話者識別 | 未登録話者へセッション内の一時IDを付けます。 |
| 音声認識 | 日本語の短い発話を文字起こしします。 |
| 統合テスト | 顔識別と話者識別の結果を統合します。 |

## 選択モデル

| 分類 | 選択肢 | 初期選択 |
|---|---|---|
| 顔検出 | ML Kit Face Detection 16.1.7 Bundled / YuNet 2026may | YuNet 2026may |
| 顔検出の推論基盤 | ML Kit 16.1.7 Bundled / OpenCV 5.0.0 / ONNX Runtime Android 1.20.0 / ncnn 20260526 / MNN 3.5.0 / LiteRT 2.1.6 | OpenCV 5.0.0 |
| 顔特徴量 | SFace 2021dec / SFace 2021dec INT8 / face-reidentification-retail-0095 | SFace 2021dec |
| 顔特徴量の推論基盤 | OpenCV 5.0.0 DNN / ONNX Runtime Android 1.20.0 / ncnn 20260526 / MNN 3.5.0 / LiteRT 2.1.6 | OpenCV 5.0.0 DNN |
| 話者特徴量 | WeSpeaker ResNet34-LM / CAM++ Chinese-English（どちらもONNX＋sherpa-onnx CPU） | CAM++ Chinese-English |

## ビルドとPepperへの導入

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon `
  -PtargetAbi=armeabi-v7a :app:testDebugUnitTest :app:assembleDebug

adb -s <Pepperの接続先> install -r app/build/outputs/apk/debug/app-debug.apk
```
