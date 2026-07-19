# Tasks: Face Accuracy and Pepper Performance Evaluation

**Input**: Design documents from `specs/002-face-evaluation/`

Completed work remains checked for repository history. The canonical Notion remaining-work summary shows only unchecked items.

## Phase 1: Setup

- [x] T001 Verify dataset/result/cache ignore rules in `.gitignore`.
- [x] T002 Record BIWI provenance, license, source files, and checksums.
- [x] T003 Record Pointing'04 provenance, license, source files, and checksums.

## Phase 2: Foundational

- [x] T004 Implement shared sample/protocol/trial schemas and leakage validation.
- [x] T005 Implement shared YuNet/SFace/0095 inference and caching.
- [x] T006 Implement FAR/MIR/Accuracy/FRR/EER and pose grouping.
- [x] T007 Add deterministic fixture, leakage, Unknown, margin, and metric tests.

## Phase 3: Dataset identity evaluation

- [x] T008 Implement BIWI discovery, pose parsing, series handling, and manifest generation.
- [x] T009 Implement Pointing'04 discovery, label parsing, and two-series manifest generation.
- [x] T010 Implement front-only and front/left/right enrollment evaluation.
- [ ] T011 Run both face models on full available BIWI and Pointing'04 and write reports.

## Phase 4: Threshold and ambiguity evaluation

- [x] T012 Implement development-only threshold/margin sweeps and final isolation.
- [x] T013 Emit trials, summary, and report artifacts.
- [ ] T014 Verify repeatability and zero split/sample leakage on all full manifests.

## Phase 5: Pepper sustained performance

- [x] T015 Add measurable processed/skipped/frame-total/state-ready counters.
- [x] T016 Implement ADB device/app/event/CPU/memory sampler.
- [x] T017 Implement timing/resource/drop/stall summarizer.
- [x] T018 Run and summarize a Pepper short functional face session.
- [ ] T019 Run and summarize a 30-minute Pepper continuous face session.
- [ ] T020 Run and summarize a Pepper multiple-face session when two faces are available.

## Phase 6: Validation and reporting

- [x] T021 Run evaluator tests, Android unit tests/lint, and verify datasets/results are absent from APK/git.
- [ ] T022 Publish measured dataset and Pepper results plus explicit unavailable metrics.

## Phase 7: Model-independent matching-method comparison

- [ ] T023 Extend the evaluator schema and output with separate `model_id` and `method_id` fields. Support at least `Face-Method-1`, `Face-Method-2`, and `Face-Method-3` without changing model inference.
- [ ] T024 Add tests for L2 normalization, same-pose centroid construction, all-pose centroid construction, the $K_{p,o}=1$ equivalence between `Face-Method-1` and `Face-Method-2`, threshold/margin isolation, and deterministic method selection.
- [ ] T025 Run a controlled model-method matrix. Hold method constant for SFace-versus-0095 comparisons and hold model constant for method comparisons. Re-select threshold and margin for every model-method pair and update `results.md` with separate model and method conclusions.

`Face-Method-4` query-window aggregation is optional until T023-T025 establish whether additional aggregation is justified. If evaluated, add a separately numbered task with same-track-only tests, explicit window/weights, added latency, and Pepper memory cost.

## Dependencies

- T001-T007 block full evaluation.
- T011 depends on available official dataset files.
- T014 depends on full manifests.
- T019 depends on uninterrupted Pepper availability.
- T020 depends on two physically visible faces.
- T023-T024 block T025.
- T022 depends on all obtainable measurements, T025, or explicit documentation that a method comparison is not executable with the available samples.

## Implementation Strategy

Acquire large files first. Preserve deterministic manifests and metric tests. Treat embedding model, enrollment protocol, matching method, threshold, and margin as separate report dimensions. Never convert missing metrics to zero.
