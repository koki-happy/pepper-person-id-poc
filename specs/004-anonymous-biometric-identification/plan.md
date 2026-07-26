# Implementation Plan

> Superseded by
> [`../005-full-person-identification/plan.md`](../005-full-person-identification/plan.md).

- Kotlin, Android API 23+, Compose, CameraX, OpenCV/ML Kit, sherpa-onnx.
- Atomic app-private binary snapshots with Application-scoped repositories.
- Startup StateFlow gates all identification resources until deletion completes on IO.
- Registered-person persistence, coordinators, routes, controls, and tests are removed.
- JVM tests, ARM64 OPPO validation, then Pepper API 23/ARMv7 final acceptance.
