# Research: Face Accuracy and Pepper Performance Evaluation

## Decision: Subject-disjoint development and final protocols

Use deterministic subject ordering and a fixed seed. Enrolled identities remain candidates; their
non-enrollment samples are split into development/final probes. Unknown subjects are disjoint between
development and final. Pointing'04 uses series separation. BIWI cross-series reporting is restricted to
subjects for which two verified series exist.

**Rationale**: This supports threshold selection without final-set leakage and makes Unknown trials real
identity-disjoint trials.

**Alternatives considered**: Random image split across all subjects (identity leakage); select threshold
on final trials (optimistic bias).

## Decision: Compare normalized enrollment centroids

Generate one centroid per enrolled identity from either frontal samples or frontal/left/right samples.
Compare normalized probe embeddings with cosine similarity, then apply the app threshold and top-two
margin exactly.

**Rationale**: It mirrors the app's multiple-sample identity aggregation while making protocols equal
between SFace and 0095.

**Alternatives considered**: Best single sample (sensitive to sample count); train a classifier (not the
app decision path).

## Decision: Count detection failures as Unknown decisions

No-face, multi-face, corrupt-image, and inference failures are retained as explicit trial outcomes and
exclusion/error counts. A registered probe with no usable embedding contributes to FRR; an Unknown
probe remains Unknown but is also reported as a detection failure so it cannot improve FAR silently.

**Rationale**: End-to-end identification includes detection and cannot discard hard poses invisibly.

## Decision: Separate app-event and host-resource evidence

Use structured app events for detection/pose/embedding/comparison timings and adb sampling for CPU,
memory, process survival, and wall-clock stalls. Report state-ready latency separately from true visible
pixel latency if the latter is unavailable.

**Rationale**: It provides direct evidence without pretending that a proxy is a display measurement.

## Decision: Thirty-minute default continuous run

Use a short functional run plus a 30-minute default sustained run, with exact run duration recorded.

**Rationale**: It is long enough to reveal heat/memory growth while remaining feasible for PoC work.
