# Research: Pepper匿名人物識別の完全実装

## Decision 1: 現行コードを段階拡張する

- **Decision**: 既存のCameraX、OpenCV、sherpa-onnx、匿名クラスタ、Compose UIを置換せず、共通契約を先に追加して段階移行する。
- **Rationale**: Android端末で顔検出・SFace特徴抽出・話者固定WAV試験が既に動作し、Pepper向けARMv7資産も存在する。
- **Alternatives considered**: 全面再実装は回帰範囲とPepperリスクが大きいため不採用。

## Decision 2: 匿名クラスタは完全インメモリ化する

- **Decision**: production経路の`FileAnonymousFaceClusterRepository`と`FileAnonymousSpeakerClusterRepository`をインメモリRepositoryへ置換する。
- **Rationale**: 起動時削除があっても現在は平文特徴量を`filesDir/biometric`へ書くため、セッション限定・永続化禁止を満たさない。
- **Alternatives considered**: 暗号化永続化はユーザーが明示的に不要としたため不採用。

## Decision 3: モデル空間・資産・ランタイムを分ける

- **Decision**: `modelSpaceId`、`artifactId`、`runtimeId`を独立した必須IDとする。
- **Rationale**: 変換物と元モデルが同じ特徴量空間かは同等性試験でしか判断できず、実行基盤は資産と別に追跡する必要がある。
- **Alternatives considered**: 現行の表示名兼`modelId`は名称変更とruntime差を区別できないため不採用。

## Decision 4: ML Kit confidenceは未測定とする

- **Decision**: 顔検出confidenceをnullableにし、ML KitではN/Aとする。
- **Rationale**: ML Kitの現行経路はconfidenceを提供せず、`1.0`固定は測定値の捏造になる。
- **Alternatives considered**: `1.0`継続、`0.0`代入はいずれも品質判定を歪めるため不採用。

## Decision 5: 16 kHz以外へフォールバックしない

- **Decision**: AudioRecordは16 kHz mono PCM16だけを初期化し、失敗時は構造化エラーで停止する。
- **Rationale**: 44.1 kHzとEnergy VADへの暗黙切替はモデル入力と比較条件を変える。
- **Alternatives considered**: アプリ内resamplingは独立した検証済み機能として将来追加可能だが、本Featureでは入力を固定する。

## Decision 6: 重複音声は分離生成せずfail-closedで扱う

- **Decision**: 話者活動推定で単独話者区間だけを抽出し、完全重複・判定不能・3人以上ではクラスタ更新を行わない。
- **Rationale**: 旧Pepperで音声分離まで行うと計算量と誤差が大きく、目的はクラスタ汚染防止である。
- **Alternatives considered**: 混合音声を単一話者として保存する案は安全要件に反する。波形分離は本Featureの受入に不要。

## Decision 7: pyannote segmentationは資産と実行経路をゲートする

- **Decision**: pyannote segmentation 3.0相当の話者活動推定資産を台帳化し、ONNX変換物、入力仕様、SHA-256、MITライセンス証拠、ARM64/API23 ARMv7実行結果が揃うまで`BLOCKED`とする。
- **Rationale**: 公式プロジェクトは重複発話検出を含むspeaker diarization用途を示し、モデルのライセンス資料はMITを示すが、Android API 23/ARMv7 ONNX経路は実機で未確認である。
- **Alternatives considered**: Python/クラウドpyannote実行は端末内・オフライン制約に反する。未確認runtimeへの黙示切替も禁止する。

## Decision 8: LiteRTは固定変換環境と実機ゲートで採否を決める

- **Decision**: YuNet FP32とSFace FP32を最初の変換対象とし、変換環境、出力hash、tensor metadata、比較結果を固定する。LiteRT Android artifactのAPI23/ARMv7可否は依存解決、APK監査、ARM64実行、Pepper実行の順で確定する。
- **Rationale**: LiteRTはGoogleの現行on-device runtimeだが、Pepper固有のAPI 23/ARMv7成立を文書情報だけで推定できない。
- **Alternatives considered**: 変換成功だけで採用する案、Play services runtimeはオフライン・旧端末制約に合わないため不採用。

## Decision 9: 同等性が合格するまでモデル空間を共有しない

- **Decision**: OpenCV/ONNXとLiteRT、およびOpenCV/ORT/ncnn/MNNの前処理・出力差を固定入力で比較し、基準合格後だけ同じ`modelSpaceId`を付与する。
- **Rationale**: 現行OpenCVは`alignCrop/feature`、他runtimeは独自前処理であり、数値同等性は未証明である。
- **Alternatives considered**: 同じ元ファイル名やアーキテクチャ名だけで共有する案はクラスタ誤照合を生む。

## Decision 10: build flavorで評価と配布を分離する

- **Decision**: `benchmark`と`candidate`をproduct flavorとして追加し、debug/releaseと直交させる。
- **Rationale**: 全runtime・詳細ログを含む比較用APKと、採用候補だけのPepper受入用APKは目的とリスクが異なる。
- **Alternatives considered**: 実行時フラグだけではAPK内のモデル、AAR、`.so`、ライセンス対象を除去できない。

## Decision 11: Android端末合格後にPepperへ進む

- **Decision**: 各シナリオをARM64 Android端末で先行し、同じscenario ID、設定、証跡項目でPepperを検証する。
- **Rationale**: 開発速度を上げつつ、Pepper実機受入を代替証拠で済ませない。
- **Alternatives considered**: Android端末だけの受入、またはPepperだけでの初回デバッグはいずれも不採用。

## Decision 12: 計画から削除した機能

- **Decision**: 観測履歴、ID統合・分離、顔IDと話者IDの関連付け、特徴量の永続・暗号化保存を実装しない。
- **Rationale**: ユーザーが不要と明示し、Constitution 3.0.0の匿名・モダリティ分離・セッション限定方針と一致する。
- **Alternatives considered**: 外部改訂計画の該当項目を維持する案はユーザーの最新指示に反する。

## User Story 3 supported matrix

設定schema v2は検出モデル、検出runtime、特徴量モデル、特徴量runtimeを別キーで保存する。
選択可否は`config/models.json`の正確なartifact/runtime/ABI/APIレコードとAPK内の資産名を
照合して決め、`VERIFIED`または`BUILDABLE`だけを選択可能にする。

| Pipeline | Artifact | Runtime | Catalog status / build scope |
|---|---|---|---|
| Detection | `mlkit-face-detection-16.1.7-bundled` | `mlkit-face-16.1.7-bundled` | BUILDABLE: ARM64, ARMv7 |
| Detection | `yunet-2026may-onnx-fp32` | `opencv-5.0.0-android-cpu` | BUILDABLE: ARM64, ARMv7 |
| Detection | `yunet-2026may-onnx-fp32` | `onnxruntime-android-1.20.0-cpu` | BUILDABLE: ARM64, ARMv7; packaged AAR ABI confirmed, Pepper parity pending |
| Detection | `yunet-2026may-ncnn-fp32-320` | `ncnn-20260526-android-cpu` | BUILDABLE: ARM64, ARMv7; 12-head host parity max abs <= 3.34e-6 |
| Detection | `yunet-2026may-mnn-fp32-320` | `mnn-3.5.0-android-cpu` | BUILDABLE: ARM64, ARMv7; 12-head host parity max abs <= 3.6e-6 |
| Detection | `yunet-2026may-litert-fp32-320` | `litert-2.1.6-android-cpu` | BUILDABLE: ARM64, ARMv7; converted-output adapter parity complete |
| Embedding | `sface-2021dec-onnx-fp32` | `opencv-5.0.0-android-cpu` | BUILDABLE: ARM64, ARMv7 |
| Embedding | `sface-2021dec-onnx-int8` | `opencv-5.0.0-android-cpu` | BUILDABLE: ARM64, ARMv7 |
| Embedding | `face-0095-onnx-fp32` | `opencv-5.0.0-android-cpu` | BUILDABLE: ARM64, ARMv7 |
| Embedding | `sface-2021dec-ncnn-fp32` | `ncnn-20260526-android-cpu` | BUILDABLE: ARM64, ARMv7; device inference pending |
| Embedding | `sface-2021dec-mnn-fp32` | `mnn-3.5.0-android-cpu` | BUILDABLE: ARM64, ARMv7; device inference pending |
| Embedding | `face-0095-ncnn-fp32` | `ncnn-20260526-android-cpu` | BUILDABLE: ARM64, ARMv7; device inference pending |
| Embedding | `face-0095-mnn-fp32` | `mnn-3.5.0-android-cpu` | BUILDABLE: ARM64, ARMv7; device inference pending |
| Embedding | `sface-2021dec-litert-fp32` | `litert-2.1.6-android-cpu` | BUILDABLE: ARM64, ARMv7; device inference pending |
| Embedding | `face-0095-litert-fp32` | `litert-2.1.6-android-cpu` | BUILDABLE: ARM64, ARMv7; fixed-input cosine 0.99999994 |
| Embedding | `sface-2021dec-onnx-fp32` | `onnxruntime-android-1.20.0-cpu` | BUILDABLE: ARM64, ARMv7; packaged AAR ABI confirmed, Pepper smoke pending |
| Embedding | `face-0095-onnx-fp32` | `onnxruntime-android-1.20.0-cpu` | BUILDABLE: ARM64, ARMv7; packaged AAR ABI confirmed, Pepper smoke pending |

上表にない組合せ、対象ABIのレコードがない組合せ、またはAPK内に資産がない組合せは
`UNSUPPORTED`または`BLOCKED`として保存・実行を禁止する。factoryも同じ正確な組合せだけを
生成し、ロード失敗時に別モデルや別runtimeへ切り替えない。

Host evidence (2026-07-26):

- settings migration, catalog selection, defaults, exact ID tests: `:app:testDebugUnitTest` passed
- unsupported/missing-asset UI and no-fallback instrumentation: `:app:compileDebugAndroidTestKotlin` passed
- instrumentation execution: Android development device is disconnected; device pass remains pending
