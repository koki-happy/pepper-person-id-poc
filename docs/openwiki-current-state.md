# pepper-person-id-poc 正本

この文書は、`codex/face-identification`ブランチの現行コードを根拠とする正本である。日付付きスナップショットではなく、更新時点の現行情報を記載する。

# 0. 文書方針

OpenWikiの「コードを根拠に現行構成・責務・依存関係を説明する」考え方と、GitHub Spec Kitの「constitution → spec → plan → tasks」の分離を、1ページ内の章構成として採用する。

1. **Project Constitution**: 長期的に維持する原則
2. **OpenWiki As-Is**: 現行コードの構成・責務・依存関係
3. **Feature Specification**: 入出力、受入条件、計算式
4. **Plan**: 実装・評価の進め方
5. **Tasks**: 未完了作業のみ
6. **Evidence**: テスト、評価、Pepper実測

正本の優先順位は、現行コード、Constitution、Feature Specification、Plan、Tasks、Evidenceの順とする。コードと説明が矛盾する場合、As-Isはコードを正とする。

# 1. Project Constitution

## 1.1 Unknown-first

- 登録人物・登録話者として受理するには、Feature Specificationが定める条件を満たす。
- 条件を満たさない入力は`Unknown`とする。
- 未登録人物・未登録話者へ匿名IDや匿名クラスタを付与しない。
- 顔検出、姿勢推定、VADなど、識別に必要なリアルタイム前処理は禁止しない。
- モデル、ライブラリ、閾値、解析間隔、VAD方式、特徴量集約方式はFeature仕様であり、Constitutionへ固定しない。

## 1.2 Privacy by Design

- 顔画像、動画、PCM、WAVを永続保存しない。
- 顔・声特徴量と人物メタデータは個人データとして扱う。
- 保存項目、保存先、利用目的、保持期間、削除方法、外部送信の有無を説明可能にする。
- 本番利用には保存時暗号化、アクセス制御、削除確認を別途要求する。

## 1.3 Pepper-first

- 受入対象はAndroid 6.0 / API 23 / ARMv7 / 約1 GB RAMの旧Pepperタブレットとする。
- 推論は端末内CPUで実行し、主要機能をオフラインで利用可能にする。
- Pepper受入完了には、ARMv7 APKのインストール、起動、主要操作フローの実行が必要である。
- 測定不能な指標を0として報告しない。高速化は実測後に行う。

## 1.4 Evidence and Traceability

- User Storyは独立してテスト可能にする。
- 要件、受入条件、テスト、タスク、対象コード、実機・評価結果を追跡可能にする。
- モデルと照合方式を別軸として記録する。
- モデル比較では方式を固定し、方式比較ではモデルを固定する。
- `main`へ直接実装せず、作業ブランチ、テスト、レビューを経由する。

## 1.5 評価指標

$$
\operatorname{FAR}=\frac{N_{\mathrm{unknown\rightarrow identified}}}{N_{\mathrm{unknown\ trials}}}
$$

$$
\operatorname{MIR}=\frac{N_{\mathrm{registered\rightarrow wrong\ person}}}{N_{\mathrm{registered\ trials}}}
$$

$$
\operatorname{Accuracy}=\frac{N_{\mathrm{registered\rightarrow correct\ person}}}{N_{\mathrm{registered\ trials}}}
$$

$$
\operatorname{FRR}=\frac{N_{\mathrm{registered\rightarrow Unknown}}}{N_{\mathrm{registered\ trials}}}
$$

$$
\operatorname{EER}=\operatorname{FAR}(\tau^*)=\operatorname{FRR}(\tau^*)
$$

$$
\operatorname{FAR}\succ\operatorname{MIR}\succ\operatorname{Accuracy}\succ\operatorname{FRR}\succ\operatorname{EER}
$$

# 2. OpenWiki As-Is

## 2.1 実行境界と依存関係

- Androidアプリ: Kotlin / Jetpack Compose / `app`モジュール1つ
- 受入端末: 旧Pepperタブレット / API 23 / ARMv7
- 顔入力: CameraX
- 顔検出・特徴量: OpenCV 5.0.0 / YuNet / SFace / 0095
- 音声入力: AudioRecord / mono PCM16
- VAD・話者特徴量: Silero VAD / sherpa-onnx 1.13.4 / CAM++ / ERes2Net
- 保存: アプリ専用領域の人物プロファイル、SharedPreferences、JSON Lines評価ログ
- 推論: 端末内CPU、オフライン

```mermaid
flowchart TB
    Camera[前面カメラ] --> FaceDetect[YuNet 顔検出・5点ランドマーク]
    FaceDetect --> Pose[solvePnP 姿勢推定]
    Pose --> PoseGate[中央値・姿勢範囲・安定時間]
    FaceDetect --> FaceModel[SFace / 0095]
    PoseGate --> FaceModel
    FaceModel --> FaceTemplate[顔テンプレート集約方式]
    FaceTemplate --> FaceScore[Cosine・人物スコア]
    FaceScore --> FaceDecision[閾値・候補差]

    Mic[マイク] --> PCM[AudioRecord PCM16]
    PCM --> VAD[Silero VAD / Energy VAD]
    VAD --> Segment[発話区間化]
    Segment --> VoiceModel[CAM++ / ERes2Net]
    VoiceModel --> VoiceTemplate[声テンプレート集約方式]
    VoiceTemplate --> VoiceScore[Cosine・人物スコア]
    VoiceScore --> VoiceDecision[閾値・候補差]

    FaceDecision --> Result[人物ID または Unknown]
    VoiceDecision --> Result
```

## 2.2 モデルと方式の区別

モデルは入力から特徴量を生成する。

$$
\mathbf e=f_m(x)
$$

方式は、特徴量の保存、集約、比較、最終判定を定める。モデルが同じでも方式が変わればスコア分布と最適閾値は変わる。

### モデル

| ID | 種類 | モデル | 出力 | 状態 |
|---|---|---|---:|---|
| Face-Model-1 | 顔 | SFace 2021dec | 128次元 | 実装済み、Pepper確認済み |
| Face-Model-2 | 顔 | face-reidentification-retail-0095 | 256次元 | 実装済み、Pepper確認済み |
| Voice-Model-1 | 声 | CAM++ English VoxCeleb | 512次元 | 実装済み |
| Voice-Model-2 | 声 | ERes2Net English VoxCeleb | 192次元 | 実装済み、現行既定 |
| Voice-Reference | 声 | RyuseiNet | モデル依存 | PC参照用、Pepperへ配置しない |

### 集約・照合方式

| ID | 対象 | 登録テンプレート | 入力 | 人物スコア | 状態 |
|---|---|---|---|---|---|
| Face-Method-1 | 顔 | 正面・左・右を別保存 | 1特徴量 | 3姿勢との最大Cosine | 現行実装 |
| Face-Method-2 | 顔 | 同一姿勢内をL2正規化平均、姿勢は別保持 | 1特徴量 | 姿勢代表との最大Cosine | 推奨一般形。各姿勢1件ではMethod-1と等価 |
| Face-Method-3 | 顔 | 全姿勢を1本へL2正規化平均 | 1特徴量 | 代表1本とのCosine | 比較用 |
| Face-Method-4 | 顔 | Face-Method-2 | 同一trackIdの短時間平均 | 姿勢代表との最大Cosine | 安定化候補 |
| Voice-Method-1 | 声 | 登録発話を別保存 | 1発話1特徴量 | 全登録発話との最大Cosine | 現行実装 |
| Voice-Method-2 | 声 | 全登録発話のL2正規化平均 | 1発話1特徴量 | 登録代表1本とのCosine | 最優先比較候補 |
| Voice-Method-3 | 声 | 品質重み付きL2正規化平均 | 1発話1特徴量 | 登録代表1本とのCosine | 次候補 |
| Voice-Method-4 | 声 | PLDA用登録表現 | 1発話1特徴量 | PLDAスコア | PC研究候補 |

## 2.3 現行画面と差分

- 人物登録と登録済み人物の連続顔識別を実装済み
- 声登録と登録済み話者識別を実装済み
- 匿名顔機能は削除済み
- 匿名話者機能は残存し、`003-speaker-identification`で削除する
- 音声認識、顔・声統合、会話履歴はプレースホルダー

## 2.4 コード責務

| 責務 | 主なコード |
|---|---|
| 顔姿勢範囲、中央値、安定時間 | `domain/face/HeadPoseGuidance.kt` |
| 顔の最大値、候補差、Unknown | `domain/face/FaceIdentifier.kt` |
| 3姿勢登録、全trackId連続識別 | `application/face/FaceIdentityCoordinator.kt` |
| solvePnP姿勢推定 | `infrastructure/face/HeadPoseEstimator.kt` |
| SFace / 0095 | `infrastructure/face/*EmbeddingEngine.kt` |
| PCM録音・VAD選択 | `infrastructure/audio/AndroidPcmAudioRecorder.kt` |
| 発話区間化 | `domain/audio/PcmUtteranceSegmenter.kt` |
| CAM++ / ERes2Net | `infrastructure/speaker/SherpaOnnxSpeakerEmbeddingEngine.kt` |
| 現行話者最大値・閾値 | `domain/speaker/SpeakerIdentifier.kt` |

# 3. Feature Specification

## 3.1 顔登録・顔識別

詳細正本: `specs/001-face-identification/spec.md`

### 3.1.1 姿勢判定

同じtrackIdの直近5件を中央値化する。

$$
\widetilde y_{t,k}=\operatorname{median}(y_{t-4,k},\ldots,y_{t,k})
$$

$$
I_F=\mathbf 1(|\widetilde y|\le8\land|\widetilde p|\le8)
$$

$$
I_L=\mathbf 1(-32\le\widetilde y\le-18\land|\widetilde p|\le8)
$$

$$
I_R=\mathbf 1(18\le\widetilde y\le32\land|\widetilde p|\le8)
$$

同一trackIdの顔が対象範囲内を1,000 ms維持した場合だけ特徴量を採用する。

### 3.1.2 Face-Method-2

人物$p$、モデル$m$、姿勢$o$の登録特徴量を、

$$
R^{face}_{p,m,o}=\{\mathbf r_{p,m,o,1},\ldots,\mathbf r_{p,m,o,K_{p,o}}\}
$$

とする。各特徴量をL2正規化し、同一姿勢内だけを平均して再正規化する。

$$
\widetilde{\mathbf r}_{p,m,o,j}=\frac{\mathbf r_{p,m,o,j}}{\|\mathbf r_{p,m,o,j}\|_2}
$$

$$
\overline{\mathbf r}_{p,m,o}
=
\frac{\sum_{j=1}^{K_{p,o}}w_{p,o,j}\widetilde{\mathbf r}_{p,m,o,j}}
{\left\|\sum_{j=1}^{K_{p,o}}w_{p,o,j}\widetilde{\mathbf r}_{p,m,o,j}\right\|_2}
$$

現行は各姿勢1件なので$K_{p,o}=1$であり、Face-Method-2はFace-Method-1と同じ結果になる。

### 3.1.3 顔識別

$$
c^{face}_{i,p,o}=\widetilde{\mathbf q}_{t,i}^{\top}\overline{\mathbf r}_{p,m,o}
$$

$$
s^{face}_{i,p}=\max_{o\in\{F,L,R\}}c^{face}_{i,p,o}
$$

$$
p_1=\arg\max_p s^{face}_{i,p},\qquad p_2=\arg\max_{p\ne p_1}s^{face}_{i,p}
$$

$$
d_i=s^{face}_{i,p_1}-s^{face}_{i,p_2}
$$

$$
\widehat y^{face}_i=
\begin{cases}
p_1,&s^{face}_{i,p_1}\ge\tau_{face}\land d_i\ge\delta_{face}\\
\mathrm{Unknown},&\text{otherwise}
\end{cases}
$$

現行既定値は$\tau_{face}=0.60$、$\delta_{face}=0.0$である。

### 3.1.4 Face-Method-4

同一trackIdの直近$H$件を入力側で集約する候補である。

$$
\overline{\mathbf q}_{t,i}
=
\operatorname{Normalize}\left(
\sum_{h=0}^{H-1}\alpha_h\operatorname{Normalize}(\mathbf q_{t-h,i})
\right)
$$

現行は$H=1$であり未実装である。

## 3.2 声登録・話者識別

詳細正本: `specs/003-speaker-identification/spec.md`

### 3.2.1 PCM・VAD

$$
z[n]=\frac{x[n]}{32768}
$$

$$
T_{voice}=\frac{1000}{F_s}\sum_kN_kv_k
$$

$$
\operatorname{SufficientAudio}\iff T_{voice}\ge1000\,\mathrm{ms}
$$

### 3.2.2 Voice-Method-1

$$
R^{voice}_{p,m}=\{\mathbf r_{p,m,1},\ldots,\mathbf r_{p,m,M_p}\}
$$

$$
s^{voice,\max}_p=\max_j\cos(\mathbf q^{voice},\mathbf r_{p,m,j})
$$

現行コードはこの最大値と閾値だけで判定する。

### 3.2.3 Voice-Method-2

$$
\widetilde{\mathbf r}_{p,m,j}=\frac{\mathbf r_{p,m,j}}{\|\mathbf r_{p,m,j}\|_2}
$$

$$
\overline{\mathbf r}^{voice}_{p,m}
=
\frac{\sum_{j=1}^{M_p}\widetilde{\mathbf r}_{p,m,j}}
{\left\|\sum_{j=1}^{M_p}\widetilde{\mathbf r}_{p,m,j}\right\|_2}
$$

$$
s^{voice,centroid}_p
=
\widetilde{\mathbf q}^{voice\top}\overline{\mathbf r}^{voice}_{p,m}
$$

Voice-Method-2は、登録件数による最大値の偏りを抑える最優先比較候補である。

### 3.2.4 話者判定目標

方式が返した人物スコア$s^{voice}_p$から、

$$
p_1=\arg\max_p s^{voice}_p,\qquad p_2=\arg\max_{p\ne p_1}s^{voice}_p
$$

$$
d^{voice}=s^{voice}_{p_1}-s^{voice}_{p_2}
$$

$$
\widehat y^{voice}=
\begin{cases}
p_1,&s^{voice}_{p_1}\ge\tau_{voice}\land d^{voice}\ge\delta_{voice}\\
\mathrm{Unknown},&\text{otherwise}
\end{cases}
$$

Voice-Method-2と候補差は未実装である。

# 4. Plan

## 4.1 顔

- `001-face-identification`はFace-Method-1で完了している。
- Face-Method-2は複数サンプル／姿勢へ拡張する場合の標準候補とする。
- `002-face-evaluation`ではモデルと方式を別軸で比較する。
- 各モデル×方式ごとに閾値と候補差を再選択する。

## 4.2 声

- 匿名話者機能を削除する。
- Voice-Method-1と2を純粋なDomain方式として実装する。
- 候補差と16 kHz入力境界を実装する。
- 同一モデルで方式を比較する。
- 採用方式を固定してCAM++とERes2Netを比較する。
- 日本語データとPepperマイクでモデル、方式、閾値、候補差を決定する。

# 5. Tasks

| ID | 未完了作業 | 完了条件 |
|---|---|---|
| 002-T011 / T014 | BIWIを含む全件評価・再現性確認 | 取得可能な公式サンプルを評価し、漏洩0件と再現性を確認 |
| 002-T019 / T020 | Pepper長時間・複数顔評価 | 30分連続と実人物2人を単顔基準と分離報告 |
| 002-T022 | 顔評価結果の最終収束 | 測定済み、未取得、外部blockerを確定 |
| 002-T023〜T025 | 顔方式比較 | `model_id`と`method_id`を分離し、Face-Method-1〜3を比較 |
| 003-T001〜T004 | 匿名話者削除 | UI、Coordinator、Clusterer、結果、テスト、ログから除去 |
| 003-T005〜T009 | 話者方式実装 | Voice-Method-1/2を純粋Domain方式として実装・テスト |
| 003-T010〜T014 | 候補差判定 | 第2候補、候補差、設定、ログ、単体テストを追加 |
| 003-T015〜T017 | 音声入力境界 | 16 kHz以外を無言で推論へ渡さない |
| 003-T018〜T025 | 日本語方式・モデル評価 | モデル固定の方式比較と方式固定のモデル比較を報告 |
| 003-T026〜T031 | Pepper評価と収束 | Pepperマイクでモデル・方式・閾値・候補差を確定 |

# 6. Evidence

## 6.1 顔機能受入

- Nothing Phone (3a): 3姿勢登録、SFace / 0095、連続識別、顔消失時の結果消去を確認
- Pepper: ARMv7 APK、3姿勢登録、登録済み人物の連続識別を確認
- 顔画像、動画、PCM、WAVがアプリ専用領域へ保存されないことを確認

## 6.2 Pointing'04

次はFace-Method-1による結果である。

| モデル | 登録方式 | 方式 | FAR | MIR | Accuracy | FRR |
|---|---|---|---:|---:|---:|---:|
| 0095 | 正面 | Face-Method-1 | 1.61% | 0.00% | 93.43% | 6.57% |
| 0095 | 正面・左・右 | Face-Method-1 | 0.72% | 0.00% | 95.58% | 4.42% |
| SFace | 正面 | Face-Method-1 | 6.45% | 0.12% | 92.71% | 7.17% |
| SFace | 正面・左・右 | Face-Method-1 | 3.23% | 0.00% | 94.03% | 5.97% |

モデルの優劣と方式の優劣を混同しない。方式を変更した場合、閾値と候補差を再調整し、同一の最終分割で評価する。
