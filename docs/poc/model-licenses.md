# 話者モデルとランタイムのライセンス監査

監査日: 2026-07-13（JST）

> この文書は技術調査記録であり、法的助言ではありません。コードのライセンス、実行ランタイムの再配布条件、モデル重みのライセンス、学習データ由来条件、商用利用条件は別々に確認します。

## 現時点の結論

CAM++ 2種とERes2Netは、関連する学習コードが`Apache-2.0`でも、現在の配布元に重みへ適用される明示的な許諾証拠がないため未確認です。SpeakerNet-MとTitaNet-Sは、NVIDIA NGCの公式モデルカードがNeMo Toolkitの`Apache-2.0`を適用すると明示しているため、重みの利用許諾を確認済みとします。学習データ（VoxCeleb）の利用条件・表示要件は別途確認します。

したがって、SpeakerNet-MとTitaNet-Sは内部benchmark APKへ同梱できますが、CAM++ 2種とERes2Netを含むcandidate配布は引き続き許諾ゲートで停止します。関連コードがApache-2.0であることを、未確認の重みの許諾根拠へ流用してはいけません。

## 5モデルの重み

正本は [`config/models.json`](../../config/models.json) です。

| モデル重み | SHA-256 | 関連コード | weight license | commercial use |
|---|---|---|---|---|
| `3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx` | `357a834f702b80161e5b981182c038e18553c1f2ca752ed6cec2052365d4129b` | 3D-Speaker、Apache-2.0 | **未確認** | **未確認** |
| `3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx` | `aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2` | 3D-Speaker、Apache-2.0 | **未確認** | **未確認** |
| `3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx` | `c59158379255ad66e161679cca6af8d52d51e389e3224ab7d7a7baae295c2db5` | 3D-Speaker、Apache-2.0 | **未確認** | **未確認** |
| `nemo_en_speakerverification_speakernet.onnx` | `d204dc8aac0014b8543f05fc8e310510c7022bc65b6452c203ec205ef7a66b23` | NVIDIA NeMo、Apache-2.0 | Apache-2.0（[NGC model card](https://catalog.ngc.nvidia.com/orgs/nvidia/teams/nemo/models/speakerverification_speakernet)） | **許可** |
| `nemo_en_titanet_small.onnx` | `ad4a1802485d8b34c722d2a9d04249662f2ece5d28a7a039063ca22f515a789e` | NVIDIA NeMo、Apache-2.0 | Apache-2.0（[NGC model card](https://catalog.ngc.nvidia.com/orgs/nvidia/teams/nemo/models/titanet_small)） | **許可** |

配布元は [`csukuangfj/speaker-embedding-models`](https://huggingface.co/csukuangfj/speaker-embedding-models) の revision `0743f301363dec56491a490f6d6cbc9d67f9a3bf` です。配布ページがファイルを公開している事実だけでは、利用・改変・再配布・商用利用の許諾になりません。

関連コードの公式ライセンス:

- [3D-Speaker LICENSE](https://github.com/modelscope/3D-Speaker/blob/master/LICENSE)
- [NVIDIA NeMo LICENSE](https://github.com/NVIDIA/NeMo/blob/main/LICENSE)

これらはコードリポジトリのライセンス根拠であり、上表の ONNX 重みへ自動的に適用されると判断していません。

## 推論ランタイム

| 利用箇所 | 成果物 | コードライセンス | 現在の確認範囲 |
|---|---|---|---|
| Windows | `com.microsoft.onnxruntime:onnxruntime:1.20.0` | MIT | [`libs.versions.toml`](../../gradle/libs.versions.toml)で版を固定。公式 [v1.20.0 LICENSE](https://github.com/microsoft/onnxruntime/blob/v1.20.0/LICENSE) を確認 |
| Android | `sherpa-onnx-static-link-onnxruntime-1.13.4.aar` | sherpa-onnx: Apache-2.0、静的リンクされた ONNX Runtime: MIT | [`app/build.gradle.kts`](../../app/build.gradle.kts)でAARを参照。公式 [sherpa-onnx v1.13.4 LICENSE](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/LICENSE) と上記 ONNX Runtime ライセンスを確認 |

ARMv7 AAR は ONNX Runtime を静的リンクしているため、別の `libonnxruntime.so` は不要です。ただし、静的リンクでファイルが見えなくなっても、該当コードのライセンス表示や third-party notices の検討が不要になるわけではありません。

現在のリポジトリには、このAARとモデルを含む配布物向けに確定した第三者ライセンス一覧・NOTICE・SBOM がありません。本番配布前に生成・レビューが必要です。

## 評価データは別監査

モデル重みの許諾と、評価 WAV の利用条件も別です。テストデータはリポジトリ内に保管せず、評価の都度、利用条件を確認できる公開情報・公開データセットから収集します。収集したアーカイブ・派生WAVはGitやAPKへ含めません。

RyuseiNetは今回の5モデル本比較の対象外で、重み取得、追加学習、変換、APK統合を行っていません。そのため本文の5モデルライセンス監査表には含めません。

## 本番利用前のゲート

次をすべて証拠ファイルとして保存するまで、モデル重みの商用利用・顧客配布・APK同梱を承認しません。

1. 各 ONNX ファイルまたはその配布リビジョンへ明示的に適用されるライセンス本文
2. モデル提供者による商用利用と再配布の可否
3. 学習データ由来の追加条件、用途制限、地域制限の有無
4. 必要な著作権表示、NOTICE、モデルカード、帰属表示
5. 使用する実ファイルの SHA-256 と、許諾証拠が指す版の一致
6. APK/AAR/JAR を含む第三者ライセンス一覧と SBOM のレビュー

このゲートを満たしていないモデルだけ、[`config/models.json`](../../config/models.json) の `weightLicense`、`commercialUse`、`licenseEvidence` を `UNVERIFIED` のまま維持します。配布ゲートは目録6件の欠落・重複、Android列挙6件との対応、同梱assetのサイズとSHA-256、未審査ONNXの混入を検査します。
