# Tasks: Registered-speaker Identification

**Input**: [`spec.md`](spec.md) and [`plan.md`](plan.md)

Only remaining work is listed. Existing PCM capture, Silero VAD, utterance segmentation, CAM++ / ERes2Net extraction, raw multiple-template storage, and threshold-based `Voice-Method-1` are current implementation.

## Phase 1: Constitution alignment

- [x] T001 Remove `AnonymousSpeakerIdentification` from navigation and `MainActivity`.
- [ ] T002 Remove anonymous screen mode, coordinator branches, UI state, reset actions, and benchmark events.
- [ ] T003 Delete `AnonymousSpeakerClusterer`, anonymous result models, and tests.
- [ ] T004 Verify no anonymous speaker ID or cluster remains in source, tests, UI, or logs.

## Phase 2: Matching-method domain boundary

- [ ] T005 Add pure domain tests for `Voice-Method-1`: maximum per-person template score, invalid dimensions, empty templates, and deterministic best candidate.
- [ ] T006 Add pure domain tests for `Voice-Method-2`: per-template L2 normalization, centroid construction, centroid re-normalization, zero-vector rejection, one-template equivalence, and deterministic scoring.
- [ ] T007 Introduce a model-independent `SpeakerTemplateScorer` or equivalent strategy boundary with explicit `methodId`.
- [ ] T008 Implement `Voice-Method-1` and `Voice-Method-2` without changing raw template persistence.
- [ ] T009 Add centroid invalidation or recomputation when a template is added, deleted, or the selected model changes.

## Phase 3: Threshold plus top-two margin

- [ ] T010 Add tests for best score, second score, margin acceptance, insufficient margin, one-candidate handling, and `Unknown` for both methods.
- [ ] T011 Extend `SpeakerIdentityResult` with `secondScore`, `margin`, `minimumMargin`, `modelId`, `methodId`, and template count.
- [ ] T012 Implement sorted per-person candidates and threshold-plus-margin decision.
- [ ] T013 Add `speakerMethod` and `speakerMargin` to validated settings, persistence, ViewModel, and UI.
- [ ] T014 Log model, method, template count, best score, second score, margin, threshold, minimum margin, and decision.

## Phase 4: Audio input boundary

- [ ] T015 Add tests proving speaker embedding accepts 16 kHz and explicitly rejects unsupported sample rates.
- [ ] T016 Prevent 44.1 kHz fallback utterances from reaching the 16-kHz-only embedding path silently; reject actionably unless resampling is separately approved.
- [ ] T017 Add UI and benchmark evidence for sample rate, VAD model, total duration, voiced duration, and insufficient-audio reason.

## Phase 5: Japanese method evaluation

- [ ] T018 Record J-SpAW and JVS provenance, license, hashes, sample discovery, exclusions, and deterministic split rules.
- [ ] T019 Extend the evaluator to consume stored embeddings and evaluate `Voice-Method-1` and `Voice-Method-2` with separate `model_id` and `method_id` fields.
- [ ] T020 Add leakage, repeatability, metric, insufficient-audio, template-count-sensitivity, and ambiguous-candidate tests.
- [ ] T021 For a fixed model, run development threshold/margin sweeps and isolated final evaluation for both methods.
- [ ] T022 Report FAR, MIR, Accuracy, FRR, EER, insufficient-audio rate, score distributions, template counts, and processing time for each method.

## Phase 6: Japanese model evaluation

- [ ] T023 Fix the selected method and compare CAM++ with ERes2Net under identical splits, templates, probes, and parameter-search rules.
- [ ] T024 Report model conclusions separately from method conclusions. Do not select a model from a comparison that changed method.
- [ ] T025 Evaluate `Voice-Method-3` only if Method-2 leaves a documented problem and a reproducible quality weight is defined.

## Phase 7: Pepper microphone evidence

- [ ] T026 Run registered-speaker functional validation on Pepper microphone input for both models using the selected method.
- [ ] T027 Run a sustained Pepper session; report CPU, memory growth, VAD delay, utterance duration, embedding time, aggregation time, comparison time, stalls, and errors.
- [ ] T028 Compare Pepper microphone behavior with dataset/file input and document microphone-specific degradation.

## Phase 8: Convergence

- [ ] T029 Select the PoC speaker model, matching method, threshold, and margin from Japanese and Pepper evidence.
- [ ] T030 Publish final results with measured values, unavailable metrics, exclusions, and external blockers.
- [ ] T031 Run unit tests, lint, ARM64 build, ARMv7 build, and Pepper installation/operation checks.

## Dependencies

- T001-T004 block constitution compliance.
- T005-T009 block method comparison and settings integration.
- T010-T014 block final Unknown behavior.
- T015-T017 block reliable Pepper input.
- T018-T022 block matching-method selection.
- T023-T025 block model selection.
- T026-T028 block Pepper acceptance.
- T029-T031 depend on all directly obtainable evidence or explicit blocker documentation.
