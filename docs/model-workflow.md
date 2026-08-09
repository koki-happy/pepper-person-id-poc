# モデル取得・変換・検証フロー

更新日: 2026-08-09

## 方針

モデル本体はサイズ、ライセンス、ABI、APIレベル、実機検証の組み合わせで管理するため、Gitにはコミットしない。Gitで管理する正本は次の4つである。

1. `config/models.json`: モデル、モデル空間、runtime、取得元、SHA-256、変換記録、ライセンス
2. `docs/model-provenance.md`: 配布元、取得方法、既知の制約、受入状況
3. `scripts/`: ダウンロード、変換、manifest生成、数値比較、parity検証
4. `app/build.gradle.kts` と `buildSrc/`: ハッシュ、カタログ、ライセンス、candidate allowlistのビルドゲート

`models[]`は現在のベンチマーク・明示選択の候補を保持し、`releaseModelIds[]`がreleaseへ同梱する話者モデルを定義する。現在はAPK同梱対象だけを候補として残している。

現在のモデル本体は、主に次のGit管理外ディレクトリへ配置される。

- `app/src/benchmark/assets/models/`
- `models/`
- `app/libs/`
- `data/audio/`

## モデル別の正本とフロー

| 分類 | モデル | 元モデル・取得元 | 変換・準備ソース | 現在の状態 |
|---|---|---|---|---|
| 顔検出 | YuNet 2026may | OpenCV Zoo | `scripts/litert/convert_models.py`、`scripts/native-face/prepare-yunet-2026may-native.ps1` | ONNXを正本とし、LiteRT/NCNN/MNNを派生生成。数値parity済み、Pepper実機受入は別ゲート |
| 顔特徴量 | SFace 2021dec | OpenCV Zoo | `scripts/litert/convert_models.py` | ONNXを正本とし、LiteRTを再生成可能。NCNN/MNNのカタログ記録はあるが、専用の一貫した再生成・数値検証は未完了 |
| 顔特徴量 | face-reidentification-retail-0095 | Open Model Zoo OpenVINO IR XML/BIN | `scripts/convert-0095-to-onnx.ps1` → `scripts/litert/convert_models.py` | IR→ONNXは実験的な逆変換。入力比較、実顔比較、SHA-256検証あり |
| 音声活動 | Silero VAD | sherpa-onnx配布物 | 変換なし。`scripts/setup-local-inference-assets.ps1`で取得 | sherpa-onnxへ直接ロード |
| 話者特徴量 | CAM++ Chinese-English | 3D-Speaker/ModelScope公式モデル | `scripts/setup-local-inference-assets.ps1`で取得 | 変換なし。Apache-2.0確認済み。Pepper受入は別ゲート |
| 話者特徴量 | WeSpeaker ResNet34-LM | WeSpeaker配布ONNX | `scripts/prepare-wespeaker-resnet34-lm.ps1`、`scripts/speaker/prepare-wespeaker-resnet34-lm.py` | グラフと重みは変更せず、sherpa-onnx用メタデータを付与 |
| 話者区間 | pyannote segmentation 3.0 | sherpa-onnx公式archive | 変換はリポジトリ外のsherpa-onnx手順。アプリ側は`SherpaPyannoteSegmentationEngine`でロード | 現行runtimeはsherpa-onnx static-link。INT8と重複キャッシュはactive assetから隔離済み。精度と最終Pepper受入は未完了 |
| 旧・未登録 | ReDimNet2 B1 | 元モデル・変換ソース未登録 | 現行`SpeakerModelOption`では使用せず、旧設定値の移行対象。active assetから削除済み |

## 再生成手順

### Git LFSでAPK同梱モデルを取得する場合

```powershell
git lfs install
git lfs pull
```

### オープンソースから再取得・変換する場合

```powershell
# 顔モデル、VAD、主要speaker assetを取得し、必要な派生物を生成
./scripts/setup-local-inference-assets.ps1

# 話者モデルだけをWindows benchmark用に取得・準備
./scripts/windows/download-speaker-models.ps1 -ModelId @("campplus-zh-en", "wespeaker-resnet34-lm")

# Android側のカタログ、ハッシュ、ライセンスを検証
./gradlew.bat :app:verifyModelCatalog :app:verifyReleaseModelLicenses --no-daemon
```

`setup-local-inference-assets.ps1`は指定URLから不足ファイルをダウンロードし、SHA-256を検証した後、必要な派生物と変換ONNXを生成する。Python、変換依存関係、ffmpegが必要であり、既存ファイルを再生成する場合は`-Force`を付ける。URL、revision、SHA-256は`config/models.json`、各準備スクリプト、`docs/model-provenance.md`を正本とする。

話者モデルのWindows取得・評価は、`config/models.json`を参照する
`scripts/windows/download-speaker-models.ps1`、`scripts/windows/verify-speaker-parity.ps1`、
`scripts/windows/verify-speaker-reference.ps1`を使用する。

## 受入の段階

1. 元モデルのURL、revision、サイズ、SHA-256を確認する。
2. 変換ツール、バージョン、入力shape、出力shapeをmanifestへ記録する。
3. 変換前後の固定入力の数値parityを確認する。
4. AndroidのAPI、ABI、runtimeでロードと推論を確認する。
5. Pepper API 23 / ARMv7で再インストール、ロード、推論、実音声・実顔の受入を確認する。

`BUILDABLE`やhost parityの状態は、Pepper実機での`VERIFIED`を意味しない。
モデルruntimeの失敗時に別runtimeへ自動切替してはならない。

## 残課題

- ReDimNet2は元モデルのrevision、取得URL、変換環境、出力SHA-256が未登録である。旧ONNXはactive assetから削除済みである。
- PNNX中間生成物4件はactive assetから削除し、カタログには履歴・ハッシュだけを残している。
- 0095、SFace、NCNN/MNN派生物の一部に`environmentDigest=UNRECORDED`または`convertedAt=null`が残る。
- LiteRT派生物は固定入力のhost比較があっても、ARM64 AndroidとPepper ARMv7の実機受入が未完了である。
- 現在の作業ツリーでは`SherpaPyannoteSegmentationEngine.kt`が未追跡である。採用する場合はコード、カタログ、受入証跡を同じ変更としてコミットする。
