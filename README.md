# pepper-person-id-poc

## 概要

Pepper上で顔識別・話者識別を検証するAndroidアプリです。

## 依存関係

- Android 6.0 / API 23
- Gradle Wrapper、JDK、Android SDK、ADB
- Git LFS（APK同梱モデルの取得）
- 対応ABI：`armeabi-v7a`、`arm64-v8a`

## 起動手順（Pepper）

### ビルド・インストール・起動

```powershell
git lfs install
git lfs pull

.\gradlew.bat -PtargetAbi=armeabi-v7a :app:assembleBenchmarkDebug

adb devices
adb install -r `
  app\build\outputs\apk\benchmark\debug\app-benchmark-debug.apk

adb shell am start `
  -n com.example.pepper_person_id_poc.benchmark/.MainActivity
```
