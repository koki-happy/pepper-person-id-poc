# Android 音声パイプライン監査

## 結論

録音、VAD、発話区間化、話者embedding、登録、centroid照合、threshold/Top-2 margin、Unknown判定、UI、JSONLまでのコード経路は接続済みです。API 28 x86ではinstrumentation 2テストが成功し、3モデル×2データセット×2クエリの12個のparity JSONを回収しました。ただし、この結果はPepper API 23 / ARMv7のライブマイク評価を代替しません。

## コード経路

| 段階 | 実装 |
|---|---|
| 録音 | [`AndroidPcmAudioRecorder.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/infrastructure/audio/AndroidPcmAudioRecorder.kt) |
| VAD | [`SherpaSileroVoiceActivityDetector.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/infrastructure/audio/SherpaSileroVoiceActivityDetector.kt) |
| 区間化 | [`PcmUtteranceSegmenter.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/domain/audio/PcmUtteranceSegmenter.kt) |
| embedding | [`SherpaOnnxSpeakerEmbeddingEngine.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/infrastructure/speaker/SherpaOnnxSpeakerEmbeddingEngine.kt) |
| 登録・判定 | [`SpeakerIdentityCoordinator.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/application/speaker/SpeakerIdentityCoordinator.kt) |
| centroid/cosine/Unknown | [`SpeakerIdentifier.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/domain/speaker/SpeakerIdentifier.kt)、[`SpeakerScorer.kt`](../../speaker-core/src/main/kotlin/com/example/pepper_person_id_poc/speakercore/SpeakerScorer.kt) |
| UI | [`AudioRecordingScreen.kt`](../../app/src/main/java/com/example/pepper_person_id_poc/ui/screen/AudioRecordingScreen.kt) |
| instrumentation | [`SpeakerModelBenchmarkTest.kt`](../../app/src/androidTest/java/com/example/pepper_person_id_poc/SpeakerModelBenchmarkTest.kt) |

## AAR / ABI / API監査

- AAR: `sherpa-onnx-static-link-onnxruntime-1.13.4.aar`
- SHA-256: `DC5AC19A28DEE3BFFC5E5A5D50CB6AFA977703FC4A7EE535A308506990FDD295`
- `armeabi-v7a`: `libsherpa-onnx-jni.so` 1個にONNX Runtimeを静的リンク。別の`libonnxruntime.so`は意図的にない。
- ARMv7: Pepper API 23でJNIロード、CAM++、ERes2Net、Silero VADの初期化実績がある。
- x86 API 23: AAR内`libonnxruntime.so`がAPI 23にない`__write_chk`を要求し、ロード失敗。
- x86 API 28: instrumentation 2テスト成功、Android統合済み3モデルの12 JSON回収済み。中国語WAV 6件はWindowsとの自動parityにも合格。

自動検査は[`verify-android-speaker-runtime.ps1`](../../scripts/windows/verify-android-speaker-runtime.ps1)で実行します。API 28 x86成功はAndroidのネイティブ推論経路を確認する証拠ですが、32-bit ARMv7、API 23、Pepperのマイク・メモリ・CPU性能へは外挿しません。

## 残る穴

- recorderは16 kHz失敗時に44.1 kHzを選べますが、embedding engineは16 kHz以外を拒否します。現行44.1 kHzは有効な話者識別フォールバックではありません。
- instrumentationはasset WAVをembedding engineへ直接渡し、`AudioRecord`、ライブVAD、segmenterを通りません。
- AndroidモデルはCAM++ English、CAM++ Chinese-English、ERes2Netの3個です。JVS上位2候補をどちらもPepper実機で比較できる状態です。
- Pepperマイクで登録4名以上、Unknown 4名以上、別セッション、2/3/5秒を反復した受入結果は未取得です。
- ASRは話者識別経路にありません。

RyuseiNetは今回の5モデル本比較の外であり、Android統合も追加学習も行っていません。
