# Windows 話者モデル実測

## 評価条件

数値の正本は[`results/jvs-speaker-benchmark`](../../results/jvs-speaker-benchmark/)です。中国語3 WAVの[`windows-speaker-benchmark`](../../results/windows-speaker-benchmark/)は配線スモークであり、本表と混同しません。

| 項目 | 値 |
|---|---|
| 生成時刻 | 2026-07-13T13:59:39.938767500Z |
| 環境 | Windows 11 10.0 / amd64、Java 17.0.14 |
| 推論 | Kotlin/JVM、ONNX Runtime Java 1.20.0 CPU |
| corpus | JVS `parallel100`、日本語studio音声 |
| WAV | 168本、16 kHz mono、2/3/5秒が各56本 |
| enrollment | 4話者・24本 |
| development | 48本（登録4話者24＋別Unknown 4話者24） |
| final evaluation | test 48本（登録4話者）＋unknown 48本（別の4話者） |
| leakage | splitを跨ぐ`source_group_id`は0 |
| threshold | モデルごとにdevelopmentだけで選択 |
| VAD | 無効 |

## 精度結果

| モデル | Top-1 | FAR | FRR | EER | Unknown誤受入 | 2秒 | 3秒 | 5秒 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| CAM++ English | 0.9375 | 0.083333 | 0.375000 | 0.333333 | 0.083333 | 0.90625 | 0.62500 | 0.78125 |
| CAM++ Chinese-English | 1.0000 | 0 | 0.145833 | 0.017857 | 0 | 0.78125 | 1.00000 | 1.00000 |
| ERes2Net English | 1.0000 | 0 | 0.145833 | 0.020833 | 0 | 0.78125 | 1.00000 | 1.00000 |
| SpeakerNet-M | 1.0000 | 0.312500 | 0.041667 | 0.041667 | 0.312500 | 0.84375 | 0.87500 | 0.75000 |
| TitaNet-S | 1.0000 | 0.062500 | 0.041667 | 0.041667 | 0.062500 | 0.93750 | 0.96875 | 0.93750 |

暫定FAR基準 `<= 5%`を満たしたのはCAM++ Chinese-EnglishとERes2Netです。TitaNet-Sは6.25%でわずかに超えました。Top-1はthreshold前の人物順位であり、Unknown拒否の成功を含まないため、FAR/FRRと一緒に読みます。

## developmentで選んだthreshold/margin

| モデル | threshold | margin | development FAR | development FRR |
|---|---:|---:|---:|---:|
| CAM++ English | 0.362584 | 0.116810 | 0 | 0.291667 |
| CAM++ Chinese-English | 0.750854 | 0 | 0 | 0 |
| ERes2Net English | 0.703234 | 0 | 0 | 0 |
| SpeakerNet-M | 0.651788 | 0 | 0 | 0 |
| TitaNet-S | 0.684103 | 0 | 0 | 0 |

各選択はdevelopment 48本（registered 24 / unknown 24）だけを使いました。testとfinal unknownはこの選択に使っていません。正確なレコードは[`thresholds.json`](../../results/jvs-speaker-benchmark/thresholds.json)です。

## レイテンシとメモリ

他の重いワークロードを停止した後にJVS本評価を再実行した最終値です。

| モデル | load ms | p50 ms | p95 ms | model bytes | dim | peak heap bytes | process committed virtual bytes* |
|---|---:|---:|---:|---:|---:|---:|---:|
| CAM++ English | 217.6040 | 40.1901 | 65.7016 | 29,596,978 | 512 | 196,162,848 | 587,993,088 |
| CAM++ Chinese-English | 217.1057 | 39.9358 | 66.0973 | 28,281,164 | 192 | 196,684,352 | 518,561,792 |
| ERes2Net English | 100.2225 | 106.3615 | 186.7711 | 26,485,263 | 192 | 196,816,992 | 575,860,736 |
| SpeakerNet-M | 25.5668 | 24.2656 | 41.3073 | 23,411,863 | 256 | 197,483,416 | 517,640,192 |
| TitaNet-S | 63.0932 | 32.6414 | 56.0459 | 40,257,283 | 192 | 197,891,064 | 618,295,296 |

\* process committed virtual memoryはaddress-space commitmentであり、RSS/PSSではありません。

いずれにせよWindows x64のload/p50/p95、JVM heap、process committed virtual memoryはPepper ARMv7のレイテンシ、RSS/PSS、native heapの代替ではありません。

## 限界

- JVSは公開studio収録で、Pepperマイク、Pepper動作音、距離差、別セッションを含みません。
- 各長さ32本のfinal decision accuracyであり、本番環境の信頼区間を確定する規模ではありません。
- 5モデルのONNX重みはライセンスと商用利用条件が未確認です。
- RyuseiNetは今回の5モデル比較に含めず、未実装・未比較です。

## 参照ファイル

- [`summary.md`](../../results/jvs-speaker-benchmark/summary.md)
- [`metrics.csv`](../../results/jvs-speaker-benchmark/metrics.csv)
- [`predictions.csv`](../../results/jvs-speaker-benchmark/predictions.csv)
- [`thresholds.json`](../../results/jvs-speaker-benchmark/thresholds.json)
- [`environment.json`](../../results/jvs-speaker-benchmark/environment.json)
- [`models.json`](../../results/jvs-speaker-benchmark/models.json)
