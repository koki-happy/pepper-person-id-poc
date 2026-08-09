# PoC 現在地

更新: 2026-08-09

## 結論

Windows x64における5モデルのJVS日本語比較は過去記録として完了しています。現在のAPK設定・同梱対象はCAM++ Chinese-EnglishとWeSpeaker ResNet34-LMの2モデルです。いずれも重みライセンスは確認済みですが、Pepperマイクの反復評価と実機受入は未完了です。

## 実装・検証済み

| 領域 | 現在の証拠 |
|---|---|
| 共通話者ロジック | L2正規化、centroid、cosine、threshold、Top-2 margin、FAR/FRR/EERを[`speaker-core`](../../speaker-core/)に共通化 |
| Windows推論 | Kotlin/JVM + ONNX Runtime Javaで過去の5モデルを実行。結果は[`JVS summary`](../../results/jvs-speaker-benchmark/summary.md) |
| JVS分割 | 168 WAV、登録4話者、developmentの別Unknown 4話者、final Unknown 4話者、2/3/5秒各56本、source重複0 |
| threshold選択 | development 48本だけでモデル別threshold/marginを選択。[`thresholds.json`](../../results/jvs-speaker-benchmark/thresholds.json) |
| Android経路 | 録音、VAD、embedding、centroid照合、Unknown判定、UI、JSONL記録を接続。現行の話者モデルは2モデル |
| API 28 x86 | instrumentation 2テスト成功、3モデルの12 JSON回収済み。中国語WAV 6件はWindowsとの自動parityにも合格。[`android-speaker-parity`](../../results/android-speaker-parity/) |
| ARMv7 AAR | sherpa-onnx 1.13.4 static-link AARのSHA-256、ARMv7 entry、metadataを自動検証 |

## JVS結果の要約（過去比較記録）

| モデル | FAR | FRR | EER | 暫定判断 |
|---|---:|---:|---:|---|
| CAM++ English | 0.0833 | 0.3750 | 0.3333 | FAR基準外 |
| CAM++ Chinese-English | 0 | 0.1458 | 0.0179 | 上位候補 |
| ERes2Net English | 0 | 0.1458 | 0.0208 | 上位候補 |
| SpeakerNet-M | 0.3125 | 0.0417 | 0.0417 | FAR基準外 |
| TitaNet-S | 0.0625 | 0.0417 | 0.0417 | FAR 5%を超過 |

## 未完了のゲート

- JVSはstudio収録であり、Pepper動作音、距離、別セッション、Pepperマイクを含まない。
- API 23 x86はAARの`__write_chk`依存でロード失敗。API 28 x86成功はPepper API 23 / ARMv7の代替ではない。
- CAM++ Chinese-EnglishとWeSpeaker ResNet34-LMは`releaseModelIds`としてライセンスゲートを通過する。データ条件、NOTICE/SBOM、Pepper実機受入は未完了。
- Androidの設定選択肢は、APKに同梱するCAM++ Chinese-EnglishとWeSpeaker ResNet34-LMの2モデル。Pepper実機比較は未実施。
- ASR、顔・声統合、会話履歴は今回の話者比較範囲外。
- RyuseiNetは追加学習なし、未実装・未比較。今回の5モデル本比較には含めない。

実行手順は[Windows セットアップ](./windows-setup.md)、数値は[Windows 話者モデル実測](./windows-speaker-benchmark.md)、採用判断は[最終推奨](./final-recommendation.md)を参照してください。
