# Contract: Speaker Pipeline

## Ordered stages

1. AudioRecord at 16 kHz mono PCM16
2. Silero VAD
3. Utterance/window segmentation
4. Speaker activity and overlap estimation
5. Cross-window local speaker linking
6. Solo-segment extraction
7. Audio quality assessment
8. Per-segment speaker embedding
9. Local-speaker aggregation
10. Finite-value and L2 normalization validation
11. All-compatible-candidate evaluation
12. Persistence policy
13. Session-only create/update/hold
14. UI and metrics

## Fail-closed rules

- 16 kHz initialization failure stops recording; no 44.1 kHz fallback.
- Overlapped speech is not treated as one speaker.
- Complete overlap, ambiguous local tracking, unsupported activity inference, or more active speakers than supported produces HOLD.
- Only solo segments contribute to a speaker embedding.
- Runtime failure never falls back to Energy VAD, another model, or another runtime.

## Required output

- recording format and initialization diagnostics
- VAD/activity/overlap/local speaker state
- audio quality and rejection reasons
- modelSpaceId, artifactId, runtimeId, dimension
- per-stage timings and RTF
- complete candidate list
- decision and persistence operation
