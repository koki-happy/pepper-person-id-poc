# Tasks: Registered-speaker Identification

**Input**: [`spec.md`](spec.md) and [`plan.md`](plan.md)

Only remaining work is listed. Existing PCM capture, Silero VAD, utterance segmentation, CAM++ / ERes2Net extraction, multiple-template storage, and threshold-based identification are documented as current implementation rather than duplicated as open tasks.

## Phase 1: Constitution alignment

- [ ] T001 Remove `AnonymousSpeakerIdentification` from navigation and `MainActivity`.
- [ ] T002 Remove `ANONYMOUS_IDENTIFICATION`, anonymous coordinator branches, anonymous UI state, reset actions, and anonymous benchmark events.
- [ ] T003 Delete `AnonymousSpeakerClusterer`, anonymous result models, and their tests.
- [ ] T004 Verify that no anonymous speaker ID or cluster string remains in source, tests, UI, or logs.

## Phase 2: Threshold plus top-two margin

- [ ] T005 Add failing `SpeakerIdentifier` tests for best score, second score, margin acceptance, insufficient margin, one-candidate handling, invalid dimensions, and `Unknown`.
- [ ] T006 Extend `SpeakerIdentityResult` with `secondScore`, `margin`, and `minimumMargin`.
- [ ] T007 Implement sorted per-person candidates and threshold-plus-margin decision in `SpeakerIdentifier`.
- [ ] T008 Add `speakerMargin` to `PocSettings`, validation ranges, persistence codec, ViewModel updates, and settings UI.
- [ ] T009 Log best score, second score, margin, threshold, minimum margin, model, and decision for each utterance.

## Phase 3: Audio input boundary

- [ ] T010 Add tests proving that speaker embedding accepts 16 kHz and explicitly rejects unsupported sample rates.
- [ ] T011 Prevent 44.1 kHz fallback utterances from reaching the 16-kHz-only embedding path silently; reject with an actionable state unless a separate resampling design is approved.
- [ ] T012 Add UI and benchmark evidence for sample rate, VAD model, total duration, voiced duration, and insufficient-audio reason.

## Phase 4: Japanese evaluation

- [ ] T013 Record J-SpAW and JVS provenance, license, hashes, sample discovery, exclusions, and deterministic split rules.
- [ ] T014 Implement or extend the evaluation runner for CAM++ and ERes2Net using the same segmentation, template aggregation, threshold, and margin equations as the Android feature.
- [ ] T015 Add leakage, repeatability, metric, insufficient-audio, and ambiguous-candidate tests.
- [ ] T016 Run development threshold/margin sweeps and isolated final evaluation for both models.
- [ ] T017 Report FAR, MIR, Accuracy, FRR, EER, insufficient-audio rate, and processing-time distributions.

## Phase 5: Pepper microphone evidence

- [ ] T018 Run registered-speaker functional validation on Pepper microphone input for both models.
- [ ] T019 Run a sustained Pepper recording and identification session; report CPU, memory growth, VAD delay, utterance duration, embedding time, comparison time, stalls, and errors.
- [ ] T020 Compare Pepper microphone behavior with dataset/file input and document microphone-specific degradation.

## Phase 6: Convergence

- [ ] T021 Select the PoC speaker model, threshold, and margin from Japanese and Pepper evidence; do not select from English/Chinese smoke tests alone.
- [ ] T022 Publish final results with measured values, unavailable metrics, exclusions, and remaining external blockers.
- [ ] T023 Run unit tests, lint, ARM64 build, ARMv7 build, and Pepper installation/operation checks.

## Dependencies

- T001-T004 block constitution compliance.
- T005-T009 block final identification behavior.
- T010-T012 block reliable Pepper microphone input.
- T013-T017 block model and threshold selection.
- T018-T020 block Pepper acceptance.
- T021-T023 depend on all directly obtainable evidence or explicit blocker documentation.
