# Tasks: Face Accuracy and Pepper Performance Evaluation

**Input**: Design documents from `specs/002-face-evaluation/`

## Phase 1: Setup

- [x] T001 Verify dataset/result/cache ignore rules in .gitignore
- [x] T002 [P] Record BIWI provenance, license, source files, and checksums in docs/evaluation-datasets.md
- [x] T003 [P] Record Pointing'04 provenance, license, source files, and checksums in docs/evaluation-datasets.md

## Phase 2: Foundational

- [x] T004 Implement shared sample/protocol/trial schemas and leakage validation in scripts/face-evaluation/datasets.py
- [x] T005 Implement shared YuNet/SFace/0095 inference and caching in scripts/face-evaluation/inference.py
- [x] T006 Implement FAR/misidentification/accuracy/FRR/EER and pose grouping in scripts/face-evaluation/metrics.py
- [x] T007 Add deterministic fixture, leakage, Unknown, margin, and metric tests in scripts/face-evaluation/tests/

## Phase 3: User Story 1 - Dataset identity evaluation

- [x] T008 [US1] Implement BIWI discovery, pose parsing, series handling, and manifest generation in scripts/face-evaluation/datasets.py
- [x] T009 [US1] Implement Pointing'04 discovery, label parsing, and two-series manifest generation in scripts/face-evaluation/datasets.py
- [x] T010 [US1] Implement front-only and front/left/right enrollment evaluation in scripts/face-evaluation/evaluate.py
- [ ] T011 [US1] Run both models/protocols on full BIWI and Pointing'04 and write evaluation-results/face dataset reports

## Phase 4: User Story 2 - Threshold and ambiguity evaluation

- [x] T012 [US2] Implement development-only threshold/margin sweeps and final isolation in scripts/face-evaluation/evaluate.py
- [x] T013 [US2] Emit trials.csv, summary.json, and report.md with overall, pose, series, and protocol deltas
- [ ] T014 [US2] Verify repeatability and zero split/sample leakage on both full manifests

## Phase 5: User Story 3 - Pepper sustained performance

- [x] T015 [US3] Add measurable processed/skipped/frame-total/state-ready counters to app face telemetry
- [x] T016 [US3] Implement adb device/app/event/CPU/memory sampler in scripts/windows/collect-pepper-face-performance.ps1
- [x] T017 [US3] Implement timing/resource/drop/stall summarizer in scripts/windows/summarize-pepper-face-performance.ps1
- [x] T018 [US3] Run and summarize a Pepper short functional face session
- [ ] T019 [US3] Run and summarize a 30-minute Pepper continuous face session
- [ ] T020 [US3] Run and summarize a Pepper multiple-face session when two faces are available

## Phase 6: Validation and reporting

- [x] T021 Run evaluator tests, Android unit tests/lint, and verify datasets/results are absent from APK/git
- [ ] T022 Publish measured dataset and Pepper results plus explicit unavailable metrics in specs/002-face-evaluation/results.md

## Dependencies

- T001-T007 block full evaluation.
- T008-T014 may execute while Pepper T015-T020 progresses.
- T011 depends on completed downloads; T019 depends on uninterrupted Pepper availability.
- T022 depends on all measurements or documented external blockers.

## Convergence audit (2026-07-18)

No duplicate convergence tasks were appended. The remaining work is already represented by T011
(full BIWI run is blocked upstream), T014 (BIWI repeatability/leakage evidence), T019 (30-minute
Pepper run), T020 (two physically visible faces), and T022 (final measured report).

## Implementation Strategy

Acquire large files first. Complete deterministic manifests and metric tests before full inference.
Run short Pepper evidence before the continuous run. Never convert missing metrics to zero.
