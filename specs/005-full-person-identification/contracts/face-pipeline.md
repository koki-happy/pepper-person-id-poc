# Contract: Face Pipeline

## Ordered stages

1. Camera frame acquisition
2. Rotation, mirror, and color conversion
3. Face detection
4. Quality measurement
5. Five-landmark validation
6. Alignment
7. Embedding extraction
8. Finite-value and L2 normalization validation
9. All-compatible-candidate evaluation
10. Persistence policy
11. Session-only create/update/hold
12. UI and metrics

## Required output per detected face

- frame and detection IDs
- bounding box and five landmarks
- nullable detector confidence
- quality values and rejection reasons
- modelSpaceId, artifactId, runtimeId, dimension
- stage timings
- complete candidate list
- decision and persistence operation

## Failures

- Unsupported model/runtime, load error, alignment failure, non-finite output, or quality failure MUST be explicit.
- Failure MUST NOT trigger another runtime.
- Failure or HOLD MUST NOT mutate session clusters.
