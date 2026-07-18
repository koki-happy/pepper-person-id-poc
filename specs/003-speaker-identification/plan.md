# Implementation Plan: Registered-speaker Identification

**Branch**: `codex/face-identification`  
**Spec**: [`spec.md`](spec.md)

## Summary

Converge the existing speaker path into a constitution-compliant registered-speaker feature. Preserve the working PCM, VAD, embedding, registration, and threshold comparison path; remove anonymous-speaker functionality; add top-two margin; make the 16 kHz boundary explicit; and qualify the models with Japanese speech and Pepper microphone evidence.

## Constitution Check

- **Unknown-first**: Partial. Threshold rejection exists; top-two margin remains.
- **No anonymous identity**: Fail. Anonymous-speaker screen, clusterer, coordinator branch, results, and logs remain.
- **Privacy by Design**: Pass for raw media persistence. PCM/WAV are not stored; embeddings and metadata persist locally.
- **Pepper-first**: Partial. Model and VAD smoke evidence exists; Japanese / Pepper microphone qualification and sustained performance remain.
- **Evidence and Traceability**: Partial. Existing tests cover threshold identification, but the speaker feature was not previously separated into its own Spec Kit artifacts.

## Technical Context

- Input: `AudioRecord`, mono PCM16, 16 kHz preferred, 44.1 kHz fallback
- VAD: Silero VAD at 16 kHz; energy-based fallback otherwise
- Segmentation: start at first speech, complete after 600 ms trailing silence or 10 s total
- Minimum voiced duration: 1,000 ms
- Embedding: sherpa-onnx `SpeakerEmbeddingExtractor`, CPU, one thread
- Models: CAM++ and ERes2Net
- Matching: cosine similarity, per-person maximum registered-template score
- Current decision: threshold only
- Target decision: threshold plus top-two margin
- Persistence: model-separated speaker templates in app-private person profiles
- Acceptance device: Pepper API 23 / ARMv7

## Architecture changes

1. Remove `AnonymousSpeakerIdentification` from `AppScreen` and `MainActivity`.
2. Remove `ANONYMOUS_IDENTIFICATION` from `SpeakerScreenMode`.
3. Remove `AnonymousSpeakerClusterer`, anonymous state, anonymous result UI, reset action, and anonymous benchmark events.
4. Extend `SpeakerIdentityResult` with second score and margin.
5. Extend `SpeakerIdentifier.identify` with `minimumMargin` and sorted candidate handling matching the face identifier contract.
6. Add `speakerMargin` to `PocSettings`, validation, SharedPreferences codec, and settings UI.
7. Reject or resample non-16-kHz utterances before the speaker embedding engine. Initial implementation should reject explicitly unless a resampling design is separately approved.
8. Keep speaker templates separate by person and model; do not introduce average-template persistence.
9. Add structured VAD, segment, embedding, comparison, threshold, second-score, margin, and decision evidence.

## Evaluation plan

1. Define deterministic enrolled/development/final splits for J-SpAW and JVS.
2. Record dataset provenance, license, exclusions, hashes, and sample-rate handling.
3. Evaluate CAM++ and ERes2Net under identical utterance and scoring rules.
4. Sweep threshold and margin on development data only.
5. Report FAR, MIR, Accuracy, FRR, EER, insufficient-audio rate, and processing time.
6. Record Pepper microphone samples without persisting raw PCM/WAV beyond the active processing session.
7. Run short and sustained Pepper sessions, reporting CPU, memory, utterance latency, VAD segmentation delay, embedding time, and decision time.

## Dependency order

1. Constitution alignment and anonymous-path removal
2. Pure domain margin tests and implementation
3. Settings and result-model changes
4. Coordinator/UI integration
5. Sample-rate boundary correction
6. Unit/lint/build validation
7. Japanese dataset evaluation
8. Pepper microphone and sustained performance evidence
9. Final results and model/threshold decision

## Completion boundary

This feature is complete only when anonymous-speaker identity is removed, threshold-plus-margin is implemented and tested, unsupported sample rates are handled explicitly, and Japanese plus Pepper microphone evidence is published. English/Chinese model smoke tests alone are insufficient for final model selection.
