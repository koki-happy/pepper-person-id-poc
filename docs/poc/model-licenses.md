# 話者モデルとランタイムのライセンス監査

監査日: 2026-08-09（JST）

> この文書は技術調査記録であり、法的助言ではありません。コードのライセンス、実行ランタイムの再配布条件、モデル重みのライセンス、学習データ由来条件、商用利用条件は別々に確認します。

## 現時点の結論

CAM++ Chinese-Englishは、公式ModelScopeモデルページに`Apache-2.0`の記載があり、WeSpeaker ResNet34-LMは公式model cardで`CC-BY-4.0`を示しているため、現在APKへ同梱する2モデルの重みの利用許諾を確認済みとします。学習データ由来の条件・表示要件は別途確認します。

したがって、CAM++ Chinese-EnglishとWeSpeaker ResNet34-LMは`releaseModelIds`として許諾ゲートを通過できます。過去に候補だった未同梱モデルはカタログ・APK選択肢から除外しています。

## 現行設定2モデルの重み

正本は [`config/models.json`](../../config/models.json) です。

| モデル重み | SHA-256 | 関連コード | weight license | commercial use |
|---|---|---|---|---|
| `3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx` | `aa3cfc16963a10586a9393f5035d6d6b57e98d358b347f80c2a30bf4f00ceba2` | 3D-Speaker、Apache-2.0 | Apache-2.0（[ModelScope公式モデルページ](https://modelscope.cn/models/iic/speech_campplus_sv_zh_en_16k-common_advanced)） | **許可** |
| `wespeaker_en_voxceleb_resnet34_LM.onnx` | `df0cec64c3bba5dbc3637e50c4259de348a24124f4bb399f413fa1d4b44ba605` | WeSpeaker、CC-BY-4.0 | CC-BY-4.0（[WeSpeaker model card](https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM/blob/f0c48c298fd835726c27956a5d617bad7115627e/README.md)） | **許可** |

WeSpeakerは公式model cardのrevision `f0c48c298fd835726c27956a5d617bad7115627e`を使用します。CAM++ Chinese-EnglishはModelScope公式モデルページを根拠とします。配布ページがファイルを公開している事実だけでは、利用・改変・再配布・商用利用の許諾になりません。

関連コードの公式ライセンス:

- [3D-Speaker LICENSE](https://github.com/modelscope/3D-Speaker/blob/master/LICENSE)
- [WeSpeaker model card](https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM/blob/f0c48c298fd835726c27956a5d617bad7115627e/README.md)

3D-Speaker LICENSEはコードリポジトリのライセンス根拠です。CAM++ Chinese-Englishについては、これに加えてModelScope公式モデルページの重みライセンス記載を直接の根拠として扱います。

## 推論ランタイム

| 利用箇所 | 成果物 | コードライセンス | 現在の確認範囲 |
|---|---|---|---|
| Windows | `com.microsoft.onnxruntime:onnxruntime:1.20.0` | MIT | [`libs.versions.toml`](../../gradle/libs.versions.toml)で版を固定。公式 [v1.20.0 LICENSE](https://github.com/microsoft/onnxruntime/blob/v1.20.0/LICENSE) を確認 |
| Android | `sherpa-onnx-static-link-onnxruntime-1.13.4.aar` | sherpa-onnx: Apache-2.0、静的リンクされた ONNX Runtime: MIT | [`app/build.gradle.kts`](../../app/build.gradle.kts)でAARを参照。公式 [sherpa-onnx v1.13.4 LICENSE](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/LICENSE) と上記 ONNX Runtime ライセンスを確認 |

ARMv7 AAR は ONNX Runtime を静的リンクしているため、別の `libonnxruntime.so` は不要です。ただし、静的リンクでファイルが見えなくなっても、該当コードのライセンス表示や third-party notices の検討が不要になるわけではありません。

現在のリポジトリには、このAARとモデルを含む配布物向けに確定した第三者ライセンス一覧・NOTICE・SBOM がありません。本番配布前に生成・レビューが必要です。

## 評価データは別監査

モデル重みの許諾と、評価 WAV の利用条件も別です。テストデータはリポジトリ内に保管せず、評価の都度、利用条件を確認できる公開情報・公開データセットから収集します。収集したアーカイブ・派生WAVはGitやAPKへ含めません。

RyuseiNetは今回の過去比較と現行設定2モデルの対象外で、重み取得、追加学習、変換、APK統合を行っていません。そのため本文のライセンス監査表には含めません。

## 本番利用前のゲート

次をすべて証拠ファイルとして保存するまで、モデル重みの商用利用・顧客配布・APK同梱を承認しません。

1. 各 ONNX ファイルまたはその配布リビジョンへ明示的に適用されるライセンス本文
2. モデル提供者による商用利用と再配布の可否
3. 学習データ由来の追加条件、用途制限、地域制限の有無
4. 必要な著作権表示、NOTICE、モデルカード、帰属表示
5. 使用する実ファイルの SHA-256 と、許諾証拠が指す版の一致
6. APK/AAR/JAR を含む第三者ライセンス一覧と SBOM のレビュー

このゲートを満たしていないモデルだけ、[`config/models.json`](../../config/models.json) の `weightLicense`、`commercialUse`、`licenseEvidence` を `UNVERIFIED` のまま維持します。配布ゲートは目録2件の欠落・重複、Android列挙2件との対応、同梱assetのサイズとSHA-256、未審査ONNXの混入を検査します。
