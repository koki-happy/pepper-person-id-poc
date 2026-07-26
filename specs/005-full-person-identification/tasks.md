# Tasks: Pepper匿名人物識別の完全実装

**Input**: Design documents from `specs/005-full-person-identification/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: 本FeatureはTDDを要求する。各ストーリーのdomain/contract testを先に追加し、失敗を確認してから実装する。

**Organization**: タスクはユーザーストーリー単位で構成し、共有基盤はSetup/Foundationalへ置く。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 異なるファイルを変更し、未完了タスクへ依存しない場合に並列実行可能
- **[Story]**: `spec.md`のUser Story

## Phase 1: Setup and source-of-truth

**Purpose**: 正本、後継範囲、生成物境界、検証コマンドを固定する。

- [x] T001 Validate `specs/005-full-person-identification/spec.md` against `checklists/requirements.md` and record no unresolved markers
- [x] T002 [P] Add the `005` supersession note to `specs/001-face-identification/plan.md`
- [x] T003 [P] Add the `005` supersession note to `specs/003-speaker-identification/plan.md`
- [x] T004 [P] Add the `005` supersession note to `specs/004-anonymous-biometric-identification/plan.md`
- [x] T005 [P] Update `data/README.md` with the local-only, per-evaluation input/result policy and the prohibition on dataset bodies in APK/Git
- [x] T006 [P] Add generated LiteRT artifacts, benchmark JSONL, CSV, SVG, Markdown, and device evidence exclusions to `.gitignore`
- [x] T007 Add a `verify005SpecArtifacts` Gradle task in `build.gradle.kts` that checks required `005` files and contracts exist

**Checkpoint**: `005` is the sole active source of truth and generated biometric/evaluation data remains local-only.

---

## Phase 2: Foundational domain, catalog, and session storage

**Purpose**: すべてのストーリーを支える三層ID、全候補評価、保存方針、セッションRepository、台帳を実装する。

**⚠️ CRITICAL**: このPhaseが完了するまで、顔・話者・LiteRT・比較UIを実装しない。

### Tests first

- [x] T008 [P] Add model ID and compatibility validation tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/model/ModelCatalogTest.kt`
- [x] T009 [P] Add deterministic all-candidate ordering and tie-break tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/anonymous/IdentificationEvaluatorTest.kt`
- [x] T010 [P] Add threshold, one-candidate lead, ambiguous, unknown, and non-finite tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/anonymous/IdentificationDecisionTest.kt`
- [x] T011 [P] Add create/update/hold policy and stricter-update tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/anonymous/PersistencePolicyTest.kt`
- [x] T012 [P] Add HOLD mutation-invariance and modality/model-space separation tests in `app/src/test/java/com/example/pepper_person_id_poc/infrastructure/repository/InMemoryAnonymousClusterRepositoryTest.kt`
- [x] T013 [P] Add session lifecycle and legacy-file deletion instrumentation tests in `app/src/androidTest/java/com/example/pepper_person_id_poc/SessionAnonymousClusterLifecycleTest.kt`
- [x] T014 [P] Add schema-v2 catalog parser/hash/reference tests in `app/src/test/java/com/example/pepper_person_id_poc/infrastructure/model/JsonModelCatalogRepositoryTest.kt`

### Implementation

- [x] T015 [P] Add `ModelSpaceId`, `ArtifactId`, and `RuntimeId` value types in `app/src/main/java/com/example/pepper_person_id_poc/domain/model/ModelIdentifiers.kt`
- [x] T016 [P] Add `ModelArtifactRecord`, `ConversionRecord`, and license fields in `app/src/main/java/com/example/pepper_person_id_poc/domain/model/ModelArtifactRecord.kt`
- [x] T017 [P] Add `CompatibilityStatus` and `ModelRuntimeCompatibility` in `app/src/main/java/com/example/pepper_person_id_poc/domain/model/ModelRuntimeCompatibility.kt`
- [x] T018 [P] Add candidate/evaluation/decision types from the contract in `app/src/main/java/com/example/pepper_person_id_poc/domain/anonymous/IdentificationEvaluation.kt`
- [x] T019 [P] Add `PersistencePolicyResult` and `PersistenceOperation` in `app/src/main/java/com/example/pepper_person_id_poc/domain/anonymous/PersistencePolicy.kt`
- [x] T020 Implement stable all-candidate scoring, lead, and decisions in `app/src/main/java/com/example/pepper_person_id_poc/domain/anonymous/IdentificationEvaluator.kt`
- [x] T021 Implement pure create/update/hold selection in `app/src/main/java/com/example/pepper_person_id_poc/domain/anonymous/AnonymousPersistencePolicy.kt`
- [x] T022 Refactor repository contracts to separate evaluate and apply operations in `app/src/main/java/com/example/pepper_person_id_poc/application/contract/AnonymousClusterRepositories.kt`
- [x] T023 Implement application-scoped face and speaker repositories in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/repository/InMemoryAnonymousClusterRepositories.kt`
- [x] T024 Remove production biometric serialization and retain only legacy-file deletion in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/repository/FileAnonymousClusterRepositories.kt`
- [x] T025 Wire in-memory repositories and startup legacy cleanup in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/AppContainer.kt`
- [x] T026 Make explicit exit clear both in-memory repositories and legacy files in `app/src/main/java/com/example/pepper_person_id_poc/MainActivity.kt`
- [x] T027 Expand `config/models.json` to schema v2 with face, speaker, VAD, segmentation, runtime, compatibility, hash, conversion, and license records
- [x] T028 Implement schema-v2 parsing and validation in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/model/JsonModelCatalogRepository.kt`
- [x] T029 Add `verifyModelCatalog` and artifact hash verification tasks in `app/build.gradle.kts`
- [x] T030 Migrate face and speaker coordinators to the new evaluate/policy/apply contracts in `app/src/main/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinator.kt` and `app/src/main/java/com/example/pepper_person_id_poc/application/speaker/SpeakerIdentityCoordinator.kt`
- [ ] T031 Run `:app:testDebugUnitTest` and `:app:connectedDebugAndroidTest` for all foundational contracts and update T008-T014 with any missing boundary cases

**Checkpoint**: 候補評価は決定的で、HOLDは不変、匿名特徴量はファイルへ書かれない。

---

## Phase 3: User Story 1 - 匿名顔を継続識別する (Priority: P1) 🎯 Face MVP

**Goal**: 複数顔、全候補、品質評価、作成・更新・保留を一つのリアルタイム顔フローで成立させる。

**Independent Test**: Android端末で一人・複数人・低品質顔を提示し、全候補、5点、品質理由、匿名ID再利用、HOLD不変を確認する。

### Tests first

- [x] T032 [P] [US1] Add face quality boundary tests for size, blur, brightness, clipping, edge truncation, pose, landmarks, and track duration in `app/src/test/java/com/example/pepper_person_id_poc/domain/face/FaceQualityPolicyTest.kt`
- [x] T033 [P] [US1] Add nullable ML Kit confidence tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/face/FaceQualityAssessmentTest.kt`
- [x] T034 [P] [US1] Add multi-face all-candidate and reserved-track behavior tests in `app/src/test/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinatorTest.kt`
- [x] T035 [P] [US1] Add low-quality HOLD repository-invariance instrumentation in `app/src/androidTest/java/com/example/pepper_person_id_poc/AnonymousFaceQualityTest.kt`
- [x] T036 [P] [US1] Add face preview contract tests for complete candidate virtualization, IDs, quality, and timings in `app/src/androidTest/java/com/example/pepper_person_id_poc/AnonymousFaceUiTest.kt`

### Implementation

- [x] T037 [P] [US1] Add `FaceQualityAssessment` and thresholds in `app/src/main/java/com/example/pepper_person_id_poc/domain/face/FaceQualityAssessment.kt`
- [x] T038 [P] [US1] Add blur, brightness, clipping, and edge measurements in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/FaceImageQualityAnalyzer.kt`
- [x] T039 [US1] Track face duration and landmark completeness in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/FaceTracker.kt`
- [x] T040 [US1] Replace fabricated ML Kit confidence with null/N/A in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/MlKitFaceDetector.kt`
- [x] T041 [US1] Preserve detector-provided confidence and input coordinates in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/YuNetFaceDetector.kt`
- [x] T042 [US1] Integrate face quality before persistence while retaining candidate display in `app/src/main/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinator.kt`
- [x] T043 [US1] Remove same-frame candidate omission and return every compatible cluster in `app/src/main/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinator.kt`
- [x] T044 [US1] Add virtualized complete candidate rows, rank, lead, quality, decision, and operation in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt`
- [x] T045 [US1] Add model-space/artifact/runtime IDs and N/A measurement rendering in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt`
- [ ] T046 [US1] Run JVM, UI, and fixed-image instrumentation tests for US1 and record evidence in `specs/005-full-person-identification/quickstart.md`

**Checkpoint**: OpenCV/ML Kitの現行顔フローが新契約で動作し、低品質入力がクラスタを汚染しない。

---

## Phase 4: User Story 2 - 匿名話者を継続識別する (Priority: P1)

**Goal**: 16 kHz固定、話者活動・重複推定、局所話者追跡、単独区間だけの匿名話者識別を成立させる。

**Independent Test**: Android端末で同一話者、別話者、交互発話、部分重複、完全重複、無音を入力し、適切な匿名IDまたはHOLDを確認する。

### Tests first

- [x] T047 [P] [US2] Add strict 16 kHz initialization and no-fallback tests in `app/src/test/java/com/example/pepper_person_id_poc/infrastructure/audio/AndroidPcmAudioRecorderTest.kt`
- [x] T048 [P] [US2] Add audio quality boundary tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/speaker/SpeakerAudioQualityPolicyTest.kt`
- [x] T049 [P] [US2] Add speaker activity output decoding tests in `app/src/test/java/com/example/pepper_person_id_poc/infrastructure/speaker/PyannoteSegmentationPostprocessorTest.kt`
- [x] T050 [P] [US2] Add local track linking tests for swaps, disappearance, reappearance, overlap, and ties in `app/src/test/java/com/example/pepper_person_id_poc/domain/speaker/LocalSpeakerTrackLinkerTest.kt`
- [x] T051 [P] [US2] Add solo-segment extraction and fail-closed overlap tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/speaker/SoloSpeakerSegmentSelectorTest.kt`
- [x] T052 [P] [US2] Add local-speaker aggregation and all-candidate tests in `app/src/test/java/com/example/pepper_person_id_poc/application/speaker/SpeakerIdentityCoordinatorTest.kt`
- [x] T053 [P] [US2] Add live 16 kHz recorder/VAD instrumentation in `app/src/androidTest/java/com/example/pepper_person_id_poc/StrictSpeakerAudioPipelineTest.kt`
- [x] T054 [P] [US2] Add fixed alternating/partial-overlap/full-overlap instrumentation assets and tests in `app/src/androidTest/java/com/example/pepper_person_id_poc/SpeakerActivityPipelineTest.kt`
- [x] T055 [P] [US2] Add speaker UI contract tests for activity, overlap, quality, candidates, RTF, and HOLD in `app/src/androidTest/java/com/example/pepper_person_id_poc/AnonymousSpeakerUiTest.kt`

### Implementation

- [x] T056 [US2] Remove 44.1 kHz and Energy VAD fallback from `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/audio/AndroidPcmAudioRecorder.kt`
- [x] T057 [P] [US2] Add structured 16 kHz initialization diagnostics in `app/src/main/java/com/example/pepper_person_id_poc/domain/speaker/AudioCaptureInitialization.kt`
- [x] T058 [P] [US2] Add `SpeakerAudioQualityAssessment` and policy in `app/src/main/java/com/example/pepper_person_id_poc/domain/speaker/SpeakerAudioQualityAssessment.kt`
- [x] T059 [P] [US2] Add activity/window/segment/track entities in `app/src/main/java/com/example/pepper_person_id_poc/domain/speaker/SpeakerActivityModels.kt`
- [x] T060 [US2] Add speaker segmentation runtime interface in `app/src/main/java/com/example/pepper_person_id_poc/application/contract/SpeakerSegmentationEngine.kt`
- [x] T061 [US2] Implement pyannote ONNX CPU inference without fallback in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/speaker/PyannoteSegmentationOnnxEngine.kt`
- [x] T062 [US2] Implement powerset/activity/overlap postprocessing in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/speaker/PyannoteSegmentationPostprocessor.kt`
- [x] T063 [US2] Implement cross-window local speaker tracking in `app/src/main/java/com/example/pepper_person_id_poc/domain/speaker/LocalSpeakerTrackLinker.kt`
- [x] T064 [US2] Implement solo-segment selection and unsupported-state HOLD in `app/src/main/java/com/example/pepper_person_id_poc/domain/speaker/SoloSpeakerSegmentSelector.kt`
- [x] T065 [US2] Implement segment-level embedding aggregation in `app/src/main/java/com/example/pepper_person_id_poc/domain/speaker/LocalSpeakerEmbeddingAggregator.kt`
- [x] T066 [US2] Integrate VAD, activity, tracking, quality, aggregation, evaluation, and policy in `app/src/main/java/com/example/pepper_person_id_poc/application/speaker/SpeakerIdentityCoordinator.kt`
- [x] T067 [US2] Render capture diagnostics, activity, overlap, local tracks, quality, candidates, RTF, and HOLD reasons in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/AudioRecordingScreen.kt`
- [x] T068 [US2] Add pyannote asset/provenance/license/compatibility records to `config/models.json`
- [x] T069 [US2] Run JVM, fixed-audio, and live-mic Android tests for US2 and record evidence in `specs/005-full-person-identification/quickstart.md`

**Checkpoint**: 複数話者や重複を1話者として保存せず、単独区間だけが匿名クラスタへ到達する。

---

## Phase 5: User Story 7 - Android端末からPepperへ段階受入する (Priority: P1)

**Goal**: 同一シナリオのAndroid先行を機械的に保証し、Pepper証跡を別に記録する。

**Independent Test**: Android pass manifestなしでPepper runnerが停止し、pass後は同じscenario IDsだけを実行する。

### Tests first

- [x] T070 [P] [US7] Add acceptance manifest schema tests in `scripts/acceptance/tests/AcceptanceManifest.Tests.ps1`
- [x] T071 [P] [US7] Add Android-pass prerequisite and serial/ABI/API guard tests in `scripts/acceptance/tests/Invoke-DeviceAcceptance.Tests.ps1`
- [x] T072 [P] [US7] Add privacy file-scan and crash-log parser tests in `scripts/acceptance/tests/DeviceEvidence.Tests.ps1`

### Implementation

- [x] T073 [P] [US7] Define scenario IDs and required evidence fields in `scripts/acceptance/scenarios.json`
- [x] T074 [US7] Implement read-only device preflight and exact serial classification in `scripts/acceptance/Get-DevicePreflight.ps1`
- [x] T075 [US7] Implement guarded build/install/launch/functional evidence runner in `scripts/acceptance/Invoke-DeviceAcceptance.ps1`
- [x] T076 [US7] Implement package/activity/permission/crash/privacy evidence capture in `scripts/acceptance/Get-DeviceEvidence.ps1`
- [x] T077 [US7] Add 15/30/60-minute bounded soak collection in `scripts/acceptance/Invoke-SoakTest.ps1`
- [x] T078 [US7] Add Android-pass manifest verification before Pepper execution in `scripts/acceptance/Assert-AndroidPassBeforePepper.ps1`
- [x] T079 [US7] Document human-required face-present and speech-present checkpoints in `specs/005-full-person-identification/quickstart.md`

**Checkpoint**: 自動化はインストール・起動・機能・安定性を混同せず、Pepper開始前にAndroid合格を要求する。

---

## Phase 6: User Story 3 - モデルと実行基盤を明示選択する (Priority: P2)

**Goal**: モデル、資産、runtimeを独立選択し、互換表にない組合せを実行させない。

**Independent Test**: 旧設定移行、有効組合せ、BLOCKED組合せ、ロード失敗を検証し、暗黙フォールバック0件を確認する。

### Tests first

- [x] T080 [P] [US3] Add detector-model/runtime and embedding-model/runtime compatibility tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/config/PocSettingsCompatibilityTest.kt`
- [x] T081 [P] [US3] Add explicit settings schema migration tests in `app/src/test/java/com/example/pepper_person_id_poc/infrastructure/repository/SharedPreferencesSettingsRepositoryTest.kt`
- [x] T082 [P] [US3] Add unsupported/missing artifact UI tests in `app/src/androidTest/java/com/example/pepper_person_id_poc/ModelSelectionCompatibilityTest.kt`
- [x] T083 [P] [US3] Add no-runtime-fallback failure tests in `app/src/androidTest/java/com/example/pepper_person_id_poc/InferenceRuntimeFailureTest.kt`

### Implementation

- [x] T084 [US3] Split detector model/runtime and embedding model/runtime enums in `app/src/main/java/com/example/pepper_person_id_poc/domain/config/PocSettings.kt`
- [x] T085 [US3] Set new-install defaults to YuNet FP32/OpenCV, SFace FP32/OpenCV, CAM++ Chinese-English/sherpa, and Silero VAD in `app/src/main/java/com/example/pepper_person_id_poc/domain/config/PocSettings.kt`
- [x] T086 [US3] Implement versioned settings migration without changing valid saved choices in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/repository/SharedPreferencesSettingsRepository.kt`
- [x] T087 [US3] Resolve selectable combinations from the catalog and build contents in `app/src/main/java/com/example/pepper_person_id_poc/application/config/ModelSelectionCoordinator.kt`
- [x] T088 [US3] Disable BLOCKED/UNSUPPORTED combinations and show reasons in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/ModelSelectionScreen.kt`
- [x] T089 [US3] Refactor face engine factories to require exact artifact/runtime pairs in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/FaceEmbeddingEngineFactory.kt`
- [x] T090 [US3] Refactor detector factories to require exact model/runtime pairs in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/FaceDetectorFactory.kt`
- [ ] T091 [US3] Run migration, selection, and no-fallback tests and record the supported matrix in `specs/005-full-person-identification/research.md`
- [x] T155 [P] [US3] Add pure exact-pair ABI policy tests for ONNX Runtime Android 1.20.0 in `app/src/test/java/com/example/pepper_person_id_poc/domain/config/FaceEmbeddingRuntimeCompatibilityTest.kt`
- [x] T156 [P] [US3] Add ARM64 SFace/0095 ONNX Runtime instrumentation smoke coverage in `app/src/androidTest/java/com/example/pepper_person_id_poc/OnnxRuntimeFaceEmbeddingSmokeTest.kt`
- [x] T157 [US3] Align face runtime enum and catalog records to packaged ONNX Runtime Android 1.20.0 with ARM64 `BUILDABLE` and ARMv7 `BLOCKED`
- [x] T158 [US3] Enforce exact ARM64 SFace/0095 ONNX pairs in `FaceEmbeddingEngineFactory` and `OnnxRuntimeFaceEmbeddingEngine` without fallback

**Checkpoint**: 設定と実際のruntimeが一致し、利用不能な組合せを選択できない。

---

## Phase 7: User Story 4 - 変換モデルの同等性を検証する (Priority: P2)

**Goal**: YuNet/SFace LiteRT変換を再現し、元runtimeと同等性を証明してから同じモデル空間を共有する。

**Independent Test**: 固定環境で同一hashを再生成し、固定入力で検出・特徴量・判定の比較結果を得る。

### Tests first

- [x] T092 [P] [US4] Add conversion manifest schema/hash tests in `scripts/litert/tests/test_manifest.py`
- [x] T093 [P] [US4] Add YuNet output/postprocess golden tests in `scripts/litert/tests/test_yunet_equivalence.py`
- [x] T094 [P] [US4] Add SFace preprocessing/output/cosine golden tests in `scripts/litert/tests/test_sface_equivalence.py`
- [x] T095 [P] [US4] Add LiteRT asset load/inference/resource-close Android tests in `app/src/androidTest/java/com/example/pepper_person_id_poc/LiteRtModelSmokeTest.kt`
- [ ] T096 [P] [US4] Add cross-runtime SFace/0095 agreement tests in `app/src/androidTest/java/com/example/pepper_person_id_poc/FaceRuntimeEquivalenceTest.kt`

### Implementation

- [x] T097 [P] [US4] Pin the conversion environment in `scripts/litert/Dockerfile` and `scripts/litert/requirements.lock`
- [x] T098 [P] [US4] Implement immutable-input conversion and manifest generation in `scripts/litert/convert_models.py`
- [x] T099 [P] [US4] Implement tensor/operator inspection in `scripts/litert/inspect_model.py`
- [x] T100 [US4] Implement YuNet detection/postprocessing equivalence comparison in `scripts/litert/compare_yunet.py`
- [x] T101 [US4] Implement SFace preprocessing/embedding/decision equivalence comparison in `scripts/litert/compare_sface.py`
- [x] T102 [US4] Add LiteRT dependency/source-set configuration with exact version and ABI gate in `app/build.gradle.kts`
- [x] T103 [US4] Implement CPU-only LiteRT tensor/runtime adapter in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/litert/LiteRtRuntime.kt`
- [x] T104 [US4] Implement YuNet LiteRT detector and postprocessor in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/LiteRtYuNetFaceDetector.kt`
- [x] T105 [US4] Implement SFace LiteRT embedding engine in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/LiteRtSFaceEmbeddingEngine.kt`
- [x] T106 [US4] Register converted artifact hashes, tensor metadata, warnings, and provisional compatibility in `config/models.json`
- [x] T107 [US4] Run conversion twice and store only public manifest/policy artifacts in `docs/model-provenance.md`
- [x] T108 [US4] Run ARM64 LiteRT smoke/equivalence before marking any pair VERIFIED in `config/models.json`
- [ ] T109 [US4] Run Pepper API23/ARMv7 LiteRT smoke/equivalence before sharing the source modelSpaceId in `config/models.json`
- [x] T159 [US4] Separate logical face embedding models from runtime-specific artifacts and resolve the exact packaged artifact without fallback in `app/src/main/java/com/example/pepper_person_id_poc/domain/config/FaceEmbeddingArtifactResolver.kt`
- [x] T160 [P] [US4] Convert face-reidentification-retail-0095 ONNX FP32 to LiteRT in the pinned environment, record a reproducible manifest/hash/tensor contract, and add the artifact to `config/models.json`
- [x] T161 [US4] Implement the exact face-reidentification-retail-0095 LiteRT preprocessing/output adapter and fixed-image OpenCV/ORT/LiteRT equivalence test in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/`
- [x] T162 [P] [US4] Convert YuNet 2026may FP32 to ncnn and MNN with pinned tool revisions, manifests, hashes, tensor names, and decoded-output equivalence in `scripts/native-face/`
- [x] T163 [P] [US4] Implement YuNet 2026may ONNX Runtime detector inference and YuNet decode/NMS equivalence without OpenCV inference fallback in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/`
- [x] T164 [US4] Implement YuNet ncnn and MNN JNI detector sessions with all output tensors and shared decode/NMS equivalence in `app/src/main/cpp/yunet_native_detector_jni.cpp`
- [x] T165 [US4] Expose YuNet OpenCV/ONNX Runtime/ncnn/MNN/LiteRT as independent runtime choices through an exact detector-artifact resolver in `app/src/main/java/com/example/pepper_person_id_poc/domain/config/`
- [ ] T166 [P] [US4] Add fixed-image Android load/infer/release and no-fallback tests for YuNet OpenCV/ONNX Runtime/ncnn/MNN/LiteRT in `app/src/androidTest/java/com/example/pepper_person_id_poc/FaceDetectorRuntimeMatrixTest.kt`
- [ ] T167 [P] [US4] Decide whether SFace INT8 is a public logical model across ONNX Runtime/ncnn/MNN/LiteRT only after exact converted artifacts and fixed-image decision agreement exist, and encode every accepted pair in `config/models.json`
- [ ] T168 [US4] Run the complete logical face model by runtime matrix on ARM64 Android and then Pepper ARMv7, recording exact artifact/runtime IDs and forbidding fallback in `specs/005-full-person-identification/quickstart.md`

### Speaker model matrix completion

- [x] T169 [P] [US3] Add exact SpeakerNet-M and TitaNet-S model ID, filename, and embedding-dimension contracts in `app/src/test/java/com/example/pepper_person_id_poc/domain/config/PocSettingsCompatibilityTest.kt`
- [x] T170 [US3] Package immutable SpeakerNet-M and TitaNet-S ONNX artifacts with pinned revision, SHA-256, tensor/preprocessing metadata, and license evidence through `scripts/setup-local-inference-assets.ps1` and `config/models.json`
- [x] T171 [US3] Expose SpeakerNet-M and TitaNet-S as explicit settings choices and route them through `SherpaOnnxSpeakerEmbeddingEngine` using their exact model IDs and dimensions without fallback
- [x] T172 [US3] Record sherpa-onnx 1.13.4 `framework=nemo` ARM64/ARMv7 compatibility for SpeakerNet-M and TitaNet-S in `config/models.json`
- [x] T173 [P] [US3] Add fixed-PCM Android ONNX load/infer/release coverage for both NeMo models in `app/src/androidTest/java/com/example/pepper_person_id_poc/NemoSpeakerModelsOnnxSmokeTest.kt`
- [x] T174 [US5] Keep SpeakerNet-M and TitaNet-S in the JVS benchmark and require both in Windows/Android parity synchronization through `config/speaker-benchmark-jvs.json` and `scripts/windows/verify-speaker-parity.ps1`
- [ ] T175 [US7] Run SpeakerNet-M and TitaNet-S fixed-PCM instrumentation on the ARM64 Android device, collect parity JSON, and only then record Android acceptance in `specs/005-full-person-identification/quickstart.md`

**Checkpoint**: LiteRT可否が変換成功ではなく、数値同等性と実機runtime証跡で決まる。

---

## Phase 8: User Story 5 - 実機性能と精度を比較する (Priority: P2)

**Goal**: 全候補を同じ条件で比較し、ライブグラフと再生成可能なレポートを提供する。

**Independent Test**: 固定JSONLからCSV/SVG/Markdownを再生成し、同じ入力で同じ統計と警告を得る。

### Tests first

- [x] T110 [P] [US5] Add nullable metric and run-metadata serialization tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/metrics/BenchmarkEventTest.kt`
- [x] T111 [P] [US5] Add ring-buffer gap/p50/p95/max tests in `app/src/test/java/com/example/pepper_person_id_poc/domain/metrics/MetricSeriesTest.kt`
- [x] T112 [P] [US5] Add face stage timing tests in `app/src/test/java/com/example/pepper_person_id_poc/application/face/FacePipelineMetricsTest.kt`
- [x] T113 [P] [US5] Add speaker stage timing and RTF tests in `app/src/test/java/com/example/pepper_person_id_poc/application/speaker/SpeakerPipelineMetricsTest.kt`
- [x] T114 [P] [US5] Add JSONL parser/statistics/mixed-condition warning tests in `scripts/reporting/tests/test_report.py`
- [x] T115 [P] [US5] Add graph render and missing-gap Compose tests in `app/src/androidTest/java/com/example/pepper_person_id_poc/MetricsGraphTest.kt`

### Implementation

- [x] T116 [P] [US5] Expand typed metric/run/scenario models in `app/src/main/java/com/example/pepper_person_id_poc/domain/metrics/BenchmarkEvent.kt`
- [x] T117 [P] [US5] Implement bounded metric series and statistics in `app/src/main/java/com/example/pepper_person_id_poc/domain/metrics/MetricSeries.kt`
- [x] T118 [US5] Add face preprocessing/alignment/embedding/scoring/policy/repository/UI timings in `app/src/main/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinator.kt`
- [x] T119 [US5] Add recorder/VAD/activity/tracking/embedding/aggregation/scoring/policy/repository timings and RTF in `app/src/main/java/com/example/pepper_person_id_poc/application/speaker/SpeakerIdentityCoordinator.kt`
- [x] T120 [US5] Extend device monitor with Java/native heap, threads, and collection timestamps in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/device/AndroidDeviceLoadMonitor.kt`
- [x] T121 [US5] Enable complete JSONL only for benchmark and explicit candidate export in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/metrics/JsonLinesBenchmarkLogger.kt`
- [x] T122 [P] [US5] Implement 60/300-second Compose Canvas graph in `app/src/main/java/com/example/pepper_person_id_poc/ui/component/MetricHistoryGraph.kt`
- [x] T123 [US5] Integrate stage values, statistics, gaps, and overhead toggle in `app/src/main/java/com/example/pepper_person_id_poc/ui/component/DeviceLoadPanel.kt`
- [x] T124 [P] [US5] Implement JSONL to CSV/SVG/Markdown reports in `scripts/reporting/generate_report.py`
- [x] T125 [US5] Add benchmark scenario/configuration UI in `app/src/main/java/com/example/pepper-person-id-poc/ui/screen/BenchmarkScreen.kt`
- [ ] T126 [US5] Run fixed comparisons across implemented runtimes and record measured/unmeasured fields in local ignored `results/`

**Checkpoint**: 比較条件と測定欠損が明示され、同じJSONLから全レポートが再生成できる。

---

## Phase 9: User Story 6 - 配布候補を安全に生成する (Priority: P3)

**Goal**: benchmark/candidateを分離し、native・資産・サイズ・ライセンスを機械監査する。

**Independent Test**: 両APKを展開し、含有資産/runtime/ABIがallowlistどおりで、candidateに非採用候補がないことを確認する。

### Tests first

- [x] T127 [P] [US6] Add flavor configuration assertions in `app/src/test/java/com/example/pepper_person_id_poc/build/BuildFlavorContractTest.kt`
- [x] T128 [P] [US6] Add APK inventory parser and duplicate native hash tests in `scripts/apk-audit/tests/ApkAudit.Tests.ps1`
- [x] T129 [P] [US6] Add API23 symbol allowlist and ORT collision tests in `scripts/apk-audit/tests/NativeCompatibility.Tests.ps1`
- [x] T130 [P] [US6] Add full catalog license gate tests in `buildSrc/src/test/kotlin/ModelLicenseGateTest.kt`

### Implementation

- [x] T131 [US6] Add `benchmark` and `candidate` product flavors and application ID suffixes in `app/build.gradle.kts`
- [x] T132 [US6] Move full comparison assets/dependencies to benchmark source sets in `app/src/benchmark/` and selected assets to `app/src/candidate/`
- [x] T133 [US6] Generate candidate asset/runtime allowlists from `config/models.json` in `buildSrc/src/main/kotlin/ModelCatalogPlugin.kt`
- [x] T134 [US6] Replace hardcoded speaker-only licensing with full included-artifact validation in `buildSrc/src/main/kotlin/ModelLicenseGate.kt`
- [x] T135 [US6] Gate every candidate variant, including debug, in `app/build.gradle.kts`
- [x] T136 [P] [US6] Implement APK/native/model inventory and size report in `scripts/apk-audit/Invoke-ApkAudit.ps1`
- [x] T137 [P] [US6] Implement duplicate `libc++_shared`, ORT collision, ABI, and API23 symbol audit in `scripts/apk-audit/Test-NativeCompatibility.ps1`
- [x] T138 [US6] Add `auditBenchmarkDebugApk` and `auditCandidateDebugApk` Gradle tasks in `app/build.gradle.kts`
- [x] T139 [US6] Add distribution manifest generation with APK/hash/contents/licenses in `scripts/apk-audit/New-DistributionManifest.ps1`
- [ ] T140 [US6] Verify candidate excludes nonselected models/AAR/.so and compare sizes in `specs/005-full-person-identification/quickstart.md`

**Checkpoint**: candidateは採用候補だけを含み、ライセンス・ARMv7・API23監査を回避できない。

---

## Phase 10: Final integration, convergence, and acceptance

**Purpose**: 全ストーリーを統合し、Android端末→Pepperの順で受入証跡を完成させる。

- [ ] T141 [P] Run all JVM and Android unit tests with `:speaker-core:test :speaker-benchmark:test :app:testBenchmarkDebugUnitTest :app:testCandidateDebugUnitTest`
- [ ] T142 [P] Run model catalog, license, APK inventory, native coexistence, conversion manifest, and report generator verification tasks
- [ ] T143 Run all fixed-image and fixed-audio instrumentation on the ARM64 Android device
- [ ] T144 Run `A-DIAG-001`, `A-FACE-001..004`, `A-SPK-001..004`, and `A-PRIV-001` with face/speech present on the ARM64 Android device
- [ ] T145 Run `A-SOAK-015`, `A-SOAK-030`, and `A-SOAK-060` and generate the Android pass manifest
- [ ] T146 Select the final face detector, face embedding runtime, speaker model, thresholds, and candidate contents from measured Android evidence in `docs/poc/final-recommendation.md`
- [ ] T147 Build and audit the ARMv7 candidate APK only after T144-T146 pass
- [ ] T148 Install and launch the candidate APK on Pepper and separately record package, activity, permissions, runtime initialization, and crash evidence
- [ ] T149 Run `P-DIAG-001`, `P-FACE-001..004`, `P-SPK-001..004`, and `P-PRIV-001` with face/speech present on Pepper
- [ ] T150 Run `P-SOAK-015`, `P-SOAK-030`, and `P-SOAK-060` and generate Pepper reports
- [ ] T151 Re-run license and distribution gates against the exact accepted candidate APK
- [ ] T152 Update `specs/005-full-person-identification/tasks.md` checkboxes and append authoritative acceptance evidence links to `specs/005-full-person-identification/quickstart.md`
- [ ] T153 Update `README.md` only with behavior verified in the accepted code and preserve explicit Pepper acceptance boundaries
- [ ] T154 Run `openwiki --update` only after the implementation is merged, keeping uncommitted behavior out of merged-code documentation

---

## Dependencies & Execution Order

### Phase dependencies

```text
Phase 1 Source of truth
  -> Phase 2 Foundation
     -> Phase 3 Face
     -> Phase 4 Speaker
     -> Phase 5 Acceptance tooling
     -> Phase 6 Settings/compatibility
        -> Phase 7 LiteRT/equivalence
           -> Phase 8 Metrics/comparison
              -> Phase 9 Packaging/license
                 -> Phase 10 Android then Pepper acceptance
```

### User story dependencies

- **US1 Face**: Phase 2のみへ依存。
- **US2 Speaker**: Phase 2とpyannote資産/runtime台帳へ依存。
- **US7 Acceptance tooling**: Phase 2のID/証跡契約へ依存し、他ストーリーと並行実装可能。
- **US3 Settings**: Phase 2のcatalog/compatibilityへ依存。
- **US4 Conversion**: US3の明示runtime選択へ依存。
- **US5 Metrics**: US1、US2、US4の実行段階へ依存。
- **US6 Packaging**: catalog、runtime、metrics、比較結果へ依存。

### Parallel opportunities

- T008-T014、T032-T036、T047-T055、T070-T072、T080-T083、T092-T096、T110-T115、T127-T130は各グループ内で並列実行可能。
- Phase 3（顔）、Phase 4（話者）、Phase 5（受入tooling）はPhase 2後に別ファイルで並列化可能。
- 変換Python toolchainとAndroid runtime adapterは契約確定後に並列化可能。
- metrics domain/report generatorとCompose graphはイベント契約確定後に並列化可能。

## Implementation Strategy

### First executable increment

1. T001-T007で正本を固定。
2. T008-T031で全候補、HOLD、インメモリRepository、台帳を完成。
3. T032-T046で現在動作している顔フローを新契約へ移行。
4. Android端末で顔MVPを確認してから話者・LiteRTへ進む。

### Incremental delivery

- 各CheckpointでJVM、instrumentation、実機のどこまで通ったかを分離して記録する。
- 外部資産、ライセンス、API23/ARMv7で成立しないruntimeは`BLOCKED`のまま残し、代替へ黙って切り替えない。
- Android端末の完全合格前にPepper受入を開始しない。
- 明示的に対象外とした履歴、統合・分離、顔声リンク、特徴量永続保存を追加しない。

## Format validation

- 全175タスクが`- [ ] Tnnn [P?] [US?] Description with file path`形式。
- Setup/Foundational/FinalにはStory labelを付けず、User Story phaseだけ`[USn]`を付与。
- テストタスクは対応実装より前に配置。
# Confirmed UI and Logcat simplification

- [x] T194 [US5] Replace synchronous app-private JSONL event writes with structured Logcat output in `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/benchmark/JsonLinesBenchmarkLogger.kt` and `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/metrics/JsonLinesBenchmarkLogger.kt`
- [x] T195 [US1] Reduce the face identification screen to the selected anonymous ID, decision, similarity, quality reason, error, CPU, PSS, total processing time, and analysis FPS in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt`
- [x] T196 [US2] Reduce the speaker identification screen to recording controls, selected anonymous ID, decision, similarity, quality reason, error, CPU, PSS, total processing time, and RTF in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/AudioRecordingScreen.kt`
- [x] T197 [US5] Remove metric histories, graphs, statistics, window selectors, and measurement toggle from `app/src/main/java/com/example/pepper_person_id_poc/ui/component/DeviceLoadPanel.kt`
- [ ] T198 Validate focused unit tests and the ARMv7 debug build, then record Logcat capture evidence in `specs/005-full-person-identification/quickstart.md`
- [x] T199 [US3] Restrict speaker model selection to WeSpeaker ResNet34-LM ONNX and CAM++ Chinese-English ONNX on sherpa-onnx CPU in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/ModelSelectionScreen.kt`
- [x] T200 [US2] Keep live speaker identification on Silero VAD and sherpa-onnx by replacing the conflicting Pyannote ONNX Runtime segmentation session with VAD-gated single-speaker segmentation
- [x] T201 [US1] Remove the retired YuNet 2023mar INT8 detector option and migrate persisted selections to YuNet 2026may FP32
