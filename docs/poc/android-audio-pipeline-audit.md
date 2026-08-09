# Android 音声パイプライン監査

## 結論

録音、VAD、発話区間化、話者embedding、登録、centroid照合、threshold/Top-2 margin、Unknown判定、UI、Logcat出力までのコード経路は接続済みです。固定入力のスモークテストとADB受入は別の検証手順として扱い、Pepper API 23 / ARMv7のライブマイク評価を最終ゲートにします。

## コード経路

| 段階 | 実装 |
|---|---|
| 録音 | [`AndroidPcmAudioRecorder.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/infrastructure/audio/AndroidPcmAudioRecorder.kt) |
| VAD | [`SherpaSileroVoiceActivityDetector.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/infrastructure/audio/SherpaSileroVoiceActivityDetector.kt) |
| 区間化 | [`PcmUtteranceSegmenter.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/domain/audio/PcmUtteranceSegmenter.kt) |
| embedding | [`SherpaOnnxSpeakerEmbeddingEngine.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/infrastructure/speaker/SherpaOnnxSpeakerEmbeddingEngine.kt) |
| 登録・判定 | [`SpeakerIdentityCoordinator.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/application/speaker/SpeakerIdentityCoordinator.kt) |
| centroid/cosine/Unknown | [`SpeakerIdentifier.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/domain/speaker/SpeakerIdentifier.kt)、[`SpeakerScorer.kt`](../../speaker-core/src/main/kotlin/com/example/pepper_person_id_poc/speakercore/SpeakerScorer.kt) |
| UI | [`CameraPreviewScreen.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt) |
| instrumentation | [`SpeakerActivityPipelineTest.kt`](../../app/src/androidTest/java/com/example/pepper_person_id_poc/SpeakerActivityPipelineTest.kt)、[`StrictSpeakerAudioPipelineTest.kt`](../../app/src/androidTest/java/com/example/pepper_person_id_poc/StrictSpeakerAudioPipelineTest.kt) |

## AAR / ABI / API監査

- AAR: `sherpa-onnx-static-link-onnxruntime-1.13.4.aar`
- SHA-256: `DC5AC19A28DEE3BFFC5E5A5D50CB6AFA977703FC4A7EE535A308506990FDD295`
- `armeabi-v7a`: `libsherpa-onnx-jni.so` 1個にONNX Runtimeを静的リンク。別の`libonnxruntime.so`は意図的にない。
- ARMv7: Pepper API 23でJNIロード、CAM++ Chinese-English、Silero VADの初期化実績がある。
- x86 API 23: AAR内`libonnxruntime.so`がAPI 23にない`__write_chk`を要求し、ロード失敗。
- x86 API 28: 固定入力のランタイムスモークと音声パイプラインのinstrumentationを実行できる。これはPepper API 23 / ARMv7の代替ではない。

自動検査は[`verify-android-speaker-runtime.ps1`](../../scripts/windows/verify-android-speaker-runtime.ps1)で実行します。API 28 x86成功はAndroidのネイティブ推論経路を確認する証拠ですが、32-bit ARMv7、API 23、Pepperのマイク・メモリ・CPU性能へは外挿しません。

## 残る穴

- recorderは16 kHz失敗時に44.1 kHzを選べますが、embedding engineは16 kHz以外を拒否します。現行44.1 kHzは有効な話者識別フォールバックではありません。
- instrumentationはasset WAVをembedding engineへ直接渡し、`AudioRecord`、ライブVAD、segmenterを通りません。
- 現在のAndroidモデルはCAM++ Chinese-EnglishとWeSpeaker ResNet34-LMの2個です。WeSpeakerを含む現行2モデルのPepper実機比較は未実施です。
- Pepperマイクで登録4名以上、Unknown 4名以上、別セッション、2/3/5秒を反復した受入結果は未取得です。
- ASRは話者識別経路にありません。

Pepper実機のライブマイク受入、性能、長時間安定性はADB/Logcatの受入手順で別途確認します。
