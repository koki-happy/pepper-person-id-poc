# pepper-person-id-poc

旧Pepper（Android 6.0 / API 23 / armeabi-v7a）向けの顔・話者識別PoCです。顔識別の既存実装を維持しながら、話者モデルをWindowsで比較し、同一WAVをAndroidと照合できる構成です。

## 構成

- `speaker-core`: Kotlinのみ。L2正規化、話者centroid、cosine、threshold、Top-2 margin、FAR/FRR/EER
- `speaker-benchmark`: Kotlin/JVM + ONNX Runtime Java。Windows x64 CPU用CLI
- `app`: Kotlin Android。OpenCV AARとsherpa-onnx Kotlin/JNI API

自分たちが保守する話者コードと依存管理はKotlin、Gradle Wrapper、Gradle Kotlin DSL、Version Catalogへ統一しています。`uv`、Python、pip、vcpkg、自作C++、自作JNI、Docker、Nixは話者経路で使用しません。既存の顔モデル変換スクリプトだけは別のレガシー保守経路です。

## Windowsセットアップと実行

```powershell
.\scripts\windows\bootstrap.ps1
.\gradlew.bat :speaker-benchmark:run --args="--config config/speaker-benchmark.json"
```

同じ処理のPowerShell wrapper:

```powershell
.\scripts\windows\run-speaker-benchmark.ps1
.\scripts\windows\verify-speaker-reference.ps1 -SkipBenchmarkRun
.\scripts\windows\verify-speaker-parity.ps1 -SkipBenchmarkRun `
  -AndroidResultsDirectory results/android-speaker-parity
```

`verify-speaker-reference.ps1`はWindows smokeの全5モデル・10スコアを固定値へ回帰確認します。`verify-speaker-parity.ps1`は固定値を使わず、端末から回収したAndroid JSONを必須入力としてWindowsの`windows-parity.json`と比較します。

出力はGit除外された`results/windows-speaker-benchmark/`へ生成されます。`summary.md`、metrics/predictions/score CSV、threshold/model/environment JSON、`windows-parity.json`を含みます。

## JVS日本語評価

JVSの利用条件を確認・同意した環境だけで、次を実行します。アーカイブとWAVはGit除外です。

```powershell
.\scripts\windows\download-jvs.ps1 -AcceptTerms
.\scripts\windows\prepare-jvs-evaluation.ps1
.\scripts\windows\run-speaker-benchmark.ps1 -Config config/speaker-benchmark-jvs.json
```

2026-07-13実測は、JVS 12話者・168 WAVを使い、登録4話者、developmentの登録4話者＋別のUnknown 4話者、独立test 4話者、最終Unknown 4話者で実施しました。2秒・3秒・5秒は各56本、splitを跨ぐ同一原音は0件、VADは無効です。threshold/marginはdevelopment 48本だけで選び、test/Unknownには固定しました。

暫定FAR `<= 5%`を満たした上位はCAM++ Chinese-English（FAR 0、EER 0.017857、p50 40.202 ms）とERes2Net（FAR 0、EER 0.020833、p50 107.127 ms）です。これはWindows x64・JVS studio音声の結果であり、Pepper ARMv7の速度やマイク精度の代替ではありません。数値の正本は[`results/jvs-speaker-benchmark/summary.md`](results/jvs-speaker-benchmark/summary.md)です。

## 注意

テストデータはリポジトリ内に保管せず、評価の都度、利用条件を確認できる公開情報・公開データセットから収集します。収集時には出典、ライセンス、取得URLを確認し、データ本体はGitや製品APKへ含めません。

同梱の中国語3ファイルは配線スモークです。JVS評価と混同しないでください。JVS評価は日本語の公開studio音声であり、Pepper実機マイクの別セッション評価は未完了です。

Android APKは指定がなければPepper向け`armeabi-v7a`だけを収録します。x86を必要とする検査は`-PtargetAbi=x86`を明示してください。ただし、同梱AARのx86版ONNX RuntimeはAPI 23にない`__write_chk`を参照するため、API 23 x86エミュレータでのネイティブ話者推論には使用できません。また、`config/models.json`の`weightLicense`、`commercialUse`、`licenseEvidence`が未確認の間はrelease buildが失敗します。ゲートは目録5件、Android列挙3件、同梱assetのサイズとSHA-256も照合します（debug buildは可能です）。

API 28 x86ではinstrumentation 2テストが成功し、3モデル×2データセット×2クエリの12個のparity JSONを回収済みです。これはAndroid経路の実測ですが、Pepper API 23 / ARMv7のparityや性能を代替しません。

RyuseiNetは追加学習と別の検証経路が必要なため、今回の5モデル本比較には含めず、未実装・未比較です。

詳細は[`docs/poc/`](docs/poc/)と[`docs/pepper-multimodal-identity-poc.md`](docs/pepper-multimodal-identity-poc.md)を参照してください。

## 顔検出・顔推論基盤の比較

モデル選択画面で、顔検出はML Kit Face Detection 16.1.7 Bundled（既定）または従来YuNet、顔特徴量の推論基盤はOpenCV 5.0.0、ONNX Runtime 1.27.0、ncnn 20260526、MNN 3.5.0を選べます。ML KitはAPK同梱モデルを使い、追跡ID、Euler角、SFace／0095向け5点座標を出力します。検出confidenceはAPIから公開されないため、画面では`N/A`と表示します。

ローカル成果物の準備:

```powershell
# ncnn/MNN公式ARMv7ライブラリとSFace/0095変換モデル
.\scripts\windows\setup-native-face-runtimes.ps1

# API 23 / armeabi-v7a / 使用演算子限定のONNX Runtime Java AAR
.\scripts\windows\build-onnxruntime-android-aar.ps1
```

未登録人物もリアルタイム処理の対象です。ML KitまたはFaceTrackerの`trackId`で画面内を追跡し、登録人物との1:N照合でUnknownになった顔は、特徴量をセッション内だけでクラスタリングして`anonymous-001`形式の一時IDを付けます。一時特徴量は画面終了またはリセットで破棄し、永続保存しません。
