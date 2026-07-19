# Feature Specification: Face Accuracy and Pepper Performance Evaluation

**Feature Branch**: `codex/face-identification`  
**Status**: Partially implemented

## Scope

Evaluate enrolled-person face identification on BIWI and Pointing'04, then evaluate sustained operation on the legacy Pepper tablet. The evaluation MUST distinguish the embedding model from the template/query aggregation and decision method.

## Evaluation axes

### Embedding models

- `Face-Model-1`: SFace 2021dec, 128 dimensions
- `Face-Model-2`: face-reidentification-retail-0095, 256 dimensions

### Matching methods

- `Face-Method-1`: current method; one template per front/left/right pose and maximum similarity across poses.
- `Face-Method-2`: normalize samples, average within the same pose, normalize the pose centroid, then take the maximum across pose centroids.
- `Face-Method-3`: normalize all enrollment samples across all poses into one centroid; comparison baseline only.
- `Face-Method-4`: aggregate a short same-track query window, then apply `Face-Method-2`; stability/performance comparison only.

Model comparison MUST hold the method constant. Method comparison MUST hold the model, data split, detector, thresholds-search process, and probe set constant.

## Mathematical protocol

For person $p$, model $m$, pose $o$, and enrollment sample $j$:

$$
\widetilde{\mathbf r}_{p,m,o,j}=\frac{\mathbf r_{p,m,o,j}}{\|\mathbf r_{p,m,o,j}\|_2}
$$

`Face-Method-2` pose centroid:

$$
\overline{\mathbf r}_{p,m,o}
=
\operatorname{Normalize}\left(
\sum_{j=1}^{K_{p,o}}w_{p,o,j}\widetilde{\mathbf r}_{p,m,o,j}
\right)
$$

with uniform weights $w_{p,o,j}=1/K_{p,o}$ unless an explicitly versioned quality rule is evaluated.

For normalized query $\widetilde{\mathbf q}_i$:

$$
s^{face}_{i,p}=\max_{o\in\{F,L,R\}}\widetilde{\mathbf q}_i^\top\overline{\mathbf r}_{p,m,o}
$$

When $K_{p,o}=1$, `Face-Method-2` reduces to the current `Face-Method-1` behavior.

`Face-Method-3` uses one all-pose centroid:

$$
\overline{\mathbf r}^{all}_{p,m}
=
\operatorname{Normalize}\left(
\sum_{o,j}\widetilde{\mathbf r}_{p,m,o,j}
\right)
$$

$$
s^{face,all}_{i,p}=\widetilde{\mathbf q}_i^\top\overline{\mathbf r}^{all}_{p,m}
$$

The best and second candidates are:

$$
p_1=\arg\max_p s_{i,p},\qquad p_2=\arg\max_{p\ne p_1}s_{i,p}
$$

$$
d_i=s_{i,p_1}-s_{i,p_2}
$$

$$
\widehat y_i=
\begin{cases}
p_1,&s_{i,p_1}\ge\tau\land d_i\ge\delta\\
\mathrm{Unknown},&\text{otherwise}
\end{cases}
$$

Threshold $\tau$ and margin $\delta$ MUST be selected separately for each model-method pair using development data only.

## User Story 1 — Dataset identity evaluation

An evaluator can reproduce identity results across models, methods, pose bands, enrollment protocols, enrolled subjects, and held-out Unknown subjects.

### Acceptance scenarios

1. Disjoint enrolled and Unknown subjects produce FAR, MIR, Accuracy, FRR, and EER overall and by pose band.
2. Front-only and front/left/right enrollment are compared on the same probes.
3. The report contains distinct `model_id` and `method_id` fields.
4. Model comparisons keep `method_id` fixed.
5. Method comparisons keep `model_id` fixed.
6. If a dataset contains only one accepted sample per pose, the report states that `Face-Method-1` and `Face-Method-2` are mathematically equivalent for that protocol.

## User Story 2 — Threshold and ambiguity evaluation

An evaluator selects threshold and top-two margin from development data while final subjects remain isolated.

### Acceptance scenarios

1. Each model-method pair receives an independent threshold/margin sweep.
2. A best score below threshold or top-two gap below margin returns `Unknown`.
3. Final metrics are produced without retuning on final data.

## User Story 3 — Pepper sustained performance

An operator can run the installed face pipeline on Pepper and obtain short-run, continuous-run, and multiple-face reports.

### Acceptance scenarios

1. Short runs report average, maximum, and P95 for detection, pose, embedding, comparison, pipeline, and UI-ready delay where measurable.
2. Long runs report CPU, memory growth, effective processing rate, skipped work, stalls, and errors.
3. Multiple-face runs are reported separately from the one-face baseline.
4. A method that adds query-window aggregation reports its additional latency and memory independently.

## Functional Requirements

- **FR-001**: Record dataset provenance, license, source URL, hashes, extraction results, and exclusions.
- **FR-002**: Use deterministic, leakage-free enrollment/development/final roles.
- **FR-003**: Run SFace and 0095 with identical detection, samples, and selected method for model comparison.
- **FR-004**: Run method comparisons with identical model, samples, split, and parameter-search protocol.
- **FR-005**: Store `model_id`, `method_id`, enrollment protocol, threshold, margin, and random seed in every report.
- **FR-006**: Report FAR, MIR, Accuracy, FRR, EER, exclusions, and sample counts overall and by pose.
- **FR-007**: Threshold and margin selection MUST use development data only.
- **FR-008**: Pointing'04 cross-series evaluation MUST avoid sample and role leakage.
- **FR-009**: Missing/corrupt samples and no-face/multiple-face samples MUST be counted explicitly.
- **FR-010**: Dataset bodies MUST remain outside the APK and version control.
- **FR-011**: Pepper evaluation MUST use the installed API 23 / ARMv7 application pipeline.
- **FR-012**: Unavailable metrics MUST remain unavailable and MUST NOT be reported as zero.
- **FR-013**: Query temporal aggregation, when evaluated, MUST use only the same track ID and MUST report the selected window and weights.
- **FR-014**: Changing model or method requires reselecting thresholds and margins; existing values MUST NOT be reused without evidence.

## Success Criteria

- All discovered official samples are scored or assigned a counted exclusion reason.
- Each result row identifies both model and method.
- Re-running the same manifest produces identical decisions and counts.
- Leakage validation reports zero cross-role overlap.
- Model and method conclusions are stated separately.
- Pepper reports contain all directly measurable metrics and explicitly mark unavailable boundaries.

## Current evidence boundary

Current Pointing'04 results use `Face-Method-1`. They establish neither the superiority of all maximum-score methods nor production thresholds for Pepper camera conditions. `Face-Method-2` is equal to the current method while each pose has only one stored template; its benefit requires multiple accepted samples within at least one pose.
