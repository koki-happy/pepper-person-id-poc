# Implementation Plan: Face Accuracy and Pepper Performance Evaluation

**Branch**: `codex/face-identification` | **Date**: 2026-07-18 | **Spec**: [spec.md](spec.md)

## Summary

Add a reproducible offline evaluator for BIWI and Pointing'04 that uses the same YuNet, SFace, 0095,
threshold, and top-two-margin semantics as the app. Add a Pepper collection/report workflow that
combines structured app events with adb CPU/memory samples, then record measured and unavailable
metrics explicitly.

## Technical Context

**Language/Version**: Python 3.11 evaluation CLI; existing Kotlin/Android app instrumentation

**Primary Dependencies**: OpenCV 5 Python/runtime, ONNX Runtime, NumPy, existing Android OpenCV 5

**Storage**: External ignored dataset bodies; versioned manifests and summary reports only

**Testing**: Python unittest/fixture smoke, leakage validation, Android unit/lint, adb device runs

**Target Platform**: Windows evaluator host; Pepper Android 6.0/API 23/armeabi-v7a acceptance

**Project Type**: Android app plus offline evaluation and device-collection scripts

**Performance Goals**: Complete deterministic full-corpus scoring; collect 30-minute Pepper run with
P95 stage timings, resource samples, drops, stalls, and update frequency

**Constraints**: Offline CPU inference; about 1 GB Pepper RAM; datasets not in APK/git; Unknown-first;
no captured media persistence; unavailable metrics must remain unavailable rather than zero

**Scale/Scope**: BIWI full RGB corpus, Pointing'04 full corpus, two face models, two enrollment
protocols, threshold/margin sweep, one/multiple-face Pepper runs

## Constitution Check

- **Unknown-first**: PASS. Evaluation includes threshold and top-two margin, prioritizing FAR and
  misidentification before accuracy/FRR/EER.
- **Privacy by Design**: PASS. Public research datasets stay outside APK/git; app saves no camera media.
- **Pepper-first**: PASS. Pepper report contains direct API23/ARMv7 measurements and separates gaps.
- **Evidence and Traceability**: PASS. Manifests, checksums, trials, summaries, tasks, and paths link.

Post-design re-check: PASS. Dataset licenses/provenance and unavailable device metrics are explicit.

## Project Structure

```text
specs/002-face-evaluation/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/face-evaluation-cli.md
├── quickstart.md
└── tasks.md

scripts/face-evaluation/
├── evaluate.py
├── datasets.py
├── inference.py
├── metrics.py
└── tests/

scripts/windows/
├── collect-pepper-face-performance.ps1
└── summarize-pepper-face-performance.ps1

datasets/                         # ignored bodies
evaluation-results/face/          # ignored generated trials, summaries, and device runs
config/face-evaluation/            # versioned protocols/manifests without dataset bodies
```

**Structure Decision**: Keep the production app Kotlin-first. Use the already-provisioned Python
conversion environment for offline corpus scoring, and PowerShell/adb for Windows-hosted Pepper
collection. Reuse app structured timing events instead of adding a second inference implementation
to the Pepper APK.

## Complexity Tracking

No constitution violations.
