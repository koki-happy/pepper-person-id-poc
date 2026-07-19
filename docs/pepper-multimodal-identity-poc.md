# Pepper向け 顔識別・話者識別PoC 実装・性能検証結果

更新日: 2026-07-13

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
- 現状: 登録済み人物のリアルタイム顔識別と、未登録人物のセッション内一時ID再識別を実装済み。話者識別はWindowsで5モデルのJVS日本語本比較を実施済み。Pepperマイクの反復評価とモデル重みの利用許諾確認は未完了

本PoCの完了範囲はローカルの顔識別と話者識別である。当初候補に含めた音声認識、顔と声の統合、会話履歴、QiSDK連携は今回の実装範囲外で、画面もプレースホルダーのままとする。

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

## 旧Pepper実機の確認状況

- ~~16kHz mono PCM 16-bitの`AudioRecord`初期化・連続読み取り~~（2026-07-12確認済み）
- 16kHzが利用できない場合の44.1kHz mono初期化
- 入力音量、ノイズフロア、VAD閾値
- カメラと`AudioRecord`の同時利用
- `SpeechRecognizer`と`AudioRecord`の同時利用可否
- `SpeechRecognizer.isRecognitionAvailable()`と日本語認識
- QiSDKのRobot Focus取得・喪失
- HumanAwareness利用可否
- ~~OpenCV JNIの`armeabi-v7a`ロード~~（OpenCV 5.0.0で確認済み）
- ~~YuNet、SFace、変換ONNX版`face-reidentification-retail-0095`のモデルロードと推論~~（OpenCV 5.0.0／Pepper ARMv7で確認済み）
- ~~sherpa-onnx JNI、Silero VAD、CAM++、ERes2Netのモデルロードと推論~~（確認済み）
- ~~モデルごとの処理時間とNative heap~~（顔・話者スモーク試験で確認済み。ピークRAM・Swapのモデル別連続測定は未実施）
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

Androidのライブ録音経路では発話区間検出をSilero VADへ固定する。Windows上の最初のモデル比較ではVADを通さず、同じ保存済みWAVと発話区間を各モデルへ入力する。

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

API・ABI上の成立と、YuNet/SFace、Silero VAD、CAM++、ERes2Netのロード・推論は旧Pepper実機で確認済みである。ERes2Netは精度スモーク試験では暫定推奨だが、最大約27秒で5秒目標を満たさないため、性能上の制約は残る。

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

## 当初の追加予定ファイル

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

## 当初の変更予定ファイル

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/pepper_person_id_poc/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/themes.xml`
- `app/proguard-rules.pro`

モデル本体は初期段階でGitリポジトリへ追加しない。

## 当初の実装手順と現在の範囲

手順1～10と14のうち、顔・話者PoCに必要な部分を実装した。手順11～13の音声認識・統合・会話履歴、手順5のQiSDK、手順15の本番相当評価は今回の範囲外または未実施である。

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

- Gradleラッパーは修正済みで、JVMテスト、debug APK、androidTest APKのビルドに成功している
- AGP 9.2.1、Kotlin 2.2.10とQiSDK 1.8.5の互換性は未確認
- OpenCV 5.0.0とsherpa-onnx 1.13.4のAPI 23・`armeabi-v7a`動作は実機確認済み
- 利用する公式配布物のURL、SHA-256、コード／ランタイムのライセンスは`model-provenance.md`へ記録済み。話者モデル重みの個別ライセンスと商用利用可否は未確認
- カメラ、OpenCV、VAD、話者モデル、QiSDKの同時利用でRAMとCPUが不足する可能性がある
- `SpeechRecognizer`と`AudioRecord`がマイクを競合する可能性がある
- Swapによりクラッシュせずに性能目標だけを大幅に超過する可能性がある
- 顔・声特徴量は生体情報である。PoCでは特徴量をログへ出さず、`android:allowBackup="false"`、確認付き全削除、匿名特徴量のセッション終了時破棄を実装した。保存時暗号化は本番化時の課題とする
- OpenCV 5.0.0の`FaceDetectorYN`と`FaceRecognizerSF` Java wrapperは公開release APIを持たず、画面離脱時に参照とカメラ解析を破棄した後のネイティブ解放はwrapperのfinalizerに依存する。Matは明示解放する
- 話者モデルは明示解放するが、ERes2Net推論中の画面離脱では同期解放が推論完了を待ち、UI復帰が遅れる可能性がある

## 未確定事項

- 本番利用時の各モデル・評価データの利用条件の最終確認
- 顔・声の登録サンプル数
- 声登録1サンプルの長さ
- 本番用の`faceThreshold`、`speakerThreshold`（PoC初期値はともに0.60）
- 最低発話時間、VAD閾値、許容ノイズ
- 顔観測の保持時間と追跡終了条件
- 特徴量の暗号化方式（PoCではモデル別に内部ストレージへ永続化し、全削除UIとバックアップ無効化を実装）
- リモート推論先、認証、通信、データ保存条件
- 本番運用時のベンチマークファイル回収方法（PoCではアプリ内部JSON Linesと画面表示）

## 実装開始ゲート

2026-07-12に調査結果の確認を受け、実装を開始した。大規模ライブラリとモデルは機能単位で配布元、ライセンス、ABI、APIレベルを確認してから追加し、モデル本体はGitへコミットしない。

## 実装後のPepper実測

2026-07-12時点の`LPT_200AR`実測。前面カメラに顔がいない状態で確認した。

現行APKの回帰確認として、2026-07-12に端末`192.168.10.110:5555`で次を実行し、instrumentation 5件すべてが成功した（`OK (5 tests)`、137.627秒）。

```powershell
adb -s 192.168.10.110:5555 shell am instrument -w -r com.example.pepper_person_id_poc.test/androidx.test.runner.AndroidJUnitRunner
```

内訳はアプリコンテキスト1件、SFaceと0095の顔ベンチ2件、CAM++/ERes2Netの英語・中国語話者ベンチ2件である。顔ベンチは両モデルについて閾値0.60で同一人物受理・別人物拒否・スコア順序を、話者ベンチはERes2Netについて両データセットで同一話者受理・別話者拒否・スコア順序を自動判定する。CAM++は既知のスコア逆転を比較結果として残すため、分離成功をテスト条件にしない。

| 項目 | 実測値 |
|---|---|
| CameraX前面プレビュー | 640x480、動作 |
| OpenCV | 5.0.0 `armeabi-v7a`、ロード成功 |
| YuNet | `face_detection_yunet_2026may.onnx`、ロード・推論成功 |
| YuNet顔なし推論 | 約490～631ms |
| SFace | `face_recognition_sface_2021dec.onnx`、初期化成功 |
| SFace初回初期化 | 2,844ms |
| AudioRecord | 16,000Hz、mono、PCM 16-bitで初期化・連続読取成功 |
| AudioRecord最小バッファ | 2,048 bytes |
| 発話区間抽出 | 暫定エネルギーVADで生成成功、短音声を`INSUFFICIENT_AUDIO`判定 |
| アプリTOTAL PSS | 約140,460kB |
| Native Heap PSS | 約83,590kB |
| TOTAL SWAP PSS | 約16,392kB |
| 画面離脱後のカメラ | `Active Camera Clients: []` |
| 画面離脱後のマイク | AudioFlingerのactive track 0件 |
| クラッシュ・OOM | 発生なし |

YuNetの顔検出処理は2秒以内の更新目標を満たす見込みがある。CPU占有を抑えるため、解析開始間隔は1,000msとする。カメラに立った人物による登録操作と長時間再登場試験は追加確認する。

### 顔識別Lombard GRIDスモーク試験

2026-07-12、front-view動画の1.0秒位置から抽出したフレームをPepper上でYuNet検出し、SFace特徴量を生成した。登録相当入力は`s22` plain、同一人物入力は`s22` Lombard、別人物入力は`s16` plainである。

| モデル | 初期化 | 顔検出（3枚） | 特徴量生成（3枚） | 同一人物スコア | 別人物スコア | Native heap |
|---|---:|---:|---:|---:|---:|---:|
| SFace | 1,945 ms | 608 / 599 / 596 ms | 911 / 745 / 756 ms | 0.954 | 0.335 | 153,222,336 bytes |

3枚すべてで顔を1件検出し、128次元の有限な特徴量を生成した。現在の顔閾値0.60では同一人物を受理し、別人物を`Unknown`へ分離できる。3枚だけのスモーク試験であり、FAR、FRR、人物間取り違え率や本番閾値を確定する根拠にはしない。

第二候補`face-reidentification-retail-0095`の公式FP32 IR（XML+BIN）はAndroid AARにOpenVINO pluginがなく直接ロードできなかった。このためopset 13 ONNXへ再現可能に変換し、元IRとの数値同等性を確認してからOpenCV 5.0.0 DNNへ統合した。

| モデル | 初期化 | 顔検出（3枚） | 特徴量生成（3枚） | 同一人物スコア | 別人物スコア | Native heap |
|---|---:|---:|---:|---:|---:|---:|
| face-reidentification-retail-0095 ONNX | 592 ms | 653 / 590 / 595 ms | 514 / 503 / 486 ms | 0.955 | 0.292 | 60,184,776 bytes |

5点ランドマークを公式の128x128基準位置へsimilarity変換し、256次元特徴量を生成した。3画像スモーク条件では同一人物スコアが別人物を上回り、閾値0.60で分離した。SFaceと同様に本番精度や閾値を確定する規模の評価ではない。

## 未登録リアルタイム識別

事前登録を行わない場合、表示名や永続`personId`は確定できない。このためリアルタイム顔識別画面では、登録済み1対N照合で`Unknown`となった顔特徴量をセッション内でクラスタリングし、`anonymous-001`形式の一時IDを同じ画面に表示する。

- `trackId`: 連続するカメラフレーム内の短期追跡ID
- `anonymousId`: 顔が画面から消えて再登場した場合も特徴量が閾値以上なら再利用するセッション内ID
- 登録人数・一時人物数に固定上限を設けない
- 一時特徴量は永続保存せず、リセット、画面終了、アプリ終了で破棄する
- 一時IDを実名や永続的な本人情報として扱わない

クラスタリング、別trackIdでの再識別、新規クラスタ、リセット、および登録人物が0人でもリアルタイム処理を継続する経路はJVM単体テスト済み。エミュレータではカメラ、YuNet、SFace初期化、顔なし状態、画面離脱後のカメラ解放を確認した。Lombard GRIDの同一人物・別人物フレーム分離は確認したが、カメラに立った人物の退場後再登場を含む匿名追跡精度は未測定である。

## 話者識別のPepper実測

2026-07-12、`LPT_200AR`、API 23、`armeabi-v7a`、CPUスレッド1で測定した。数値は初回ロードを含む単発スモーク試験であり、全コーパス精度ではない。

### 端末・録音経路

| 項目 | 結果 |
|---|---|
| PCM | 16,000 Hz、mono、PCM 16-bit |
| AudioRecord最小バッファ | 2,048 bytes |
| VAD | Silero VADロード・連続処理成功 |
| CAM++初回UI初期化 | 9,214 ms |
| ERes2Net初回UI初期化 | 3,884 ms |
| CAM++特徴量次元 | 512 |
| ERes2Net特徴量次元 | 192 |
| 画面離脱後 | AudioFlinger active track 0件 |

### 英語Lombard GRIDスモーク試験

登録相当入力を`s22` plain、同一話者入力を`s22` Lombard、別話者入力を`s16` plainとした。

| モデル | 初期化 | 登録入力 | 同一話者入力 | 別話者入力 | 同一スコア | 別話者スコア | Native heap |
|---|---:|---:|---:|---:|---:|---:|---:|
| CAM++ | 6,776 ms | 2,508 ms | 2,790 ms | 2,499 ms | 0.850 | 0.969 | 57,702,024 bytes |
| ERes2Net | 3,387 ms | 7,187 ms | 7,955 ms | 7,197 ms | 0.765 | 0.115 | 50,819,456 bytes |

閾値0.60では、CAM++は同一話者も別話者も登録人物として受理し、別話者をより高く評価した。ERes2Netは同一話者を受理し、別話者を`Unknown speaker`へ分離できる。

### sherpa-onnx配布中国語サンプル試験

| モデル | 初期化 | 登録入力 | 同一話者入力 | 別話者入力 | 同一スコア | 別話者スコア | Native heap |
|---|---:|---:|---:|---:|---:|---:|---:|
| CAM++ | 9,168 ms | 3,041 ms | 6,416 ms | 9,332 ms | 0.576 | 0.886 | 57,704,528 bytes |
| ERes2Net | 3,846 ms | 7,727 ms | 18,501 ms | 27,397 ms | 0.794 | 0.285 | 50,765,520 bytes |

閾値0.60では、CAM++は同一話者を`Unknown`とし、別話者を登録人物として受理する逆転が発生した。ERes2Netは同一話者を受理し、別話者を`Unknown speaker`へ分離できた。

### 暫定判断

- CAM++とERes2Netはどちらも選択・初期化・特徴量生成可能な実装を維持する。
- この限定スモーク条件ではERes2Netを暫定推奨とする。
- CAM++を第一候補として本採用しない。閾値調整だけではスコア逆転を解消できない。
- ERes2NetはCAM++より推論時間が長く、発話終了後5秒目標を超えるケースがある。
- 日本語話者、Pepperマイク、複数登録サンプルによる追加評価なしに本番閾値を確定しない。
- 匿名話者IDは同じ特徴量比較を使うため、モデルの誤分離・誤結合特性をそのまま受ける。

## Kotlin/JVM Windows話者ベンチマーク

2026-07-13に、`speaker-core`と`speaker-benchmark`を追加した。新規の保守対象コードはKotlinへ統一し、依存管理はGradle Wrapper、Gradle Kotlin DSL、Version Catalogだけを使用する。Windows推論はONNX Runtime Java 1.20.0 CPU、Android推論は既存のsherpa-onnx 1.13.4 Kotlin/JNI APIを使用する。1.21.0以降のONNX Runtime Java DLLはこのWindows端末で初期化に失敗したため、実動確認できた1.20.0へ固定した。

共有する後処理は次のとおりである。

```text
各登録EmbeddingをL2正規化
→ 人物ごとに平均
→ セントロイドを再L2正規化
→ 全人物とのcosine
→ Top-1 thresholdとTop-1/Top-2 margin
→ 人物IDまたはUnknown
```

Android側も、登録サンプルの最大スコア方式から上記の共有`SpeakerScorer`へ移行した。thresholdとmarginは設定へ保存できる。既定marginは、複数人物の日本語developmentデータで決定していないため`0.0`とする。

実行コマンド:

```powershell
.\scripts\windows\bootstrap.ps1
.\gradlew.bat :speaker-benchmark:run --args="--config config/speaker-benchmark.json"
.\scripts\windows\verify-speaker-parity.ps1 -SkipBenchmarkRun
```

### Windows 5モデル配線スモーク結果

sherpa-onnx公式の中国語3ファイルを、登録1話者、同一話者テスト1本、Unknown 1話者としてVADなしで入力した。以下は1回計測の配線スモーク値であり、日本語精度評価ではない。

| モデル | 次元 | 同一話者スコア | Unknownスコア | p50 | p95 | 閾値0.60での結果 |
|---|---:|---:|---:|---:|---:|---|
| CAM++英語 | 512 | 0.576 | 0.886 | 103.5 ms | 110.8 ms | 同一拒否・Unknown誤受入 |
| CAM++中国語・英語 | 192 | 0.809 | 0.161 | 90.0 ms | 108.7 ms | 分離 |
| ERes2Net英語 | 192 | 0.794 | 0.284 | 225.7 ms | 334.8 ms | 分離 |
| SpeakerNet-M | 256 | 0.723 | 0.403 | 63.7 ms | 72.3 ms | 分離 |
| TitaNet-S | 192 | 0.785 | 0.177 | 67.0 ms | 98.4 ms | 分離 |

CAM++英語版とERes2Netのスコアは、同じWAVをsherpa-onnx 1.13.4で処理したAndroid既知値との差が`0.002`以内であることを自動検証した。したがって、Kotlinで再現したモデル別FbankとONNX Runtime JavaのWindows経路は、既存Android経路の原因切り分けに利用できる。

ただし、この中国語配線スモークは登録1名・Unknown 1名で、日本語音声、4名以上の登録話者、4名以上のUnknown話者、development分割、2秒／3秒／5秒の固定比較を満たさない。このスモーク単体をFAR、FRR、EER、Top-1、threshold／marginの確定根拠にはしない。ここで確定できるのは次だけである。

- CAM++英語版の失敗はWindowsでも再現し、Android統合だけが原因ではない。
- 他4モデルはこの限定入力で配線と分離を確認した。
- JVSで候補は絞るが、Pepper実機収録とライセンス監査前に本採用モデルは決定しない。
- 詳細手順、監査、来歴、parity形式は`docs/poc/`を正本とする。

### JVS日本語5モデル本比較

上記の中国語3ファイルは配線スモークである。その後、JVS `parallel100`から計12話者・168 WAVの日本語本比較を実施した。enrollment 24本（4話者）、development 48本（登録4話者 24本＋別のUnknown 4話者 24本）、test 48本、final unknown 48本（別の4話者）である。2秒・3秒・5秒は各56本、splitを跨ぐ同一原音は0件、VADは無効である。threshold/marginはdevelopment 48本だけで選択し、test/unknownには固定した。

| モデル | Top-1 | FAR | FRR | EER | 2秒 | 3秒 | 5秒 | 暫定評価 |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| CAM++英語 | 0.9375 | 0.0833 | 0.3750 | 0.3333 | 0.9063 | 0.6250 | 0.7813 | FAR基準外 |
| CAM++中国語・英語 | 1.0000 | 0 | 0.1458 | 0.0179 | 0.7813 | 1.0000 | 1.0000 | 上位候補 |
| ERes2Net英語 | 1.0000 | 0 | 0.1458 | 0.0208 | 0.7813 | 1.0000 | 1.0000 | 上位候補 |
| SpeakerNet-M | 1.0000 | 0.3125 | 0.0417 | 0.0417 | 0.8438 | 0.8750 | 0.7500 | FAR基準外 |
| TitaNet-S | 1.0000 | 0.0625 | 0.0417 | 0.0417 | 0.9375 | 0.9688 | 0.9375 | FAR 5%をわずかに超過 |

暫定受入基準FAR `<= 5%`を満たす上位候補はCAM++ Chinese-EnglishとERes2Netである。JVSはstudio収録でPepperマイクではなく、WindowsのレイテンシもPepper ARMv7性能に読み替えない。数値の正本は[`results/jvs-speaker-benchmark/summary.md`](../results/jvs-speaker-benchmark/summary.md)である。

AndroidはAPI 28 x86でinstrumentation 2テストに成功し、3モデル×2データセット×2クエリの12個のJSONを回収した。API 23 x86はAARが`__write_chk`を要求してロードに失敗する。API 28 x86実測はPepper API 23 / ARMv7のparityや性能を代替しない。RyuseiNetは今回の5モデル比較の外とし、追加学習なし、未実装・未比較である。

5モデルのONNX重みは適用ライセンス、商用条件、根拠URLが未確認のため、releaseゲートを維持する。ゲートでは目録とAndroid列挙モデル、同梱assetのサイズ・SHA-256も照合する。
