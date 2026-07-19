# Windows セットアップと実行

## 方針

Windows のモデル比較は Kotlin/JVM の CLI として実行します。ビルド、テスト、実行の入口は Gradle Wrapper に限定します。

使用するもの:

- Windows x64
- JDK 17
- `gradlew.bat`
- Gradle Kotlin DSL と Version Catalog
- Kotlin/JVM
- ONNX Runtime Java CPU

使用しないもの:

- `uv`、Python、`pip`
- `vcpkg`
- 自作 C/C++、自作 JNI、自作 CMake プロジェクト
- Docker、Nix

Kotlin から ONNX Runtime の既成 Windows ネイティブ DLL は内部的に利用します。「推論エンジンまで純 Kotlin」という意味ではありません。

## 前提資産

Gradle 実行前に、[`config/speaker-benchmark.json`](../../config/speaker-benchmark.json) が参照する次のローカル資産が必要です。

- `models/` 配下の5個の ONNX ファイル
- `data/audio/smoke/` 配下の3個の WAV
- Android をビルドする場合は [`app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar`](../../app/libs/sherpa-onnx-static-link-onnxruntime-1.13.4.aar)

これらの大容量資産は Maven/Gradle 依存ではありません。現在の worktree には配置済みです。クリーン環境で不足している場合は、取得 URL と SHA-256 を固定した [`config/models.json`](../../config/models.json) および [`scripts/windows`](../../scripts/windows/) を確認してから配置してください。モデル重みを取得できることと、利用許諾が得られていることは別問題です。

JVS本評価には利用条件の確認と明示的な同意が必要です。取得物と派生WAVはローカル限定で、再配布しません。

## Gradle Wrapper だけを使うコマンド

リポジトリルートの PowerShell で実行します。

```powershell
Set-Location C:\Users\KokiShimohara\project\pepper-person-id-poc
```

### 1. ツールチェーン確認

```powershell
.\gradlew.bat --version
```

期待値は Java 17 です。`speaker-benchmark` 自身も [`jvmToolchain(17)`](../../speaker-benchmark/build.gradle.kts) を指定しています。

### 2. JVM・Android 単体テスト

```powershell
.\gradlew.bat :speaker-core:test :speaker-benchmark:test :app:testDebugUnitTest
```

### 3. 設定・WAV・モデルの事前検証

```powershell
.\gradlew.bat :speaker-benchmark:run --args="--config config/speaker-benchmark.json --validate-only"
```

ここでは manifest の分割、WAV 形式、モデル SHA-256、サンプルレート、設定値を検証します。推論結果は生成しません。

### 4. 5モデルの Windows ベンチマーク

```powershell
.\gradlew.bat :speaker-benchmark:run --args="--config config/speaker-benchmark.json"
```

出力先は [`results/windows-speaker-benchmark`](../../results/windows-speaker-benchmark/) です。既存ファイルはレポートライターによって置換されます。

### 5. JVSの取得・準備・本評価

```powershell
.\scripts\windows\download-jvs.ps1 -AcceptTerms
.\scripts\windows\prepare-jvs-evaluation.ps1
.\scripts\windows\run-speaker-benchmark.ps1 -Config config/speaker-benchmark-jvs.json
```

`download-jvs.ps1`は3,536,595,425 bytesとSHA-256 `37180e2f87bd1a3e668d7c020378f77cebf61dd57d4d74c71eb0114f386a3999`を検査します。このSHA-256は2026-07-13に取得したアーカイブのローカル再現性pinで、公式公表checksumではありません。

`prepare-jvs-evaluation.ps1`は24 kHz原音を16 kHzへリサンプルし、168 WAVとmanifestを[`data/audio/jvs-evaluation`](../../data/audio/jvs-evaluation/)へ生成します。本評価の出力は[`results/jvs-speaker-benchmark`](../../results/jvs-speaker-benchmark/)です。

### 6. ARMv7 Android AAR メタデータ確認

Android SDK がある Windows 環境では、Pepper 用 ABI を明示します。

```powershell
.\gradlew.bat -PtargetAbi=armeabi-v7a :app:checkDebugAarMetadata
```

### 7. Pepper 用 debug APK のビルド

```powershell
.\gradlew.bat -PtargetAbi=armeabi-v7a :app:assembleDebug :app:assembleDebugAndroidTest
```

Android の AAR 構成は [Android 音声パイプライン監査](./android-audio-pipeline-audit.md)、実機 JSON の回収は [Windows/Android parity](./windows-android-parity.md) を参照してください。

## ベンチマーク設定の区別

[`config/speaker-benchmark.json`](../../config/speaker-benchmark.json)は中国語3 WAVの配線スモークです。[`config/speaker-benchmark-jvs.json`](../../config/speaker-benchmark-jvs.json)がJVS日本語本比較用で、threshold/marginはdevelopmentのみで選択します。両方ともVADは無効です。
