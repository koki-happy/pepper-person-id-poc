# Feature Specification: Face Accuracy and Pepper Performance Evaluation

**Feature Branch**: `codex/face-identification`

**Created**: 2026-07-18

**Status**: Approved for implementation

**Input**: Evaluate enrolled-person face identification with the full BIWI and Pointing'04 datasets,
then evaluate sustained face detection, pose guidance, and identification performance on the legacy
Pepper tablet.

## User Scenarios & Testing

### User Story 1 - Dataset identity evaluation (Priority: P1)

An evaluator can reproduce identity results for both supported face models across yaw/pitch bands,
registration strategies, enrolled subjects, and held-out Unknown subjects.

**Why this priority**: False acceptance and wrong-person identification are the highest-risk failures.

**Independent Test**: A fixed manifest produces per-model CSV/JSON/Markdown reports from BIWI and
Pointing'04 without using device camera input.

**Acceptance Scenarios**:

1. **Given** disjoint enrolled and Unknown subjects, **When** evaluation runs, **Then** FAR,
   misidentification, correct identification, FRR, and EER are reported overall and by pose band.
2. **Given** front-only and front/left/right enrollment protocols, **When** the same probes are scored,
   **Then** the performance delta is reported under identical thresholds.
3. **Given** subjects with two capture series, **When** enrollment and probe series differ, **Then** a
   cross-series report is produced without sample leakage.

---

### User Story 2 - Threshold and ambiguity evaluation (Priority: P1)

An evaluator can select a threshold and top-two margin from development data while keeping final
subjects isolated, with Unknown returned below threshold or below the minimum margin.

**Why this priority**: A single favorable threshold measured on the final set would overstate safety.

**Independent Test**: A threshold sweep reports FAR/FRR/EER and rejects a deliberately ambiguous
first-versus-second candidate case.

**Acceptance Scenarios**:

1. **Given** development and final splits, **When** thresholds and margins are swept, **Then** chosen
   parameters derive only from development data and final metrics remain isolated.
2. **Given** a best score below threshold or a top-two gap below margin, **When** classified, **Then**
   the output is Unknown.

---

### User Story 3 - Pepper sustained performance (Priority: P1)

An operator can run the real app face pipeline on Pepper and receive a reproducible short-run and
continuous-run performance report, including stalls and resource growth.

**Why this priority**: Offline correctness is insufficient if the legacy tablet cannot sustain it.

**Independent Test**: A bounded Pepper session produces timing distributions, update frequency, CPU,
memory, dropped-work, and stall measurements from actual camera processing.

**Acceptance Scenarios**:

1. **Given** one face moving front/left/right/up/down, **When** the short run completes, **Then** average,
   maximum, and P95 are reported for detection, pose, identification, frame total, and UI-visible delay.
2. **Given** continuous operation, **When** the long run completes, **Then** CPU, memory growth, effective
   FPS, dropped work, stalls, and pose-display update frequency are reported.
3. **Given** multiple faces where practical, **When** identification runs, **Then** throughput and stalls
   are recorded separately from the single-face baseline.

### Edge Cases

- Missing/corrupt dataset files fail validation before scoring and identify the exact sample.
- Subjects, sequences, or images cannot occur in both enrollment and final probe roles.
- Images with no face or multiple faces are counted and reported rather than silently discarded.
- Pose labels outside declared dataset ranges are rejected.
- A dataset unsuitable for a requested comparison reports the supported subset explicitly.
- Pepper process death, camera loss, counter reset, or incomplete run marks the run invalid.

## Requirements

### Functional Requirements

- **FR-001**: Evaluation MUST use the full available BIWI RGB frames and Pointing'04 images with their
  official subject, series, yaw, and pitch metadata.
- **FR-002**: Dataset provenance, license, source URL, downloaded file hashes, extraction result, and
  exclusions MUST be recorded.
- **FR-003**: A deterministic manifest MUST assign subjects and samples to enrollment, development,
  final registered probes, and final Unknown probes without subject or sample leakage.
- **FR-004**: Both SFace and converted 0095 MUST run under the same detection, enrollment, probe, and
  scoring protocol.
- **FR-005**: Reports MUST include pose-banded correct identification, registered-person
  misidentification, FAR, FRR, and sample counts.
- **FR-006**: Reports MUST include EER and threshold/margin sweeps, with parameter selection isolated
  from the final split.
- **FR-007**: Front-only enrollment MUST be compared with front/left/right enrollment.
- **FR-008**: Cross-series evaluation MUST cover all Pointing'04 subjects and the four BIWI subjects
  that have two series, subject to verified dataset metadata.
- **FR-009**: Below-threshold and insufficient-margin probes MUST produce Unknown.
- **FR-010**: Every result MUST be reproducible from a versioned manifest and machine-readable report.
- **FR-011**: Dataset bodies MUST remain outside the APK and excluded from version control.
- **FR-012**: Pepper evaluation MUST run the installed application pipeline on Android 6.0/API 23/ARMv7.
- **FR-013**: Pepper reports MUST include average, maximum, and P95 detection, pose, identification,
  full-pipeline, and camera-to-visible-update latency where each boundary can be measured directly.
- **FR-014**: Pepper reports MUST include average/minimum effective FPS, CPU average/maximum, memory
  average/maximum/growth, dropped count/rate, stall count/maximum duration, and pose update frequency.
- **FR-015**: Pepper MUST be evaluated for both a short functional run and a continuous run; the report
  MUST state exact duration, face count, pose actions, model, settings, and any invalid measurements.
- **FR-016**: Dataset and Pepper evidence MUST distinguish measured values from unavailable or inferred
  values; unavailable metrics MUST NOT be reported as zero.

### Key Entities

- **Dataset sample**: Dataset, subject, series, image path, yaw, pitch, roll when available, and checksum.
- **Evaluation manifest**: Immutable role assignments, enrollment protocol, model, threshold sweep,
  margin sweep, exclusions, and random seed.
- **Identity trial**: Probe subject, expected enrolled ID or Unknown, candidates, scores, decision, and
  pose band.
- **Accuracy report**: Counts and metrics overall, by dataset, pose, model, protocol, and threshold.
- **Pepper run**: Device facts, app/model/settings, timing samples, resource samples, drops, stalls, and
  start/end state.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of discovered official RGB samples are either scored or listed with a specific,
  counted exclusion reason.
- **SC-002**: Both models produce overall and pose-banded FAR, misidentification, correct-identification,
  FRR, and EER results for both datasets.
- **SC-003**: Re-running an unchanged manifest produces identical trial decisions and metric counts.
- **SC-004**: Automated leakage checks find zero overlap between enrollment/development/final subjects
  and samples under the declared protocol.
- **SC-005**: Pepper short-run and continuous-run reports contain every directly measurable metric in
  FR-013/FR-014, and explicitly label metrics that the current pipeline cannot measure.
- **SC-006**: No dataset image is added to the APK or version control.

## Assumptions

- Dataset use is limited to local research/evaluation under each publisher's license.
- Full dataset download time and storage are acceptable; acquisition begins before evaluator execution.
- Pose bands use official labels/poses and include a separately reported frontal band.
- Where BIWI metadata differs from the supplied summary, verified publisher metadata takes precedence
  and the discrepancy is recorded.
- Pepper continuous run defaults to at least 30 minutes when no longer duration is specified.
