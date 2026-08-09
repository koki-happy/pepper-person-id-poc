# pepper-person-id-poc

## 概要

Pepper上で顔識別・話者識別を検証するAndroidアプリです。

## 依存関係

- Android 6.0 / API 23
- Gradle Wrapper、JDK、Android SDK、ADB
- Git、Git LFS（LFS管理ファイルを取得する場合）
- Python 3.11以上、ffmpeg（モデル資産の準備）
- 対応ABI：`armeabi-v7a`、`arm64-v8a`

## インストール手順

### リポジトリ取得

リポジトリをcloneしてルートディレクトリへ移動した後に実行します。

```powershell
git lfs install
git lfs pull
```

現行のLFS対象は`app/src/main/assets/videos/*.mp4`です。モデル、AAR、検証用入力、変換生成物はLFSではなく、Git管理外のファイルとしてセットアップスクリプトから取得します。

### モデル・依存ファイル準備

`setup-local-inference-assets.ps1`は不足ファイルをダウンロードし、SHA-256を検証してから必要な変換・派生モデルを生成します。

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r scripts\litert\requirements.lock
.\scripts\setup-local-inference-assets.ps1 -Python .\.venv\Scripts\python.exe

.\gradlew.bat :app:verifyModelCatalog :app:verifyReleaseModelLicenses --no-daemon
```

`ffmpeg`はPATHに追加してください。既存資産を再取得・再生成する場合は`-Force`を付けます。

## Pepperへのインストール・起動

### ビルド・インストール・起動

```powershell
.\gradlew.bat -PtargetAbi=armeabi-v7a :app:assembleBenchmarkDebug

adb devices
adb install -r `
  app\build\outputs\apk\benchmark\debug\app-benchmark-debug.apk

adb shell am start `
  -n com.example.pepper_person_id_poc.benchmark/.MainActivity
```
