# Feature Specification: Guided Face Registration and Real-time Enrolled-person Identification

**Feature Branch**: `codex/face-identification`  
**Created**: 2026-07-18  
**Status**: Implemented and validated on Nothing Phone (3a) and Pepper API 23 / ARMv7

## Source of truth

- Project principles: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
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

Speaker implementation, speech recognition, face-speaker fusion, conversation history, full dataset qualification, and long-duration performance qualification are separate features.

## User Story 1 — Guided three-pose registration (P1)

An operator registers one person by following front, left, and right guidance. A pose is accepted only when exactly one face remains inside the target range for 1,000 ms continuously.

### Acceptance scenarios

1. Given one visible face, when front, left, and right each remain valid for 1,000 ms, then exactly three embeddings are stored for the selected model.
2. Given a valid hold, when the face leaves the range before completion, then the hold timer resets and no embedding is captured for that attempt.
3. Given zero or multiple faces, then registration is blocked and the UI explains the required face count.
4. Given cancellation, foreground loss, or navigation away, then partial embeddings are discarded and the previous completed profile remains unchanged.
5. Given re-registration, then the existing selected-model embeddings are replaced only after all three new poses succeed.

## User Story 2 — Real-time enrolled-person identification (P1)

While the identification screen is active, the camera continuously evaluates every current face track. Each track receives an enrolled display name or `Unknown`; no identification button is required.

### Acceptance scenarios

1. Given one or more visible faces and enrolled profiles, when the configured analysis interval elapses, then every current track is compared independently.
2. Given a best score below threshold or a best-minus-second score below the minimum margin, then the result is `Unknown`.
3. Given a face disappears or its detector track changes, then stale results are cleared and the current tracks are evaluated automatically.
4. Given an unenrolled face, then no anonymous ID or anonymous cluster is created.

### Identification terminology

- System level: multiple input faces × multiple enrolled people, therefore N-to-N identification.
- Internal calculation: each current track embedding is compared 1-to-N against all enrolled people.
- Per-person score: the maximum cosine similarity across that person's registered embeddings, including front, left, and right samples.
- The current implementation does not aggregate three query frames; it uses one query embedding per track at each configured analysis interval.

## User Story 3 — Controls, privacy, and deletion (P1)

An operator can tune bounded face parameters, understand persisted data, delete one person's profile, or delete all profiles and evaluation logs.

### Acceptance scenarios

1. Valid settings persist and are restored after restart; invalid values are rejected.
2. Deleting one person removes that person's persisted profile.
3. Full deletion removes all face and speaker profile data plus evaluation logs after the scope is shown explicitly.
4. The UI explains that captured images and video are not persisted and that embeddings remain in app-private storage without external transmission.

## Functional Requirements

- **FR-001**: A completed registration MUST store exactly three face embeddings: front, left, and right.
- **FR-002**: A target pose MUST remain continuously valid for 1,000 ms; leaving the range MUST reset progress.
- **FR-003**: Registration analysis MUST default to 200 ms and median smoothing over the most recent five pose samples for the current track.
- **FR-004**: Initial pose ranges MUST be front `|yaw| <= 8°`, `|pitch| <= 8°`, and side absolute yaw from `18°` through `32°`, with device direction/sign validated in the UI.
- **FR-005**: Expensive embedding extraction MUST occur only after a registration pose succeeds or once per current face at the configured identification interval.
- **FR-006**: Every current face MUST be evaluated independently using threshold plus top-two minimum margin.
- **FR-007**: Identification MUST update continuously without a button and MUST clear stale track results.
- **FR-008**: Anonymous face identities, anonymous clusters, and anonymous-face navigation MUST NOT exist.
- **FR-009**: Face threshold, margin, registration and identification intervals, stable duration, pose ranges, and smoothing count MUST be persisted and validated.
- **FR-010**: The UI MUST expose face count, smoothed yaw/pitch/roll, target pose, hold progress, model readiness, current results, and actionable errors.
- **FR-011**: Face images and video MUST NOT be persisted; only embeddings and person metadata may be stored.
- **FR-012**: The face path MUST operate offline on Nothing Phone (3a) for development and Pepper Android 6.0 / API 23 / ARMv7 for acceptance.
- **FR-013**: Detection, pose, embedding, comparison, full-pipeline, and request-total durations MUST be emitted as structured evidence where measurable.
- **FR-014**: Dataset bodies MUST remain outside the APK and version control.
- **FR-015**: Speaker work remains a separate feature. Its current boundary is 16 kHz mono PCM16, speech-segment detection, minimum voiced duration, registered-person comparison, and `Unknown` / `INSUFFICIENT_AUDIO` outcomes.
- **FR-016**: Removing anonymous face tracking MUST NOT remove continuous comparison against explicitly enrolled profiles.
- **FR-017**: The UI MUST explain stored and non-stored data, purpose, location, retention, deletion, and external transmission status.
- **FR-018**: Operators MUST be able to delete one profile or all profile and evaluation data with explicit scope.
- **FR-019**: Re-registration MUST atomically replace only the selected model's three embeddings after all poses succeed.
- **FR-020**: Registration MUST support explicit cancellation and discard partial state on interruption.
- **FR-021**: Registration is single-person; identification supports all faces in the current analyzed frame and associates each result with its detector track ID.

## Key Entities

- **Head pose**: yaw, pitch, and roll associated with a current face track.
- **Registration session**: person ID, display name, ordered targets, hold progress, completed poses, and captured embeddings not yet persisted.
- **Identification frame**: current track IDs and their per-track results.
- **Face identity result**: `IDENTIFIED` or `UNKNOWN`, best score, second score, margin, threshold, selected person, and processing duration.
- **Face settings**: bounded thresholds, intervals, pose ranges, stable duration, and smoothing count.

## Success Criteria and Current Evidence

- **SC-001 — PASS**: Front, left, and right registration stores exactly three samples; interrupted registration stores no partial samples.
- **SC-002 — PASS**: Current face tracks receive periodically refreshed enrolled-name or `Unknown` results and stale tracks are cleared.
- **SC-003 — PASS**: Domain, coordinator, settings, alignment, repository, and model tests pass together with lint and ARM64 / ARMv7 builds.
- **SC-004 — PASS**: Nothing Phone (3a) directly exercised three-pose registration, SFace and 0095, continuous identification, and stale-result clearing without a crash.
- **SC-005 — PASS**: Pepper ARMv7 installed and opened the application; the operator completed front / left / right registration and confirmed continuous enrolled-person identification on the face screen.
- **SC-006 — PASS**: No captured face image, video, or audio file is present in app-private output; persisted outputs are model assets, embeddings/metadata, settings, and structured benchmark records.

A physical two-person Pepper frame and the 30-minute sustained run are evaluation-feature work, not blockers for completion of this face-function feature.

## Traceability

| Requirements | Acceptance / evidence | Tests / tasks | Primary files |
| --- | --- | --- | --- |
| FR-001..005, FR-019..020 | US1, SC-001/003/004/005 | T004, T006, T008..T011, T018..T020 | `HeadPoseGuidance.kt`, `FaceIdentityCoordinator.kt`, `YuNetFaceDetector.kt`, `CameraPreviewScreen.kt` |
| FR-006..008, FR-016, FR-021 | US2, SC-002/003/004/005 | T005, T007, T012..T014, T018..T020 | `FaceIdentifier.kt`, `FaceIdentityResult.kt`, `FaceIdentityCoordinator.kt`, `AppScreen.kt` |
| FR-009..010 | US3 settings, SC-003 | T015..T017 | `PocSettings.kt`, `SharedPreferencesSettingsRepository.kt`, `SettingsScreen.kt` |
| FR-011, FR-017..018 | US3 privacy/deletion, SC-006 | T017, T021 | `PersonRepository.kt`, `FilePersonRepository.kt`, `SettingsScreen.kt` |
| FR-012..013 | SC-004/005 and `quickstart.md` | T018..T020 | `YuNetFaceDetector.kt`, benchmark logger/events, device scripts |
| FR-014..015 | Explicit deferred boundary | Separate dataset/speaker features | `specs/002-face-evaluation/`, later speaker spec |

## Assumptions and Deferred Work

- SFace remains the default face model; converted 0095 remains selectable for comparison.
- Pointing'04 results and Pepper short-run telemetry are evidence for the separate evaluation feature, not proof of production accuracy.
- BIWI acquisition, 30-minute Pepper telemetry, a physical multi-face Pepper run, speaker re-specification, encryption, and production access controls remain separately tracked work.
