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

顔画面には、人物ID、Track ID、顔検出時間・FPS、顔特徴量抽出時間、使用モデル、推論基盤を表示します。

## 選択モデル

| 分類 | 選択肢 | 初期選択 |
|---|---|---|
| 顔検出 | ML Kit Face Detection 16.1.7 Bundled / YuNet 2026may / YuNet 2023mar INT8 | ML Kit Face Detection 16.1.7 Bundled |
| 顔特徴量 | SFace 2021dec / SFace 2021dec INT8 / face-reidentification-retail-0095 | SFace 2021dec |
| 顔特徴量の推論基盤 | OpenCV 5.0.0 DNN / ONNX Runtime 1.27.0 / ncnn 20260526 / MNN 3.5.0 | OpenCV 5.0.0 DNN |
| 話者特徴量 | CAM++ English / CAM++ Chinese-English / ERes2Net | ERes2Net |

SFace INT8はOpenCVのみ対応しています。YuNetはOpenCV `FaceDetectorYN`で実行します。ONNX Runtime、ncnn、MNNは顔特徴量モデルの推論基盤です。

## ローカル資材

モデル、AAR、ネイティブライブラリはローカルで準備します。初回または更新時に次を実行します。

```powershell
.\scripts\setup-local-inference-assets.ps1
.\scripts\windows\setup-native-face-runtimes.ps1
.\scripts\windows\build-onnxruntime-android-aar.ps1
```

## ビルドとPepperへの導入

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon `
  -PtargetAbi=armeabi-v7a :app:testDebugUnitTest :app:assembleDebug

adb -s <Pepperの接続先> install -r app/build/outputs/apk/debug/app-debug.apk
```

設計、モデル出典、評価条件の詳細は[`docs/`](docs/)と[`specs/`](specs/)を参照してください。
