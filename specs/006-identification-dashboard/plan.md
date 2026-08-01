# Implementation Plan: 識別ダッシュボードと設定画面分離

**Branch**: `codex/face-identification` | **Date**: 2026-08-01 | **Spec**: [spec.md](spec.md)

## Summary

MainActivityを横画面固定にし、起動先を顔識別へ変更する。顔・音声画面はPepper向け共通ダッシュボード配置とし、右下へ負荷検証時だけ単一のミュート済みサンプル動画プレイヤーを置く。パラメータ設定とモデル・推論基盤セット選択を別画面へ分離する。ARM64スマホで受入後、ARMv7 Pepperへ導入する。

## Technical Context

**Language/Version**: Kotlin / JVM 11

**Primary Dependencies**: Jetpack Compose Material3、CameraX、Android AudioRecord、Android標準VideoView/MediaPlayer、既存OpenCV・sherpa-onnx・推論基盤

**Storage**: SharedPreferences設定、メモリ内のセッション限定匿名クラスタ、APK同梱MP4

**Testing**: JUnit、Android instrumentation、Gradle assemble、ADB UIツリー・スクリーンショット・logcat

**Target Platform**: ARM64 Androidスマートフォン、Android 6.0/API 23/ARMv7 Pepperタブレット

**Project Type**: 単一Androidアプリ

**Performance Goals**: 既存識別処理のスループットを維持し、動画再生中も操作可能であること

**Constraints**: オフライン、横画面固定、Pepper 1GB RAM、画像・動画・音声入力の永続保存禁止、動画音声は初期ミュート

**Scale/Scope**: 4画面、2識別方式、4モデルセットプルダウン、29設定項目、2サンプル動画から単一選択

## Constitution Check

- PASS: 匿名IDのみを表示し、顔登録機能を追加しない。
- PASS: カメラ・マイク入力は保存せず、匿名クラスタはセッション限定とする。
- PASS: Pepper API 23/ARMv7を最終受入対象とし、スマホ証跡と区別する。
- PASS: モデル・推論基盤は明示セットで選択し、暗黙フォールバックを行わない。
- PASS: 仕様、設計、タスク、実装、端末証跡を分離する。

## Project Structure

```text
app/src/main/
├── AndroidManifest.xml
├── assets/videos/
├── java/com/example/pepper_person_id_poc/
│   ├── MainActivity.kt
│   ├── domain/config/PocSettings.kt
│   ├── infrastructure/repository/SharedPreferencesSettingsRepository.kt
│   └── ui/
│       ├── component/SampleVideoPlayer.kt
│       ├── navigation/AppScreen.kt
│       ├── screen/CameraPreviewScreen.kt
│       ├── screen/AudioRecordingScreen.kt
│       ├── screen/SettingsScreen.kt
│       └── screen/ModelSelectionScreen.kt
└── res/

app/src/test/
app/src/androidTest/
specs/006-identification-dashboard/
```

**Structure Decision**: 既存の単一Activity・Compose画面構成を維持し、共通動画部品と設定モデルだけを追加する。

## Design Decisions

1. 画面方向はManifestで横画面固定とする。
2. MainUiStateの初期画面を匿名顔識別へ変更する。
3. 顔・音声画面は左に入力、中央に性能・結果、右下に動画、上部または下部に主要操作を置く共通比率を使う。
4. 動画は設定で「非表示／20MB／200MB」を単一選択し、選択時だけ`assets/videos/`の1本をMediaPlayerとTextureViewで繰り返し再生して音量を0に固定する。
5. 音声入力は一定間隔で表示用音量履歴へ変換し、固定幅ブロックとして時間方向の横スクロール領域に描画する。
6. パフォーマンスと識別結果は共通の横スクロール表部品で表示する。
7. モデル候補は既存台帳と互換性判定から、実行可能なモデル・推論基盤セットだけを生成する。
8. 複数サンプルOFF時は最大更新件数を1として識別コーディネータへ渡し、後続更新を抑止する。
9. パラメータは単一の検証済み設定モデルへ保持し、保存時にスキーマ移行・範囲検証を行う。
10. 設定画面は基本と5つの詳細カテゴリを1つの縦スクロール一覧にし、顔検出方式に依存する項目だけ条件表示する。
11. 保存値は画面表示だけでなく、各処理オブジェクトの再生成時にコンストラクタへ渡して固定値を置換する。
12. スマホとPepperは同じUIソースを使用し、ABIだけを分けてビルドする。
13. 音量の内部値はdBFSのまま保持し、グラフ表示だけを0～100%へ正規化する。
14. パフォーマンス表は狭い共通セル幅を使用し、単位をヘッダーへ集約する。話者表ではRTFを計算負荷の直後に置く。

## Post-design Constitution Check

全ゲートPASS。サンプル動画はユーザー提供の固定表示資産であり、カメラ・マイク入力の保存ではない。Pepper導入はスマホ受入後に限定する。
