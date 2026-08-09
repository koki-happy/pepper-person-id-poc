# Tasks: Android face/speaker reference dashboard

## Phase 1 — specification and state boundary

- [x] T001 Add the dashboard display model/mapper for face rows, speaker rows, overlap
  relation and average-only metrics under `app/src/main/java/com/example/pepper_person_id_poc/ui/state/`.
- [x] T002 Extend `MainUiState`/navigation so the combined dashboard is the identification
  route and settings/model screens return to it.

## Phase 2 — combined live dashboard

- [x] T003 Refactor `CameraPreviewScreen` into the HTML three-panel layout: summary cards,
  face result panel, speaker result panel, camera panel and speaker panel.
- [x] T004 Render permission-denied camera/audio states as `—`/`未検出`, clear stale camera
  frames, and keep face/speaker controls independent.
- [x] T005 Add bounded display-only face/speaker histories and an elapsed-time event log;
  include relation success/failure and multi-speaker handling without persistent mapping.
- [x] T006 Replace the performance panel with one table of average CPU, memory, face total
  processing time and speaker total processing time; keep stage data out of the reference UI.
- [x] T007 Make the waveform width-fill and rolling, using actual recorder level; use gray
  for silence, black for multi-speaker and stable color per single-speaker label.

## Phase 3 — settings/model parity

- [x] T008 Ensure `SettingsScreen` exposes all implemented parameters and model/runtime
  choices using existing Android controls, validates values and applies them on next start.
- [x] T009 Add any dashboard-only thresholds (overlap 0.60 and display classifications)
  to persisted settings only when they are real runtime parameters; otherwise keep them as
  presentation constants and document the distinction.

## Phase 4 — verification

- [ ] T010 Add unit/Compose tests for average metrics, threshold classifications, permission
  denial, multi-speaker relation suppression and bounded history.
- [x] T011 Run `:app:compileBenchmarkDebugKotlin` and relevant tests; fix regressions without
  overwriting unrelated dirty changes.
- [x] T012 Install/smoke-test the APK on an available Android target and record Pepper
  acceptance separately if the physical device is online.

## Phase 5: Convergence

- [x] T013 Implement an ephemeral face-speaker relation calculator that intersects the
  speaker utterance interval with face-track intervals, selects the longest overlap and
  breaks ties by face similarity per FR-014/US5-AC1/US5-AC2 (missing).
- [x] T014 Wire the relation into the live dashboard and event log, suppressing face↔speaker
  mapping for multi-speaker, unavailable, and below-threshold cases per FR-015/FR-016/FR-019
  (partial).
- [x] T015 Add unit tests for longest-overlap selection, similarity tie-breaking, threshold
  suppression, and multi-speaker/unavailable states per SC-005 (missing).
