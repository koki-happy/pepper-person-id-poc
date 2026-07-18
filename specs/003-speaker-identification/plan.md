# Implementation Plan: Registered-speaker Identification

**Branch**: `codex/face-identification`  
**Spec**: [`spec.md`](spec.md)

## Summary

Converge the existing speaker path into a constitution-compliant registered-speaker feature. Preserve working PCM, VAD, embedding, and raw-template storage; remove anonymous-speaker functionality; add top-two margin; make the 16 kHz boundary explicit; implement model-independent matching strategies; and select the model, method, threshold, and margin from Japanese and Pepper microphone evidence.

## Constitution Check

- **Unknown-first**: Partial. Threshold rejection exists; margin remains.
- **No anonymous identity**: Fail. Anonymous-speaker path remains.
- **Privacy by Design**: Pass for raw media persistence. PCM/WAV are not stored.
- **Pepper-first**: Partial. Model/VAD smoke evidence exists; microphone qualification and sustained performance remain.
- **Evidence and Traceability**: Partial. Model and method have not previously been separated in reports.

## Technical Context

- Input: `AudioRecord`, mono PCM16, 16 kHz preferred, 44.1 kHz fallback
- VAD: Silero VAD at 16 kHz; energy-based fallback otherwise
- Minimum voiced duration: 1,000 ms
- Models: `Voice-Model-1` CAM++ and `Voice-Model-2` ERes2Net
- Current method: `Voice-Method-1`, per-person maximum registered-template cosine score
- Required comparison: `Voice-Method-2`, L2-normalized person centroid cosine score
- Optional later method: `Voice-Method-3`, quality-weighted centroid
- Current decision: threshold only
- Target decision: threshold plus top-two margin
- Persistence: model-separated raw speaker templates in app-private profiles
- Acceptance device: Pepper API 23 / ARMv7

## Architecture changes

1. Remove anonymous-speaker navigation, mode, clusterer, state, UI, tests, and logs.
2. Extend `SpeakerIdentityResult` with second score, margin, minimum margin, model ID, and method ID.
3. Introduce a pure domain matching boundary, such as `SpeakerTemplateScorer`, that accepts one query and a person's raw templates.
4. Implement `Voice-Method-1` and `Voice-Method-2` behind that boundary.
5. Keep raw templates separated by person and model. Do not replace stored templates with only a centroid.
6. Derive and cache a centroid only when necessary; invalidate it when a template is added, deleted, or the model changes.
7. Add `speakerMethod` and `speakerMargin` to validated settings only after domain tests exist.
8. Reject non-16-kHz utterances before the embedding engine unless a separate resampling design is approved.
9. Log model, method, template count, best score, second score, margin, thresholds, and decision.

## Evaluation plan

### Method comparison

1. Fix one speaker model and one deterministic data split.
2. Compare `Voice-Method-1` and `Voice-Method-2` on exactly the same embeddings and probes.
3. Select threshold and margin independently for each method using development data only.
4. Report FAR, MIR, Accuracy, FRR, EER, insufficient-audio rate, and score distributions.
5. Record performance cost and template-count sensitivity.
6. Add `Voice-Method-3` only if Method-2 leaves a documented quality problem and a reproducible quality weight can be defined.

### Model comparison

1. Fix the selected matching method.
2. Compare CAM++ and ERes2Net under identical segmentation, templates, probes, thresholds-search process, and metrics.
3. Do not infer model superiority from results that changed both model and method.

### Pepper qualification

1. Validate functional registration and identification with Pepper microphone input.
2. Run sustained sessions and report CPU, memory, VAD delay, utterance duration, embedding time, aggregation time, comparison time, stalls, and errors.
3. Compare dataset/file input and Pepper microphone behavior.

## Dependency order

1. Constitution alignment and anonymous-path removal
2. Pure-domain matching-method tests and implementation
3. Top-two margin and result-model changes
4. Settings and UI integration
5. Sample-rate boundary correction
6. Unit/lint/ARM64/ARMv7 validation
7. Japanese method comparison
8. Japanese model comparison with method fixed
9. Pepper microphone and sustained evidence
10. Final model/method/threshold/margin decision

## Completion boundary

This feature is complete only when anonymous-speaker identity is removed, `Voice-Method-1` and `Voice-Method-2` are reproducibly evaluated, the selected method has threshold-plus-margin behavior, unsupported sample rates are handled explicitly, and Japanese plus Pepper microphone evidence identifies the final model, method, threshold, and margin. Smoke tests alone are insufficient.
