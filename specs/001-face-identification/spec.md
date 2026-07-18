# Feature Specification: Guided Face Registration and Real-time Enrolled-person Identification

**Feature Branch**: `codex/face-identification`  
**Status**: Implemented and validated on Nothing Phone (3a) and Pepper API 23 / ARMv7

## Source of truth

- Project principles: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
- Code-grounded overview: [`../../docs/openwiki-current-state.md`](../../docs/openwiki-current-state.md)
- Implementation plan: [`plan.md`](plan.md)
- Completed implementation tasks: [`tasks.md`](tasks.md)
- Device evidence: [`quickstart.md`](quickstart.md)

## Scope

This feature completes guided front/left/right face registration and continuous identification of all visible face tracks against explicitly enrolled profiles. Speaker identification, dataset qualification, long-duration performance, speech recognition, fusion, and conversation history are separate features.

## Model and matching-method separation

The face embedding model and the matching method are independent configuration/evaluation axes.

### Models

- `Face-Model-1`: SFace 2021dec, 128 dimensions
- `Face-Model-2`: face-reidentification-retail-0095, 256 dimensions

### Methods

- `Face-Method-1`: current implementation; one separately stored template for front, left, and right, with maximum similarity across the three templates.
- `Face-Method-2`: generalized method; L2-normalize and average multiple samples within the same pose, normalize the pose centroid, then use maximum similarity across poses.
- `Face-Method-3`: all-pose centroid comparison baseline; not the default because it removes pose separation.
- `Face-Method-4`: optional same-track query-window aggregation before `Face-Method-2`.

The current registration stores one template per pose. Therefore `Face-Method-2` with $K_{p,o}=1$ is mathematically identical to the implemented `Face-Method-1` and does not change current behavior.

## User Story 1 — Guided three-pose registration

An operator registers one person by following front, left, and right guidance. A pose is accepted only when exactly one face with the same track remains inside the target range for 1,000 ms continuously.

### Acceptance scenarios

1. Front, left, and right success stores exactly three model-separated embeddings.
2. Leaving the target range resets progress.
3. Zero or multiple faces block registration.
4. Cancellation or interruption discards partial embeddings and preserves the previous completed profile.
5. Re-registration atomically replaces the selected model's templates only after all poses succeed.

## User Story 2 — Real-time enrolled-person identification

While the identification screen is active, every current face track is periodically evaluated. Each track receives an enrolled display name or `Unknown`; no identification button is required.

### Acceptance scenarios

1. Every current track is compared independently.
2. A score below threshold or top-two gap below margin returns `Unknown`.
3. Disappeared or changed tracks clear stale results.
4. Unenrolled faces receive no anonymous ID or cluster.
5. Result evidence records model, method, threshold, margin, scores, track ID, and processing time.

## Mathematical specification

### Detection landmarks and pose

For image $X_t$, YuNet returns a face box, track ID, and five landmarks:

$$
L_{t,k}=\{(u_i,v_i)\}_{i=1}^{5}
$$

The landmarks are eyes, nose, and mouth corners; they are distinct from the three enrollment poses. The implementation solves

$$
\lambda_i
\begin{bmatrix}u_i\\v_i\\1\end{bmatrix}
=
K\begin{bmatrix}R&t\end{bmatrix}
\begin{bmatrix}X_i\\Y_i\\Z_i\\1\end{bmatrix}
$$

and derives yaw, pitch, and roll from $R=[r_{ij}]$:

$$
\operatorname{yaw}=\operatorname{atan2}\!\left(-r_{20},\sqrt{r_{00}^{2}+r_{10}^{2}}\right)
$$

$$
\operatorname{pitch}=\operatorname{atan2}(r_{21},r_{22}),\qquad
\operatorname{roll}=\operatorname{atan2}(r_{10},r_{00})
$$

For each track, the latest $H=5$ observations are smoothed component-wise by median.

### Pose acceptance

Current initial ranges are:

$$
I_F=\mathbf 1(|\widetilde y|\le8\land|\widetilde p|\le8)
$$

$$
I_L=\mathbf 1(-32\le\widetilde y\le-18\land|\widetilde p|\le8)
$$

$$
I_R=\mathbf 1(18\le\widetilde y\le32\land|\widetilde p|\le8)
$$

$$
\operatorname{PoseAccepted}(o,t)
\iff n_{face}(t)=1\land I_o(t)=1\land(t-t_{entered})\ge1000\,\mathrm{ms}
$$

### Generalized per-pose enrollment templates

For person $p$, model $m$, pose $o\in\{F,L,R\}$, and sample $j$:

$$
R^{face}_{p,m,o}=\{\mathbf r_{p,m,o,1},\ldots,\mathbf r_{p,m,o,K_{p,o}}\}
$$

Normalize every sample:

$$
\widetilde{\mathbf r}_{p,m,o,j}=\frac{\mathbf r_{p,m,o,j}}{\|\mathbf r_{p,m,o,j}\|_2}
$$

`Face-Method-2` creates one centroid per pose:

$$
\overline{\mathbf r}_{p,m,o}
=
\operatorname{Normalize}\left(
\sum_{j=1}^{K_{p,o}}w_{p,o,j}\widetilde{\mathbf r}_{p,m,o,j}
\right)
$$

Uniform aggregation uses $w_{p,o,j}=1/K_{p,o}$. Current implementation has $K_{p,o}=1$, so

$$
\overline{\mathbf r}_{p,m,o}=\widetilde{\mathbf r}_{p,m,o,1}
$$

and matches `Face-Method-1`.

### Identification aggregation

At time $t$, current face embeddings are

$$
Q_t=\{\mathbf q_{t,1},\ldots,\mathbf q_{t,N_t}\}
$$

and enrolled people are $P=\{p_1,\ldots,p_M\}$. The system is N-to-N at frame level; each current track is compared 1-to-N internally.

For normalized query $\widetilde{\mathbf q}_{t,i}$:

$$
c^{face}_{i,p,o}=\widetilde{\mathbf q}_{t,i}^{\top}\overline{\mathbf r}_{p,m,o}
$$

The person score is the maximum across pose centroids:

$$
s^{face}_{i,p}=\max_{o\in\{F,L,R\}}c^{face}_{i,p,o}
$$

Thus the most similar pose is selected; front/left/right are not averaged into one all-pose identity vector.

The best and second candidates and margin are

$$
p_1=\arg\max_p s^{face}_{i,p},\qquad
p_2=\arg\max_{p\ne p_1}s^{face}_{i,p}
$$

$$
d_i=s^{face}_{i,p_1}-s^{face}_{i,p_2}
$$

The decision is

$$
\widehat y^{face}_i=
\begin{cases}
p_1,&s^{face}_{i,p_1}\ge\tau_{face}\land d_i\ge\delta_{face}\\
\mathrm{Unknown},&\text{otherwise}
\end{cases}
$$

If no second candidate exists, the margin condition is considered satisfied after threshold acceptance.

Current defaults are $\tau_{face}=0.60$, $\delta_{face}=0.0$, registration analysis 200 ms, identification 1,000 ms, stable duration 1,000 ms, and smoothing count 5. The margin code exists, but default zero does not reject ambiguous candidates.

## Functional Requirements

- **FR-001**: Completed current registration MUST store exactly one separate embedding for front, left, and right.
- **FR-002**: Registration MUST be atomic and discard partial state on interruption.
- **FR-003**: Pose smoothing, ranges, and stable duration MUST be bounded and testable.
- **FR-004**: Current identification MUST use `Face-Method-1`, threshold, and top-two margin.
- **FR-005**: Model and method IDs MUST be separate in specifications and evaluation reports.
- **FR-006**: If multiple samples per pose are introduced, `Face-Method-2` is the default candidate: normalize/average within pose, preserve separate pose centroids, and take the maximum across poses.
- **FR-007**: An all-pose centroid MUST remain an evaluation baseline rather than silently replacing pose-preserving matching.
- **FR-008**: Every visible track MUST be evaluated independently and stale results cleared.
- **FR-009**: Anonymous face identities and clusters MUST NOT exist.
- **FR-010**: Images and video MUST NOT be persisted.
- **FR-011**: Face settings and result evidence MUST include threshold, margin, intervals, pose ranges, smoothing count, model, and method.
- **FR-012**: The face path MUST operate offline on Pepper API 23 / ARMv7.

## Success Criteria and Evidence

- Front/left/right registration stores exactly three current templates and partial registration stores none.
- Continuous identification, threshold/margin logic, settings, repository behavior, and model alignment are covered by tests.
- Nothing Phone (3a) and Pepper have exercised the primary face flow with SFace and 0095.
- Current Pointing'04 evidence uses `Face-Method-1`; method comparisons are tracked in `002-face-evaluation`.
