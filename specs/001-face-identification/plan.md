# Implementation Plan: Guided Face Registration and Real-time Enrolled-person Identification

> Superseded for active anonymous face behavior by
> [`../005-full-person-identification/plan.md`](../005-full-person-identification/plan.md).

**Branch**: `codex/face-identification` | **Date**: 2026-07-18 | **Spec**: [spec.md](spec.md)

## Summary

Use a two-stage camera path: lightweight face detection plus pose estimation for guided registration,
followed by embedding extraction only for an accepted registration pose. On the identification screen,
extract all visible face tracks at the configured interval and compare each against enrolled profiles.
Store front, left, and right embeddings per person, apply threshold plus top-two margin, remove only
anonymous face UI/cluster paths, and validate first on Nothing Phone (3a), then Pepper ARMv7.

## Technical Context

**Language/Version**: Kotlin/JVM 11 bytecode, Android application

**Primary Dependencies**: Jetpack Compose, CameraX, OpenCV 5.0.0 Android AAR, YuNet 2026may,
SFace 2021dec, converted face-reidentification-retail-0095 ONNX

**Storage**: Android `AtomicFile` binary person profiles and SharedPreferences settings; no images

**Testing**: JUnit4/Truth unit tests, AndroidX instrumentation, adb device smoke tests

**Target Platform**: Acceptance on Android 6.0/API 23/armeabi-v7a Pepper; development reference on
arm64-v8a Nothing Phone (3a)

**Project Type**: Single Android mobile application with domain/application/infrastructure/UI layers

**Performance Goals**: Registration pose updates at 200 ms; enrolled-person identification updates at
the configured 1,000 ms default; per-stage durations are logged for later P95 analysis

**Constraints**: Offline CPU inference, about 1 GB Pepper RAM, no captured media persistence, existing
Kotlin/OpenCV architecture, preserve unrelated in-progress speaker work

**Scale/Scope**: One active camera, one visible face for registration, multiple visible faces for
identification, and 1-to-N local profiles per detected track; face MVP only

## Constitution Check

- **Unknown-first**: PASS. Domain result requires threshold and margin; no anonymous cluster path.
- **Privacy by Design**: PASS for PoC. Only embeddings/metadata persist; production encryption remains
  a separately declared release gate.
- **Pepper-first**: PASS. Default build ABI remains ARMv7 and Pepper validation is explicit.
- **Evidence and Traceability**: PASS. Tests/tasks map to FRs and implementation uses a work branch.

Post-design re-check: PASS. The UI contract, data model, and quickstart retain all four gates.

## Project Structure

### Documentation (this feature)

```text
specs/001-face-identification/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/face-ui-contract.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/java/com/example/pepper_person_id_poc/
├── domain/face/              # pose, stability and matching rules
├── domain/config/            # bounded face settings
├── application/face/         # guided registration and real-time multi-track identification
├── infrastructure/face/      # YuNet landmarks, solvePnP, embeddings
├── infrastructure/repository/# settings and profile persistence
└── ui/screen/                # camera guidance and controls

app/src/test/java/com/example/pepper_person_id_poc/
├── domain/face/
├── domain/config/
└── application/face/
```

**Structure Decision**: Extend the existing layered Android application. Do not introduce a new
module or native bridge; domain timing and matching remain pure Kotlin where possible.

## Complexity Tracking

No constitution violations or additional project structures are required.
