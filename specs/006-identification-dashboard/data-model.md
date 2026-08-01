# Data Model: 識別ダッシュボード

## DashboardMode

- `FACE` / `SPEAKER`
- 画面切替時に移動元入力を解放してから移動先を開始する。

## SamplingPolicy

- `multipleSamplesEnabled: Boolean = false`
- `showFaceLandmarks: Boolean = false`
- OFF時は匿名IDごとの最初の有効サンプルだけを保持する。
- ON時は`maximumUpdateCount`まで代表特徴量を更新する。

## ModelRuntimeSet

- `role`: 顔検出、顔特徴量、話者特徴量、音声区間検出
- `modelId`, `modelDisplayName`
- `runtimeId`, `runtimeDisplayName`
- `artifactId`, `compatibilityStatus`, `selectable`
- `displayName`: `モデル名／推論基盤名`
- 選択・保存・復元・適用の単位は常にセット全体。

## SampleVideoState

- `selection`: `OFF`、`SAMPLE_20_MB`、`SAMPLE_200_MB`（初期値`OFF`）
- `asset`: 選択時だけ`sample_20MB.mp4`または`sample_200MB.mp4`の1本
- `playbackState`: 選択した1本だけ繰り返し再生
- `muted`: 常にtrue
- 画面破棄時にプレイヤーを解放する。

## ParameterDraft

- 基本設定: 複数サンプル取得、顔の特徴点表示、顔・話者の照合閾値と最大更新件数
- 顔検出: 解析間隔、YuNet検出スコア、NMS、最大候補数、ML Kit最小顔サイズ
- 顔ラベル追跡: 継続IoU、消失許容フレーム数
- 発話検出: VAD閾値・最小無音・最小発話・最大発話、アプリ側の終了無音・最大発話
- 音声品質: 作成・更新の最小時間と最小発話率、最小RMS、クリッピング振幅・率
- 話者ラベル追跡: 区間間類似度、消失許容区間数
- 各値は型、許容範囲、初期値を持ち、保存時に一括検証する。
- YuNet専用値とML Kit専用値は両方保持するが、選択モデル側だけを画面表示・実処理へ適用する。
- 保存済み値と編集中値を区別し、保存前の戻る操作では破棄する。

## AudioLevelSample

- `elapsedMillis: Long`: 表示開始からの経過時間
- `levelDbFs: Float`: -90～0 dBFS
- `displayPercent: Float`: 0～100%。`levelDbFs`を表示用に線形正規化した正数
- 表示専用の循環履歴とし、音声データは保持しない。

## SessionAnonymousClusterStore

- 顔と話者の匿名クラスタは実行中プロセスのメモリ内だけに保持する。
- 顔画像、動画、PCM/WAV、識別履歴全件、個々の特徴量ログは保存しない。

## IdentificationHistory

- 顔識別結果は識別フレーム単位で直近50件をメモリ上に保持する。
- 音声識別結果はVADで発話として確定し、話者識別処理を完了した発話単位で直近50件をメモリ上に保持する。
- 51件目を追加すると最古の1件を削除する。
- 雑音・無音、顔画像、音声データ自体は保持しない。
