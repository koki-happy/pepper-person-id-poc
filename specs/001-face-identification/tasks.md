# Tasks: Guided Face Registration and Real-time Enrolled-person Identification

**Input**: Design documents from `specs/001-face-identification/`

**Tests**: Domain and coordinator changes use test-first coverage; Android/device checks validate runtime integration.

## Phase 1: Setup and specification

- [x] T001 Create the approved face feature specification and requirements checklist in specs/001-face-identification/
- [x] T002 Create the technical plan, research, data model, UI contract, and quickstart in specs/001-face-identification/
- [x] T003 Establish the project constitution in .specify/memory/constitution.md

## Phase 2: Foundational face rules

- [x] T004 [P] Add failing head-pose range, smoothing, and timed-stability tests for FR-002..004 in app/src/test/java/com/example/pepper_person_id_poc/domain/face/HeadPoseGuidanceTest.kt
- [x] T005 [P] Add failing threshold plus top-two margin tests for FR-006 in app/src/test/java/com/example/pepper_person_id_poc/domain/face/FaceIdentifierTest.kt
- [x] T006 Implement head-pose value, target range, smoother, and timed stability rules for FR-002..004 in app/src/main/java/com/example/pepper_person_id_poc/domain/face/HeadPoseGuidance.kt
- [x] T007 Implement best/second candidate scores and minimum-margin acceptance for FR-006 in app/src/main/java/com/example/pepper_person_id_poc/domain/face/FaceIdentifier.kt and FaceIdentityResult.kt

## Phase 3: User Story 1 - Guided three-pose registration (P1)

**Goal**: Persist front, left, and right embeddings only after continuous target-pose holds.

**Independent Test**: Interrupted holds save nothing; a complete ordered session saves exactly three samples.

- [x] T008 [US1] Add guided registration, cancellation, and atomic replacement tests for FR-001..005 and FR-019..020 in app/src/test/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinatorTest.kt
- [x] T009 [US1] Add pose observations and guided registration state machine for FR-001..005 in app/src/main/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinator.kt
- [x] T010 [US1] Extract YuNet landmarks, use five-point solvePnP, keep detected face count independent, smooth by track, and gate embeddings for FR-005 in app/src/main/java/com/example/pepper_person_id_poc/infrastructure/face/YuNetFaceDetector.kt
- [x] T011 [US1] Add front/left/right guidance, cancel, and progress UI for FR-001..004 and FR-020 in app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt

## Phase 4: User Story 2 - Real-time multi-person enrolled identification (P1)

**Goal**: Every current face track continuously receives an enrolled-person or Unknown result.

**Independent Test**: Multiple known/unknown faces update independently and stale tracks are cleared.

- [x] T012 [US2] Add real-time multi-track refresh tests for FR-006..007 and FR-021 in app/src/test/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinatorTest.kt
- [x] T013 [US2] Implement periodic all-track enrolled-person comparison for FR-006..007 in app/src/main/java/com/example/pepper_person_id_poc/application/face/FaceIdentityCoordinator.kt
- [x] T014 [US2] Add real-time multi-person result UI and remove only anonymous-face navigation/code for FR-008 and FR-016 in app/src/main/java/com/example/pepper_person_id_poc/ui/screen/CameraPreviewScreen.kt, MainActivity.kt, and ui/navigation/AppScreen.kt

## Phase 5: User Story 3 - Settings and deletion (P1)

**Goal**: Persist bounded face settings and retain explicit full deletion.

**Independent Test**: Valid values round-trip, invalid combinations fail validation, and delete-all removes profiles.

- [x] T015 [P] [US3] Add face setting validation tests for FR-009 in app/src/test/java/com/example/pepper_person_id_poc/domain/config/PocSettingsTest.kt
- [x] T016 [US3] Add face margin, intervals, stable time, pose ranges, and smoothing settings for FR-009 in app/src/main/java/com/example/pepper_person_id_poc/domain/config/PocSettings.kt and infrastructure/repository/SharedPreferencesSettingsRepository.kt
- [x] T017 [US3] Add face controls, privacy explanation, person deletion, and full profile/log deletion for FR-009 and FR-017..018 in app/src/main/java/com/example/pepper_person_id_poc/ui/screen/SettingsScreen.kt, CameraPreviewScreen.kt, and ui/viewmodel/MainViewModel.kt

## Phase 6: Validation and evidence

- [x] T018 Run all app unit tests, lint, and the arm64/ARMv7 debug builds using gradle/wrapper/gradle-wrapper.jar
- [x] T019 Install and smoke-test guided registration, both models, live identification, and stale-result clearing on Nothing Phone (3a) using specs/001-face-identification/quickstart.md
- [x] T020 Install the ARMv7 APK on Pepper and record installed, runtime-working, and app-operable evidence in specs/001-face-identification/quickstart.md
- [x] T021 Confirm no captured media is persisted and update implementation status in specs/001-face-identification/spec.md

## Dependencies & Execution Order

- T004-T007 establish pure face rules and block registration/identification integration.
- US1 and US2 share the coordinator and detector, so execute T008-T014 sequentially.
- US3 may start after the domain shape is stable; UI integration follows coordinator integration.
- Device validation follows all code and unit build tasks.

## Implementation Strategy

Complete the face-only MVP through T021. BIWI/Pointing'04 performance evaluation and all speaker
implementation remain explicitly deferred; their contracts are fixed in FR-014/FR-015 and the supplied
canonical specification, but they are not tasks in this implementation pass.
