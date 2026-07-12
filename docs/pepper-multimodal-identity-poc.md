# Pepper向け 顔識別・話者識別・発話記録PoC 調査結果・実装計画

更新日: 2026-07-12

## 対象リポジトリの構成

- プロジェクト: `pepper-person-id-poc`
- Androidモジュール: `app` 1つ
- APK: 1つ
- 言語: Kotlin
- UI: Jetpack Compose
- Activity: `MainActivity` 1つ
- パッケージ: `com.example.pepper_person_id_poc`
- `minSdk`: 23
- `targetSdk`: 36
- 現状: Android StudioのEmpty Activity相当。顔識別、話者識別、音声認識、QiSDK連携は未実装

PoCでは設定用Activityとメイン用Activityを分けない。起動時に`MainActivity`内の設定画面を表示し、各機能画面へ遷移する。各機能画面の戻るボタンとAndroidのシステム戻る操作は設定画面へ戻る。設定画面でシステム戻る操作を行った場合のみActivityを終了する。

```text
MainActivity
└── AppScreen
    ├── Settings（起動時）
    ├── DeviceDiagnostics
    ├── PersonRegistration
    ├── FaceIdentification
    ├── SpeakerIdentification
    ├── Transcription
    ├── FusionTest
    ├── ConversationHistory
    └── BenchmarkResults
```

## 既存コードで参考にできる処理

### sbr-pepper-ticket-reception

- リポジトリ: `C:\Users\KokiShimohara\AndroidStudioProjects\project\pepper\sbr-pepper-ticket-reception`
- URL: https://github.com/HappyHackInc/sbr-pepper-ticket-reception

| ファイル | 参考にする処理 |
|---|---|
| `application/contract/RobotDevice.kt` | Android・Pepper固有処理をインターフェースの背後へ分離する構成 |
| `infrastructure/device/Pepper.kt` | QiSDK登録、Robot Focus、LifecycleObserver、SpeechRecognizer、停止・解放 |
| `infrastructure/ui/activity/BaseActivity.kt` | ActivityライフサイクルとPepperデバイス管理 |
| `infrastructure/ui/activity/MainActivity.kt` | `RECORD_AUDIO`権限要求、Compose画面の表示期間に合わせた開始・停止 |
| `application/use_case/user/chat/StartSpeechRecognitionUseCase.kt` | 音声認識をUIから分離する構成 |
| `infrastructure/ui/screen/SettingScreen.kt` | 戻るボタン付き設定画面、スクロール、状態に基づくCompose UI |
| `infrastructure/ui/viewmodel/SettingViewModel.kt` | 設定状態と保存処理の分離 |
| `infrastructure/repository/EncryptedPreferenceSettingRepository.kt` | 設定値の永続化とRepository分離 |
| `infrastructure/di/ServiceNowBindingModule.kt` | Hiltによるcontractと実装の関連付け |

参考リポジトリの`application`、`infrastructure`、`ui/state`、`ui/viewmodel`、`ui/screen`という責務分離を採用する。ただしPoCで不要なJLL固有処理や過剰な抽象化は追加しない。

### rb-blocks-client-android

- リポジトリ: https://github.com/HappyHackInc/rb-blocks-client-android
- `QrReaderManager.java`: 前面カメラ選択、開始、停止、権限、イベント通知、リソース解放
- `LauncherActivity.java`: `CAMERA`、`RECORD_AUDIO`の起動時権限確認
- `FaceRecogManager.java`: HumanAwarenessの人物一覧、人物出現・消失イベント、リスナー解除

### fogo_reception_1f

- リポジトリ: https://github.com/HappyHackInc/fogo_reception_1f
- `HumanDetectionSensor.kt`: Kotlin CoroutinesとHumanAwarenessリスナーの`setup()`、`release()`

### hh-dev-tools

- リポジトリ: https://github.com/HappyHackInc/hh-dev-tools
- `threshold-voice-recognizer/AudioSensor.kt`
- `threshold-voice-recognizer/MyAudioSensor.kt`
- 参考範囲: `AudioRecord`、PCM 16-bit mono、バッファサイズ、録音開始・停止、音量計算

## 既存コードで参考にできない処理

- `SettingActivity`とメイン系Activityを分離する構成
- QRコード・Data Matrix検出処理
- `FaceRecogManager`を顔からの個人識別として扱うこと
- `SpeechRecognizer`をPCM取得や話者識別に使うこと
- 音声読み取りをメインLooperで行う処理
- 顔画像と音声を直接比較する処理
- 発話内容や発話した名前から人物を判定する処理
- Activity、Composable、UseCaseへモデル固有処理を直接記述すること
- JLL固有のログイン、WebView、API設定
- 画面終了ごとにアプリプロセスを強制終了する処理

## 旧Pepper実機で確認済みの項目

以下はADBによる実機確認値を正本とする。

| 項目 | ADBによる実機確認値 |
|---|---|
| メーカー | ARTNCORE |
| モデル／デバイス | LPT_200AR |
| Android | 6.0（API 23） |
| ビルドID | MRA58K |
| ハードウェア | `mt8127`／ART&CORE ANC8127 Tablet Board |
| CPUアーキテクチャ | ARMv7-A、32bit（`armv7l`） |
| CPU | ARM Cortex-A7系、4コア（CPU part `0xc07`） |
| CPU動作周波数 | 最小598 MHz、最大1.3 GHz |
| 主ABI | `armeabi-v7a` |
| 対応ABI | `armeabi-v7a,armeabi` |
| CPU命令機能 | NEON、VFPv3、VFPv4、Thumb-2など |
| RAM | 994,364 kB（約971 MiB、仕様上約1 GB） |
| 取得時の利用可能RAM | 436,208 kB（約426 MiB） |
| Swap | 497,180 kB（取得時の空き368,588 kB） |
| `/data`容量 | 21.6 GB（使用7.6 GB、空き14.0 GB） |
| 画面解像度 | 1280×800 |
| 画面密度 | 213 dpi |
| OpenGL ES | 2.0（`ro.opengles.version=131072`） |
| Linuxカーネル | 3.18.22+、ARMv7、SMP PREEMPT |
| カメラ | 前面カメラ1台、Orientation 0 |
| ネットワーク | Wi-Fi接続、Android上でVALIDATED |

確認できたカメラプレビュー候補には`320x240`、`640x480`、`800x600`、`1280x720`、`1280x960`などがある。初期試験は`640x480`とし、処理負荷が高い場合は`320x240`へ下げる。

## 旧Pepper実機で確認が必要な項目

- 16kHz mono PCM 16-bitの`AudioRecord`初期化・連続読み取り
- 16kHzが利用できない場合の44.1kHz mono初期化
- 入力音量、ノイズフロア、VAD閾値
- カメラと`AudioRecord`の同時利用
- `SpeechRecognizer`と`AudioRecord`の同時利用可否
- `SpeechRecognizer.isRecognitionAvailable()`と日本語認識
- QiSDKのRobot Focus取得・喪失
- HumanAwareness利用可否
- OpenCV JNIの`armeabi-v7a`ロード
- YuNet、SFace、face-reidentification-retail-0095のモデルロードと推論
- sherpa-onnx JNI、Silero VAD、CAM++、ERes2Netのモデルロードと推論
- モデルごとの処理時間、ピークRAM、Swap使用量
- カメラ、音声、QiSDKを併用した15分連続動作

## 顔検出方式候補

第一候補はYuNetとする。顔識別モデルの比較時も顔検出をYuNetへ固定する。

- OpenCV DNNのCPU推論
- 顔矩形とランドマークを取得可能
- SFaceの顔位置合わせへ接続しやすい
- 全カメラフレームで推論せず、診断結果を基に検出fpsを制限する
- 検出間は一時`trackId`で追跡し、特徴量の生成頻度を抑える

最新構成を試す前に、OpenCV Android配布物のAPI 23・`armeabi-v7a`対応を実ファイルで確認する。成立しない場合は互換性が確認できる4.x系とYuNet `2023mar`を比較する。

## 顔識別モデル候補

### 第一候補: SFace `2021dec`

- YuNetとの組み合わせがOpenCV Zooのデモで提供されている
- OpenCV DNNへ推論基盤を統一できる
- cosine類似度またはL2距離で比較する
- 初期試験はFP32とし、INT8は速度と精度を別途実測する
- OpenCV Zoo本体だけでなく、モデルファイル個別のライセンスを導入前に確認する

### 第二候補: face-reidentification-retail-0095

- SFaceとは異なるMobileNet V2系
- 256次元特徴量
- 1.107Mパラメータ、0.588 GFLOPs
- OpenCV DNNでの入力形式、前処理、出力正規化、配布形式を検証する
- モデル個別の配布条件と商用利用条件を導入前に確認する

## 話者識別モデル候補

発話区間検出はSilero VADへ固定し、話者特徴量モデルだけを比較する。

### 第一候補: 3D-Speaker CAM++

- 3D-Speaker上で7.2Mパラメータ
- sherpa-onnxのSpeaker Embedding機能で利用する候補
- 16kHz入力

### 第二候補: 3D-Speaker ERes2Net

- CAM++とは異なるアーキテクチャ
- 同じsherpa-onnx基盤で比較可能
- ERes2Net-baseは3D-Speaker上で6.61Mパラメータ

3D-SpeakerリポジトリはApache License 2.0だが、利用するONNXファイル、学習データ由来条件、商用利用条件をモデルファイル単位で確認する。英語VoxCeleb学習モデルの日本語短文における精度は実測する。

## 音声認識方式候補

初期PoCではAndroid `SpeechRecognizer`を参考実装とする。ただし取得済みPCMを直接入力できないため、以下を明示する。

- 話者識別と音声認識で完全に同じ音声区間を保証できない
- PoC上の暫定実装である
- `AudioRecord`と同時にマイクを利用できない可能性がある
- 将来は同一PCMを入力できるローカルまたはリモート`TranscriptionEngine`へ交換する

## ローカル推論の成立可能性

API・ABI上は成立する可能性があるが、実機性能上の成立は未確認である。

肯定材料:

- API 23
- ARMv7、NEON対応
- 約1GB RAMと約486MiBのSwap
- 前面カメラとマイク
- sherpa-onnxはAndroid arm32とSpeaker Identificationを対応範囲としている

制約:

- Cortex-A7 4コア、最大1.3GHz
- 32bitプロセス
- 取得時の利用可能RAMは約426MiB
- OpenGL ES 2.0のためGPU推論を前提にしない
- Swap発生時はクラッシュしなくても応答時間が大きく悪化する可能性がある

CPU推論を基本とし、識別モデルを複数同時常駐させない。顔検出、顔特徴量生成、話者特徴量生成の同時集中を制御する。

## リモート推論が必要になる条件

- `armeabi-v7a`向けJNIがロードできない
- API 23でモデル初期化に失敗する
- OOM、連続GC、著しいSwap増加が発生する
- 顔識別更新が2秒を超える
- 発話終了から結果表示まで5秒を超える
- カメラプレビューまたはPCM取得が停止する
- QiSDKとの併用が不安定になる
- 15分連続試験に失敗する

リモート実装も`FaceEmbeddingEngine`、`SpeakerEmbeddingEngine`、`TranscriptionEngine`の同じインターフェースへ適合させる。生画像・生音声を送信する場合は、送信・保存・削除・暗号化条件を別途確定する。

## 追加予定ファイル

```text
app/src/main/java/com/example/pepper_person_id_poc/
├── application
│   ├── contract
│   ├── model
│   └── usecase
├── domain
│   ├── person
│   ├── face
│   ├── speaker
│   ├── fusion
│   └── conversation
├── infrastructure
│   ├── device/camera
│   ├── device/audio
│   ├── device/pepper
│   ├── face
│   ├── speaker
│   ├── transcription
│   ├── repository
│   ├── benchmark
│   └── di
└── ui
    ├── screen
    ├── component
    ├── state
    └── viewmodel
```

主要な追加候補:

- `AppScreen.kt`
- `MainViewModel.kt`
- `SettingsScreen.kt`
- `DeviceDiagnosticsScreen.kt`
- `PersonRegistrationScreen.kt`
- `FaceIdentificationScreen.kt`
- `SpeakerIdentificationScreen.kt`
- `TranscriptionScreen.kt`
- `FusionTestScreen.kt`
- `ConversationHistoryScreen.kt`
- `BenchmarkResultsScreen.kt`
- `PersonProfile.kt`
- `PersonRepository.kt`
- `FaceDetector.kt`
- `FaceTracker.kt`
- `FaceEmbeddingEngine.kt`
- `FaceIdentifier.kt`
- `PcmAudioRecorder.kt`
- `VoiceActivityDetector.kt`
- `SpeakerEmbeddingEngine.kt`
- `SpeakerIdentifier.kt`
- `TranscriptionEngine.kt`
- `PersonFusionEngine.kt`
- `ConversationTurn.kt`
- `BenchmarkLogger.kt`
- `PepperLifecycleController.kt`

## 変更予定ファイル

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/pepper_person_id_poc/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/proguard-rules.pro`

モデル本体は初期段階でGitリポジトリへ追加しない。

## 実装手順

1. Gradleラッパーと空プロジェクトのビルド成立を確認する
2. 単一Activityで設定画面から始まる画面遷移を実装する
3. エミュレータ向けFakeデバイス実装を用意する
4. 権限、ABI、CPU、RAM、Swap、ストレージ、カメラ、音声、ネットワークの端末診断を実装する
5. QiSDKとRobot Focusのライフサイクル管理を実装する
6. JSON Lines形式のベンチマーク基盤を実装する
7. カメラプレビュー、YuNet顔検出、バウンディングボックス、`trackId`を実装する
8. 人物登録、SFace特徴量、1対N顔識別、`Unknown`判定を実装する
9. `AudioRecord`、音声レベル、Silero VAD、発話区間収集を実装する
10. CAM++による声登録、1対N話者識別、`Unknown speaker`判定を実装する
11. `SpeechRecognizer`による暫定文字起こしを実装する
12. 顔観測、話者結果、文字起こしを`PersonFusionEngine`で統合する
13. `ConversationTurn`、会話履歴、処理時間表示を実装する
14. ERes2Netとface-reidentification-retail-0095を同条件で比較する
15. Pepperで人物A、人物B、未登録人物、競合、雑音、複数顔、15分連続試験を行う

通常は`emulator-5554`でUI、状態遷移、Fake実装、単体テストを先に確認する。実装が安定した後に`192.168.10.110:5555`のPepperを使用する。

## リスク

- 現行`gradlew.bat`は空の`-classpath`を指定するため、`-classpath requires class path specification`で失敗する
- `java -jar gradle\wrapper\gradle-wrapper.jar tasks`ではGradle設定の読み込みに成功している
- AGP 9.2.1、Kotlin 2.2.10とQiSDK 1.8.5の互換性は未確認
- OpenCVのAndroid配布物がAPI 23・`armeabi-v7a`を含むか未確認
- sherpa-onnxの選定バージョンとONNX Runtimeの組み合わせを実ファイルで確認する必要がある
- 顔・話者モデルの配布ファイル単位のライセンス確認が必要
- カメラ、OpenCV、VAD、話者モデル、QiSDKの同時利用でRAMとCPUが不足する可能性がある
- `SpeechRecognizer`と`AudioRecord`がマイクを競合する可能性がある
- Swapによりクラッシュせずに性能目標だけを大幅に超過する可能性がある
- 顔・声特徴量は生体情報として、ログ禁止、バックアップ除外、全削除を実装する必要がある

## 未確定事項

- OpenCVとsherpa-onnxの採用バージョンおよび`armeabi-v7a`実体
- 各モデルファイルの商用利用条件
- 顔・声の登録サンプル数
- 声登録1サンプルの長さ
- `faceThreshold`、`speakerThreshold`、`combinedThreshold`の初期値
- 最低発話時間、VAD閾値、許容ノイズ
- 顔観測の保持時間と追跡終了条件
- 特徴量の暗号化・永続化方式
- アプリ再起動後の登録データ保持方針
- リモート推論先、認証、通信、データ保存条件
- ベンチマークファイルの保存先と回収方法

## 実装開始ゲート

2026-07-12に調査結果の確認を受け、実装を開始した。大規模ライブラリとモデルは機能単位で配布元、ライセンス、ABI、APIレベルを確認してから追加し、モデル本体はGitへコミットしない。

## 実装後のPepper実測

2026-07-12時点の`LPT_200AR`実測。前面カメラに顔がいない状態で確認した。

| 項目 | 実測値 |
|---|---|
| CameraX前面プレビュー | 640x480、動作 |
| OpenCV | 5.0.0 `armeabi-v7a`、ロード成功 |
| YuNet | `face_detection_yunet_2026may.onnx`、ロード・推論成功 |
| YuNet顔なし推論 | 約490～631ms |
| SFace | `face_recognition_sface_2021dec.onnx`、初期化成功 |
| SFace初回初期化 | 2,844ms |
| アプリTOTAL PSS | 約140,460kB |
| Native Heap PSS | 約83,590kB |
| TOTAL SWAP PSS | 約16,392kB |
| 画面離脱後のカメラ | `Active Camera Clients: []` |
| クラッシュ・OOM | 発生なし |

YuNetの顔検出処理は2秒以内の更新目標を満たす見込みがある。CPU占有を抑えるため、解析開始間隔は1,000msとする。実人物の顔検出、SFace特徴量生成、登録、1対N識別は人物がカメラ範囲にいる条件で追加確認する。
