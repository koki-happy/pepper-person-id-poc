# 最終推奨（JVS本比較の履歴と現行APK）

## 判断

過去のWindows/JVS比較ではCAM++ Chinese-EnglishとERes2Netが上位でした。ERes2Netは現在のAPKへ同梱せず、設定・カタログから削除しています。現行APKで残す話者モデルはCAM++ Chinese-EnglishとWeSpeaker ResNet34-LMです。

ただし、過去の比較は「Windows x64・JVS studio音声」の結果です。現在の2モデルは重みライセンスを確認済みですが、Pepper実機マイクでの精度、ARMv7性能、データ条件、NOTICE/SBOMが未確定のため、本番採用はまだ承認しません。

## 過去の5モデル比較記録

| モデル | JVS FAR | EER | 扱い |
|---|---:|---:|---|
| CAM++ Chinese-English | 0 | 0.017857 | 第1候補。ライセンス確認済み。Pepper実機比較へ進める |
| ERes2Net English | 0 | 0.020833 | 精度上の第1候補群。ただし重みライセンス未確認 |
| TitaNet-S | 0.0625 | 0.041667 | 保留。精度は良いが暫定FAR 5%を超過 |
| CAM++ English | 0.0833 | 0.333333 | 今回候補から外す |
| SpeakerNet-M | 0.3125 | 0.041667 | 今回候補から外す |

2秒群では上位2モデルとも0.78125と低く、誤りは主にUnknown判定によるFRRです。Pepper評価では2秒音声を必ず独立集計します。

## 次の受入ゲート

1. Androidへ同梱済みのCAM++ Chinese-EnglishとWeSpeaker ResNet34-LMを、同じ前処理・判定形式のままPepper実機で比較する。
2. Pepperマイクで登録4名以上、Unknown 4名以上、別セッション、2/3/5秒、距離・動作音条件を収録する。
3. developmentだけでthreshold/marginを選び、独立test/UnknownでFAR `<= 5%`を再確認する。
4. Pepper ARMv7でレイテンシ、native memory、長時間安定性を実測する。
5. 採用するONNX重みのライセンス、商用利用、再配布、NOTICE/SBOMを確定する。

API 28 x86で成功した2 instrumentation testsと3モデル12 JSON（うち中国語WAV 6件はWindowsとの自動parity合格）はAndroid経路の有効な証拠ですが、Pepper API 23 / ARMv7のparityと性能の代替ではありません。RyuseiNetは今回の範囲外であり、追加学習なし、未実装・未比較です。
