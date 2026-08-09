# Implementation Plan: Pepper匿名人物識別の完全実装

**Branch**: `codex/face-identification` | **Date**: 2026-07-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-full-person-identification/spec.md`

## Summary

既存の匿名顔・匿名話者PoCを、全候補評価、品質と保存可否の分離、モデル空間・資産・ランタイムの三層管理、複数ランタイム比較、LiteRT変換と同等性、複数話者・重複発話のfail-closed処理、詳細計測、比較用／Pepper候補用成果物へ拡張する。匿名クラスタはモダリティ別かつセッション内だけのインメモリ保持とし、顔声リンク、観測履歴、ID統合・分離、特徴量永続保存は実装しない。受入はARM64 Android端末を先行し、合格後にPepper API 23 / ARMv7で同じシナリオを実行する。

## Technical Context

**Language/Version**: Kotlin 2.2.10、Java 11、C++17、PowerShell、Python 3（変換・評価ツール）

**Primary Dependencies**: Android Gradle Plugin 9.2.1、Jetpack Compose、CameraX 1.6.1、OpenCV 5.0.0、ML Kit Face Detection 16.1.7、sherpa-onnx 1.13.4、ONNX Runtime Android 1.20.0（顔はARM64のみ、Pepper ARMv7はBLOCKED）、ncnn 20260526、MNN 3.5.0、Google LiteRT（固定版は実装時にAPI 23/ARMv7実機ゲートで確定）

**Storage**: SharedPreferencesの非生体設定のみ。匿名クラスタと特徴量はアプリケーションスコープのメモリだけに保持し、性能・診断イベントは端末内ファイルへ保存せずLogcatからADBで取得する。

**Testing**: JUnit 4、Truth、AndroidX Test/Espresso/Compose UI test、固定モデル入力instrumentation、JVM単体テスト、ADB実機シナリオ

**Target Platform**: 最終受入はPepper Android 6.0 / API 23 / armeabi-v7a / 約1 GB RAM / 端末内CPU / オフライン。ARM64 Android端末は先行検証用。

**Project Type**: 単一Androidアプリ＋Kotlin/JVM共通話者モジュール＋ローカルモデル変換ツール

**Performance Goals**: すべての段階時間とRTFを計測可能にし、Pepper実測から候補を収束する。未測定値はnullとし、目標値を推測で固定しない。

**Constraints**: minSdk 23、candidateはarmeabi-v7a、CPU推論、暗黙フォールバック禁止、生体特徴量・生メディアの永続化禁止、全候補保持、ライセンス未確認時の配布ゲート維持

**Scale/Scope**: 顔検出3候補、顔特徴量3候補、顔実行基盤5候補、話者特徴量3候補、VAD 1候補、話者活動推定1候補、2つの配布構成、7つの受入ストーリー

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Result |
|---|---|---|
| Anonymous-only Identification | 実名登録なし、顔声リンクなし、モダリティ別・セッション内匿名IDのみ | PASS |
| Privacy by Design | 画像・動画・PCM・WAV・特徴量を永続保存しない。評価データ本体を配布物へ含めない | PASS |
| Pepper-first | API 23 / ARMv7 / CPU / オフラインを最終受入とし、ARM64を代替扱いしない | PASS |
| Evidence and Traceability | FR、契約、タスク、テスト、Android証跡、Pepper証跡を対応付ける | PASS |
| Kotlin-first / existing runtimes | Kotlinを中心に既存OpenCVとsherpa-onnxを再利用し、追加ランタイムは互換表で隔離 | PASS |
| Workflow | constitution → spec → plan → tasks → implementation の順で実施 | PASS |

### Post-design Re-check

- データモデルは匿名・モダリティ分離を維持する。
- Repository契約は評価と更新を分け、インメモリ実装だけをproductionへ注入する。
- 評価ログ契約は生体特徴量と生メディアを禁止する。
- quickstartはAndroid端末合格後だけPepperへ進む。
- 外部モデルやランタイムの未確認事項は互換状態`BLOCKED`で表現し、フォールバックしない。

## Architecture

### 1. 共通識別ドメイン

顔と話者で共通の候補スコア、評価、判定、保存可否、品質理由を定義する。スコア計算は純粋関数とし、Repository変更は明示された`CREATE`または`UPDATE`操作だけが行う。`HOLD`ではID採番、重心、件数、キャッシュを不変にする。

### 2. セッションRepository

現在の`FileAnonymous*ClusterRepository`をproduction経路から外し、ApplicationスコープのインメモリRepositoryへ置き換える。起動時に旧`filesDir/biometric`を削除し、明示終了でメモリと旧ファイルを消去する。process death後の復元は行わない。

### 3. 顔パイプライン

CameraX → 回転・鏡像・色変換 → 検出 → 顔品質 → 5点ランドマーク → 位置合わせ → 特徴抽出 → L2正規化 → 全候補評価 → 保存方針 → 作成・更新・保留 → UI/計測。

ML Kitの検出confidenceは取得不能のためnull/N/Aとし、`1.0`を捏造しない。品質方針はconfidenceを提供する検出器と提供しない検出器を区別する。OpenCVと他ランタイムの前処理差も同等性ゲートの対象とする。

### 4. 話者パイプライン

AudioRecord 16 kHz mono PCM16 → Silero VAD → 発話窓 → 話者活動・重複推定 → 窓間局所話者追跡 → 単独話者区間抽出 → 音声品質 → 区間特徴量 → 局所話者集約 → L2正規化 → 全候補評価 → 保存方針 → 作成・更新・保留 → UI/計測。

44.1 kHzとEnergy VADへのフォールバックを削除する。話者活動推定が未利用または失敗した場合、複数話者安全性を保証できないためクラスタ更新を行わない。

### 5. モデル台帳と互換表

`config/models.json`を顔、話者、VAD、話者活動推定、変換物、ランタイム、互換性を含むschema v2へ移行する。`modelSpaceId`は特徴量互換性、`artifactId`は実ファイルと変換来歴、`runtimeId`は実行実装を表す。同じモデル空間の付与は同等性ゲート合格後だけ行う。

### 6. 変換と同等性

`scripts/litert/`へ固定環境、変換、検査、比較、manifest生成を置く。元ONNXは変更せず、入力ハッシュ、ツールrevision、完全なコマンド、出力ハッシュ、tensor metadata、警告を記録する。YuNetは検出件数・順位・座標・5点・NMS、SFaceは次元・有限値・正規化後cosine・識別判定を比較する。

### 7. 配布構成

`benchmark`は全候補の評価処理と同等性試験を含む。`candidate`は採用候補のみを含む。どちらも実行時の詳細イベントを端末内ファイルへ保存せず、必要時にADBでLogcatを取得する。debug/release build typeとproduct flavorを直交させる。

### 8. 計測とレポート

顔・話者の段階別時間、RTF、候補数、CPU、PSS、Java/native heap、端末空きメモリ、ドロップ、停止、エラーを構造化Logcatイベントとして出力する。ライブ画面は識別結果とCPU、PSS、総処理時間、解析FPSまたはRTFだけを表示し、履歴グラフや段階別統計を持たない。長時間試験ではADB側でLogcatを回収・集計し、計測対象スレッドでファイルI/Oを行わない。

## Project Structure

### Documentation (this feature)

```text
specs/005-full-person-identification/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── identification-result.md
│   ├── persistence-policy.md
│   ├── model-catalog.md
│   ├── face-pipeline.md
│   ├── speaker-pipeline.md
│   └── metrics.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── assets/models/
    │   ├── cpp/
    │   └── java/com/example/pepper_person_id_poc/
    │       ├── application/
    │       │   ├── face/
    │       │   └── speaker/
    │       ├── domain/
    │       │   ├── anonymous/
    │       │   ├── config/
    │       │   ├── face/
    │       │   ├── metrics/
    │       │   ├── model/
    │       │   └── speaker/
    │       ├── infrastructure/
    │       │   ├── audio/
    │       │   ├── face/
    │       │   ├── metrics/
    │       │   ├── model/
    │       │   └── repository/
    │       └── ui/
    ├── test/
    └── androidTest/
speaker-core/
 config/models.json
scripts/
├── litert/
├── reporting/
└── windows/
```

**Structure Decision**: 既存の単一AndroidアプリとKotlin/JVMモジュールを維持し、共通ドメインを`app/domain`、端末実装を`app/infrastructure`、変換・レポートを`scripts`へ分離する。新しい永続Repositoryは追加しない。

## Delivery Phases

1. Spec Kit正本・後継範囲・モデル台帳
2. 三層ID・互換表・build flavor・native監査
3. 共通全候補評価・保存方針・インメモリRepository
4. 顔／音声品質と設定移行
5. LiteRT変換、端末runtime、同等性
6. 話者活動推定、局所話者追跡、重複fail-closed、CAM++／ERes2Net／WeSpeaker／SpeakerNet-M／TitaNet-Sの明示ONNX選択
7. UI、詳細計測、レポート、比較
8. candidate収束、ライセンス、Android→Pepper受入

各Phaseはテスト先行で完了条件を満たし、同じファイルを触るタスクは直列化する。

## Complexity Tracking

| Added complexity | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| 複数の顔ランタイム | Pepper上の速度・互換性を実測比較するため | 単一OpenCV経路では改訂計画の比較とLiteRT受入を満たさない |
| 話者活動推定と局所追跡 | 重複・複数話者の特徴量汚染を防ぐため | 単一PCM列を1話者扱いすると誤クラスタ更新が起きる |
| benchmark/candidate flavor | 評価機能と最小配布物を分離するため | 単一APKでは不要資産と詳細ログを候補成果物から除けない |
| 三層モデルID | 変換物とruntimeを追跡しつつ互換特徴量だけ共有するため | 表示名やファイル名だけではクラスタ互換性を保証できない |
