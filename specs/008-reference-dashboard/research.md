# Research: Android reference dashboard

## Existing runtime boundaries

- `CameraPreviewScreen` already creates and closes CameraX, face detection/embedding,
  PCM recording, VAD and speaker coordination. Reusing this route prevents duplicate
  microphone/camera consumers and preserves current model-selection behavior.
- `FaceIdentityUiState` exposes current face results, observation count and pipeline
  timings. `SpeakerIdentityUiState` exposes activity state, local speaker tracks,
  results, overlap ratio and stage timings. Both are sufficient for a display-only
  dashboard history.
- `PocSettings` and `SharedPreferencesSettingsRepository` already persist the editable
  face, VAD, segmentation and speaker parameters. `MainViewModel.saveSettings()` validates
  the selected model/runtime set before saving.

## Decisions

1. Keep one combined screen and make `CameraPreviewScreen` the dashboard route.
2. Keep face and speaker ON/OFF state local to the dashboard; permission denial leaves
   the panel visible and renders `—`/`未検出`, never synthetic face boxes.
3. Capture bounded display snapshots in Compose memory. Render as many rows as fit the
   current panel and drop rows on reset/exit; do not write UI history to disk.
4. Show only average CPU, memory, face total processing time and speaker total processing
   time in the metrics table. Detailed stage timing remains available to Logcat/benchmark,
   not the reference dashboard.
5. Use local similarity legends: face `<0.60 / 0.60–0.69 / ≥0.70`, speaker
   `<0.75 / 0.75–0.84 / ≥0.85`. This is a presentation rule; the actual matching thresholds
   remain editable `PocSettings` values.
6. Display the overlap percentage and relation status only for the current session. A
   single speaker with overlap ≥0.60 may show a temporary relation; multi-speaker or
   below-threshold windows show `複数話者`/`対応なし` and do not map IDs.

## Risks and mitigations

- Recomposition can accidentally restart inference: keep coordinators/recorders under
  `remember` keyed by persisted model parameters and close them in `DisposableEffect`.
- A permission-denied camera must not retain a stale frame: clear/unbind the controller
  and gate overlays on the permission state.
- A waveform must not grow forever: use the measured available width and a bounded rolling
  sample list; silent samples are gray and labelled `無音`.
