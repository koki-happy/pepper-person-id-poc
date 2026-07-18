# Feature Specification: Guided Face Registration and Real-time Enrolled-person Identification

**Feature Branch**: `codex/face-identification`  
**Status**: Implemented and validated on Nothing Phone (3a) and Pepper API 23 / ARMv7

## Source of truth

- Project principles: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
- Code-grounded overview: [`../../docs/openwiki-current-state.md`](../../docs/openwiki-current-state.md)
- Implementation plan: [`plan.md`](plan.md)
- Completed implementation tasks: [`tasks.md`](tasks.md)
- Device validation evidence: [`quickstart.md`](quickstart.md)
- Current behavior: code on `codex/face-identification`; when prose and code disagree, record the discrepancy and treat code as the As-Is source of truth.

## Scope

This feature completes the face-only PoC path:

1. guided front / left / right face registration;
2. continuous identification of all currently visible faces against explicitly enrolled profiles;
3. threshold and top-two-margin rejection to `Unknown`;
4. face settings, deletion, privacy explanation, structured timing evidence, and Pepper acceptance.

Speaker identification, speech recognition, face-speaker fusion, conversation history, full dataset qualification, and long-duration performance qualification are separate features.

## User Story 1 — Guided three-pose registration (P1)

An operator registers one person by following front, left, and right guidance. A pose is accepted only when exactly one face remains inside the target range for 1,000 ms continuously.

### Acceptance scenarios

1. Given one visible face, when front, left, and right each remain valid for 1,000 ms, then exactly three embeddings are stored for the selected model.
2. Given a valid hold, when the face leaves the range before completion, then the hold timer resets and no embedding is captured for that attempt.
3. Given zero or multiple faces, registration is blocked and the UI explains the required face count.
4. Given cancellation, foreground loss, or navigation away, partial embeddings are discarded and the previous completed profile remains unchanged.
5. Given re-registration, existing selected-model embeddings are replaced only after all three new poses succeed.

## User Story 2 — Real-time enrolled-person identification (P1)

While the identification screen is active, the camera continuously evaluates every current face track. Each track receives an enrolled display name or `Unknown`; no identification button is required.

### Acceptance scenarios

1. Given one or more visible faces and enrolled profiles, when the configured analysis interval elapses, every current track is compared independently.
2. Given a best score below threshold or a best-minus-second score below the minimum margin, the result is `Unknown`.
3. Given a face disappears or its detector track changes, stale results are cleared and current tracks are evaluated automatically.
4. Given an unenrolled face, no anonymous ID or anonymous cluster is created.

## Mathematical specification

### Detection landmarks and pose

For image $X_t$, YuNet returns a face box, track ID, and five image landmarks:

$$
L_{t,k}=\{(u_i,v_i)\}_{i=1}^{5}
$$

The five landmarks are the eyes, nose, and mouth corners. They are distinct from the three enrollment poses.

The implementation solves the PnP relation

$$
\lambda_i
\begin{bmatrix}u_i\\v_i\\1\end{bmatrix}
=
K
\begin{bmatrix}R&t\end{bmatrix}
\begin{bmatrix}X_i\\Y_i\\Z_i\\1\end{bmatrix}
$$

and derives Euler angles from $R=[r_{ij}]$:

$$
\operatorname{yaw}=\operatorname{atan2}\!\left(-r_{20},\sqrt{r_{00}^{2}+r_{10}^{2}}\right)
$$

$$
\operatorname{pitch}=\operatorname{atan2}(r_{21},r_{22}),
\qquad
\operatorname{roll}=\operatorname{atan2}(r_{10},r_{00})
$$

For each track, the latest $H=5$ observations are smoothed component-wise:

$$
\widetilde y_{t,k}=\operatorname{median}(y_{t-H+1,k},\ldots,y_{t,k})
$$

$$
\widetilde p_{t,k}=\operatorname{median}(p_{t-H+1,k},\ldots,p_{t,k}),
\qquad
\widetilde r_{t,k}=\operatorname{median}(r_{t-H+1,k},\ldots,r_{t,k})
$$

The current code uses LEFT as negative yaw and RIGHT as positive yaw:

$$
I_{front}=\mathbf 1(|\widetilde y|\le8\land|\widetilde p|\le8)
$$

$$
I_{left}=\mathbf 1(-32\le\widetilde y\le-18\land|\widetilde p|\le8)
$$

$$
I_{right}=\mathbf 1(18\le\widetilde y\le32\land|\widetilde p|\le8)
$$

A pose is accepted only after one face with the same track remains valid for the stable duration:

$$
\operatorname{PoseAccepted}(o,t)
\iff
n_{face}(t)=1\land I_o(t)=1\land(t-t_{entered})\ge1000\,\mathrm{ms}
$$

### Three-pose template storage

For person $p$ and face model $m$, front, left, and right embeddings are stored separately:

$$
R^{face}_{p,m}=
\{\mathbf r_{p,m,F},\mathbf r_{p,m,L},\mathbf r_{p,m,R}\}
$$

The implementation does **not** average or concatenate the three embeddings. All three are persisted only after the registration session completes, replacing the selected model's previous face templates atomically.

### Identification aggregation

At time $t$, current face embeddings are

$$
Q_t=\{\mathbf q_{t,1},\ldots,\mathbf q_{t,N_t}\}
$$

and enrolled people are $P=\{p_1,\ldots,p_M\}$. The system is N-to-N at frame level. Internally, each current track is compared 1-to-N against all enrolled people.

Cosine similarity is

$$
\cos(\mathbf a,\mathbf b)=
\frac{\mathbf a^\top\mathbf b}{\|\mathbf a\|_2\|\mathbf b\|_2}
$$

For input face $i$, person $p$, and enrolled pose $o\in\{F,L,R\}$:

$$
c^{face}_{i,p,o}=\cos(\mathbf q_{t,i},\mathbf r_{p,m,o})
$$

The three enrolled pose templates are combined by maximum similarity:

$$
s^{face}_{i,p}=\max_{o\in\{F,L,R\}}c^{face}_{i,p,o}
$$

Thus the input is matched to the most similar enrolled pose; no averaged representative embedding is created.

The best and second-best people are

$$
p_1=\arg\max_p s^{face}_{i,p},
\qquad
p_2=\arg\max_{p\ne p_1}s^{face}_{i,p}
$$

with margin

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

If no second candidate exists, the margin condition is considered satisfied after the threshold passes.

Current defaults are:

- $\tau_{face}=0.60$
- $\delta_{face}=0.0$
- registration analysis interval: 200 ms
- identification interval: 1,000 ms
- stable duration: 1,000 ms
- smoothing samples: 5

The margin implementation exists, but the default $\delta_{face}=0.0$ does not reject ambiguous candidates. Dataset-selected values remain evaluation evidence, not the application default.

## User Story 3 — Controls, privacy, and deletion (P1)

An operator can tune bounded face parameters, understand persisted data, delete one person's profile, or delete all profiles and evaluation logs.

### Acceptance scenarios

1. Valid settings persist and are restored after restart; invalid values are rejected.
2. Deleting one person removes that person's persisted profile.
3. Full deletion removes all face and speaker profile data plus evaluation logs after the scope is shown explicitly.
4. The UI explains that captured images and video are not persisted and that embeddings remain in app-private storage without external transmission.

## Functional Requirements

- **FR-001**: A completed registration MUST store exactly three separate face embeddings: front, left, and right.
- **FR-002**: A target pose MUST remain continuously valid for 1,000 ms; leaving the range MUST reset progress.
- **FR-003**: Registration analysis MUST default to 200 ms and median smoothing over the most recent five pose samples for the current track.
- **FR-004**: Initial pose ranges MUST be front `|yaw| <= 8°`, `|pitch| <= 8°`, and side absolute yaw from `18°` through `32°`.
- **FR-005**: Expensive embedding extraction MUST occur only after a registration pose succeeds or once per current face at the configured identification interval.
- **FR-006**: Every current face MUST be evaluated independently using per-person maximum template similarity, threshold, and top-two minimum margin.
- **FR-007**: Identification MUST update continuously without a button and MUST clear stale track results.
- **FR-008**: Anonymous face identities, anonymous clusters, and anonymous-face navigation MUST NOT exist.
- **FR-009**: Face threshold, margin, intervals, stable duration, pose ranges, and smoothing count MUST be persisted and validated.
- **FR-010**: The UI MUST expose face count, smoothed yaw/pitch/roll, target pose, hold progress, model readiness, current results, and actionable errors.
- **FR-011**: Face images and video MUST NOT be persisted; only embeddings and person metadata may be stored.
- **FR-012**: The face path MUST operate offline on Nothing Phone (3a) for development and Pepper Android 6.0 / API 23 / ARMv7 for acceptance.
- **FR-013**: Detection, pose, embedding, comparison, full-pipeline, and request-total durations MUST be emitted as structured evidence where measurable.
- **FR-014**: Dataset bodies MUST remain outside the APK and version control.
- **FR-015**: Speaker work remains a separate feature.
- **FR-016**: Removing anonymous face tracking MUST NOT remove continuous comparison against explicitly enrolled profiles.
- **FR-017**: The UI MUST explain stored and non-stored data, purpose, location, retention, deletion, and external transmission status.
- **FR-018**: Operators MUST be able to delete one profile or all profile and evaluation data with explicit scope.
- **FR-019**: Re-registration MUST atomically replace only the selected model's three embeddings after all poses succeed.
- **FR-020**: Registration MUST support explicit cancellation and discard partial state on interruption.
- **FR-021**: Registration is single-person; identification supports all faces in the current analyzed frame and associates each result with its detector track ID.

## Success Criteria and Current Evidence

- **SC-001 — PASS**: Three-pose registration stores exactly three samples; interruption stores no partial samples.
- **SC-002 — PASS**: Current tracks receive refreshed enrolled-name or `Unknown` results and stale tracks are cleared.
- **SC-003 — PASS**: Domain, coordinator, settings, alignment, repository, and model tests pass with lint and ARM64 / ARMv7 builds.
- **SC-004 — PASS**: Nothing Phone (3a) exercised three-pose registration, SFace and 0095, continuous identification, and stale-result clearing.
- **SC-005 — PASS**: Pepper completed front / left / right registration and continuous enrolled-person identification.
- **SC-006 — PASS**: No captured face image, video, or audio file is persisted.

A physical two-person Pepper frame and the 30-minute sustained run are evaluation-feature work, not blockers for completion of this face-function feature.

## Traceability

| Requirements | Acceptance / evidence | Tests / tasks | Primary files |
| --- | --- | --- | --- |
| FR-001..005, FR-019..020 | US1, SC-001/003/004/005 | T004, T006, T008..T011, T018..T020 | `HeadPoseGuidance.kt`, `FaceIdentityCoordinator.kt`, `YuNetFaceDetector.kt`, `CameraPreviewScreen.kt` |
| FR-006..008, FR-016, FR-021 | US2, SC-002/003/004/005 | T005, T007, T012..T014, T018..T020 | `FaceIdentifier.kt`, `FaceIdentityResult.kt`, `FaceIdentityCoordinator.kt`, `AppScreen.kt` |
| FR-009..010 | US3 settings, SC-003 | T015..T017 | `PocSettings.kt`, `SharedPreferencesSettingsRepository.kt`, `SettingsScreen.kt` |
| FR-011, FR-017..018 | US3 privacy/deletion, SC-006 | T017, T021 | `PersonRepository.kt`, `FilePersonRepository.kt`, `SettingsScreen.kt` |
| FR-012..013 | SC-004/005 and `quickstart.md` | T018..T020 | `YuNetFaceDetector.kt`, benchmark events, device scripts |
| FR-014..015 | Explicit separate feature boundary | Dataset and speaker features | `specs/002-face-evaluation/`, `specs/003-speaker-identification/` |
