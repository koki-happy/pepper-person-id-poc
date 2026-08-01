# Tasks: 識別ダッシュボードと設定画面分離

## Phase 1: Setup

- [x] T001 Bundle the two sample MP4 assets and configure no-compress packaging in `app/src/main/assets/videos/` and `app/build.gradle.kts`
- [x] T002 Force MainActivity landscape orientation in `app/src/main/AndroidManifest.xml`

## Phase 2: Foundational

- [x] T003 Add multiple-sample policy persistence and defaults in `app/src/main/java/com/example/pepper_person_id_poc/domain/config/PocSettings.kt` and `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/repository/SharedPreferencesSettingsRepository.kt`
- [x] T004 Add model-runtime set option mapping in `app/src/main/java/com/example/pepper_person_id_poc/domain/config/ModelRuntimeSetOption.kt`
- [x] T005 Add reusable muted sample video player in `app/src/main/java/com/example/pepper_person_id_poc/ui/component/SampleVideoPlayer.kt`

## Phase 3: User Story 1 - 起動直後に顔識別を開始する

- [x] T006 [US1] Change initial navigation and back behavior in `app/src/main/java/com/example/pepper_person_id_poc/ui/state/MainUiState.kt` and `app/src/main/java/com/example/pepper_person_id_poc/MainActivity.kt`
- [x] T007 [US1] Add startup and navigation unit coverage in `app/src/test/java/com/example/pepper_person_id_poc/ui/`

## Phase 4: User Story 2 - 顔と音声の識別状況を切り替える

- [x] T008 [US2] Rebuild the face screen as the compact landscape dashboard in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt`
- [x] T009 [US2] Rebuild the speaker screen as the matching compact landscape dashboard in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/AudioRecordingScreen.kt`
- [x] T010 [US2] Integrate face-speaker navigation and sample video controls in `app/src/main/java/com/example/pepper_person_id_poc/MainActivity.kt`

## Phase 5: User Story 3 - 識別パラメータを設定する

- [x] T011 [US3] Replace the menu-style settings UI with compact parameter rows, Japanese tap help, and multiple-sample toggle in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/SettingsScreen.kt`
- [x] T012 [US3] Apply multiple-sample policy to face and speaker coordinators in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt` and `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/AudioRecordingScreen.kt`

## Phase 6: User Story 4 - モデル構成を設定する

- [x] T013 [US4] Replace independent model/runtime controls with four valid-set dropdowns in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/ModelSelectionScreen.kt`
- [x] T014 [US4] Save complete model/runtime selections through `app/src/main/java/com/example/pepper_person_id_poc/ui/viewmodel/MainViewModel.kt`

## Phase 7: User Story 5 - セッションを初期化する

- [x] T015 [US5] Add in-dashboard confirmed session reset without deleting saved settings in `app/src/main/java/com/example/pepper_person_id_poc/MainActivity.kt`

## Phase 8: Validation

- [x] T016 Run unit tests and ARM64 assemble with `.\gradlew.bat testBenchmarkDebugUnitTest assembleBenchmarkDebug -PtargetAbi=arm64-v8a`
- [x] T017 Implement horizontally scrollable audio level timeline and grouped performance/result tables, manual-scroll pause, time units, top-three ID/similarity cells, mode-count agreement, and current/average/maximum performance rows
- [x] T018 Run unit tests and build the ARM64 smartphone APK
- [x] T019 Validate landscape layout, timeline, tables, settings, and both sample videos on smartphone `192.168.10.111:42095`
- [x] T020 Build ARMv7 APK only after smartphone acceptance with `.\gradlew.bat assembleBenchmarkDebug -PtargetAbi=armeabi-v7a`
- [ ] T021 Install and validate the same layout and core flows on Pepper `192.168.10.106:5555`
- [x] T022 [US3] Add all basic and detailed parameter fields, bounds, model-dependent defaults, and schema migration in `app/src/main/java/com/example/pepper_person_id_poc/domain/config/PocSettings.kt` and `app/src/main/java/com/example/pepper_person_id_poc/infrastructure/repository/SharedPreferencesSettingsRepository.kt`
- [x] T023 [US3] Add category navigation, compact editors, conditional YuNet/ML Kit rows, and Japanese tap help in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/SettingsScreen.kt`
- [x] T024 [US3] Wire saved settings into face detection/tracking and speaker VAD/segmentation/quality/tracking in `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt` and `app/src/main/java/com/example/pepper_person_id_poc/ui/screen/AudioRecordingScreen.kt`
- [x] T025 [US3] Add default, bounds, migration, and runtime-construction tests under `app/src/test/java/com/example/pepper_person_id_poc/`
- [x] T026 [US2] Sort face and speaker identification-result rows by each label's latest judgment time, with numeric label order as the tie-breaker, in `app/src/main/java/com/example/pepper_person_id_poc/ui/component/IdentificationHistoryTable.kt`
- [x] T027 [US2] Add a persisted single-choice load-test video option with OFF as the default and render at most one player
- [x] T028 [US2] Convert the audio graph display scale from -90..0 dBFS to positive 0..100 percent without changing internal audio measurements
- [x] T029 [US2] Compact performance cells, move RTF after device load, and keep units in headers instead of data cells
- [x] T030 Validate the new video option, positive graph scale, and compact performance cells on the ARM64 smartphone
- [ ] T031 Rebuild, install, photograph, and validate the same changes on Pepper
- [x] T033 Increase face and speaker identification history to 50 entries and label the tables as the latest 50 face frames and latest 50 utterances
- [x] T034 Label the second RTF header row as speaker-identification total divided by audio length
- [x] T035 Rename the RTF denominator and analyzed utterance duration column to input audio length while retaining continuous input recording length separately
- [x] T036 Keep the RTF formula exclusively in the second header row inside the performance table

## Dependencies

- T001-T005 block UI integration.
- T006-T007 complete startup behavior.
- T008-T010 complete the primary dashboard.
- T011-T015 complete settings and reset behavior.
- T022-T025 must complete before repeating T018-T019; T019 must pass before T020-T021.

## Implementation Strategy

Implement the shared platform and settings model first, then face startup/dashboard as the MVP. Match the speaker dashboard next, finish both setting screens, validate on the ARM64 smartphone, and only then build/install the ARMv7 Pepper APK.
