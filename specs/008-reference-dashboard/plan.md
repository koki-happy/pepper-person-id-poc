# Implementation Plan: Android face/speaker reference dashboard

**Branch**: `codex/008-reference-dashboard` | **Date**: 2026-08-09 | **Spec**: [spec.md](./spec.md)

## Summary

Replace the current split face and speaker screens with one Jetpack Compose dashboard
that follows `docs/current-screen.html`. Face camera and speaker status are visible
together, both pipelines are independently switchable, result histories and event logs
are display-only, and the performance table shows one average-only value per implemented
metric. Existing CameraX, face, PCM/VAD/Segmentation 3.0 and speaker coordinators remain
the source of runtime data; this work adds a UI adapter, not a second inference pipeline.

## Technical Context

**Language/Version**: Kotlin/JVM with Jetpack Compose Material 3

**Primary Dependencies**: CameraX, Android audio capture, existing face/speaker
coordinators, SharedPreferences settings repository, model catalog and Logcat benchmark logger

**Storage**: Existing anonymous session repositories and persisted settings. UI histories,
event rows and temporary face↔speaker correspondence are memory-only and reset on reset,
screen recreation/session exit; no biometric history is persisted.

**Testing**: Gradle unit tests, Compose/instrumentation smoke tests where available,
`:app:compileBenchmarkDebugKotlin`, and APK install/screenshot checks on Android. Physical
Pepper acceptance is reported separately when the device is online.

**Target Platform**: Android API 23+, Pepper landscape (armeabi-v7a), and landscape phone/tablet

**Project Type**: Android mobile application

**Performance Goals**: Keep camera/audio inference off the main thread; render a bounded,
width-filling dashboard without unbounded history; show measured face/speaker totals and
device averages without adding synchronous file writes to the pipeline.

**Constraints**: Offline-capable, anonymous/session-only identity, 640×480 4:3 preview,
permission-denied results are `—`/`未検出` with no fabricated boxes, multi-speaker windows
show `複数話者` and do not map face↔speaker IDs, settings apply at the next identification
start, and only loadable model/runtime combinations may be selected.

**Scale/Scope**: One existing activity, one dashboard route, existing settings/model screens,
display-only rows up to the visible viewport, no server/API contract.

## Constitution Check

* Pass: retain anonymous IDs and no persistent face↔speaker identity link.
* Pass: no new network or biometric persistence; use existing repositories and Logcat metrics.
* Pass: the temporary overlap correspondence is explicitly session-scoped UI data only,
  never written to disk and never treated as a named identity link.
* Pass: Pepper ARMv7/API 23 remains a target; host build is not physical acceptance.

## Project Structure

```text
app/src/main/java/com/example/pepper_person_id_poc/
├── MainActivity.kt
├── ui/state/MainUiState.kt
├── ui/viewmodel/MainViewModel.kt
├── ui/screen/CameraPreviewScreen.kt       # combined dashboard route
├── ui/component/ScrollableDataTable.kt
├── domain/config/PocSettings.kt            # persisted, editable parameters
└── application/{face,speaker}/             # existing inference coordinators
app/src/test/java/com/example/pepper_person_id_poc/
└── ...                                     # state/metric mapping tests
specs/008-reference-dashboard/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md
```

**Structure Decision**: Keep the existing single-activity Compose architecture. The
existing `CameraPreviewScreen` already owns both CameraX and audio lifecycles, so it is
extended as the one dashboard instead of creating parallel pipelines. Settings remain in
`SettingsScreen`/`PocSettings`; the dashboard consumes their saved values on the next start.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Temporary face↔speaker correspondence in the dashboard | The HTML explicitly shows overlap and relation failure/success for the current session | Persisting or globally linking identities would violate the anonymous/session-only boundary; UI state is memory-only and discarded on reset/exit |
