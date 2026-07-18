# Feature Specification: Guided Face Registration and Real-time Enrolled-person Identification

**Feature Branch**: `codex/face-identification`

**Created**: 2026-07-18

**Status**: Implemented and validated on Nothing Phone (3a); installed/model-tested on Pepper

**Input**: Complete face identification first on Nothing Phone (3a), then validate on Pepper. Defer
dataset performance evaluation and speaker implementation while fixing their later-phase boundaries.

## User Scenarios & Testing

### User Story 1 - Guided three-pose face registration (Priority: P1)

An operator registers one person by following front, left, and right pose guidance. A pose is saved
only after exactly one face remains within the target range for 1,000 ms continuously.

**Why this priority**: Identification cannot be demonstrated reliably without repeatable enrollment.

**Independent Test**: Register a person while moving in and out of each target range; verify that
exactly three embeddings are stored only after front, left, and right succeed in order.

**Acceptance Scenarios**:

1. **Given** one visible face, **When** front, left, and right each remain in range for 1,000 ms,
   **Then** one embedding for each pose is saved and registration completes with three samples.
2. **Given** a target pose is being held, **When** the face leaves the range before 1,000 ms,
   **Then** the hold timer resets and no embedding is generated.
3. **Given** zero or multiple visible faces, **When** registration is active, **Then** no embedding is
   saved and the operator sees a face-count instruction.

---

### User Story 2 - Real-time multi-person enrolled face identification (Priority: P1)

The camera continuously compares every visible face with enrolled profiles and overlays the enrolled
person name or Unknown for each tracked face. It never creates anonymous IDs or anonymous clusters.

**Why this priority**: Registered-person identification is the face PoC's primary operator flow. Its
scope is limited to explicit enrolled profiles, avoiding anonymous-person tracking or persistence.

**Independent Test**: Register at least two people, show multiple known and unknown faces together,
and verify that every face is refreshed at the configured interval using threshold and top-two margin.

**Acceptance Scenarios**:

1. **Given** one or more visible faces and registered profiles, **When** an analysis interval elapses,
   **Then** every visible face is compared and receives an enrolled name or Unknown overlay.
2. **Given** any visible face, **When** either threshold or minimum margin is not met, **Then** that
   face is displayed as Unknown without creating an anonymous identity.
3. **Given** prior results, **When** faces disappear or tracking IDs change, **Then** stale results are
   cleared and the next visible tracks are evaluated automatically.

---

### User Story 3 - Face controls and deletion (Priority: P1)

An operator can tune bounded face parameters and remove persisted PoC registrations.

**Why this priority**: Device tuning and deletion are required to operate the PoC safely.

**Independent Test**: Save non-default face settings, restart the app, verify restoration, then delete
all profiles and confirm no face match is possible.

**Acceptance Scenarios**:

1. **Given** valid face parameter values, **When** settings are saved and the app restarts,
   **Then** the same values are restored.
2. **Given** an out-of-range setting, **When** saving is attempted, **Then** it is not persisted.
3. **Given** stored registrations, **When** delete-all is confirmed, **Then** all person embeddings are
   removed and the UI confirms deletion.

### Edge Cases

- Tracking ID changes during a hold: reset smoothing and hold state for the new track.
- The second candidate does not exist: treat the margin as satisfied only after threshold passes.
- Model initialization or embedding extraction fails: show an error and clear stale identity results.
- Pose sign differs between a mirrored preview and camera coordinates: expose current signed yaw and
  validate direction on each device before accepting Pepper completion.
- App leaves the foreground during registration: abandon the in-progress hold without saving.

## Requirements

### Functional Requirements

- **FR-001**: Registration MUST save exactly three face embeddings per completed attempt: front,
  left, and right.
- **FR-002**: A pose MUST remain continuously valid for 1,000 ms; leaving the range MUST reset it.
- **FR-003**: Registration analysis MUST default to a 200 ms interval and median smoothing over the
  most recent five pose samples.
- **FR-004**: Front MUST initially allow absolute yaw and pitch up to 8 degrees; left and right MUST
  initially use absolute yaw from 18 through 32 degrees. On the Nothing Phone (3a) front camera's
  oriented analysis image, the user's left turn MUST use positive yaw and the user's right turn MUST
  use negative yaw. Because the front-camera preview is mirrored, its on-image arrows MUST point right
  for the user's left turn and left for the user's right turn. The UI MUST show this guidance over the
  camera image and show the currently interpreted direction in the control panel.
- **FR-005**: Face embeddings MUST be generated only when a registration pose succeeds or, in the
  identification screen, once per visible face at the configured analysis interval.
- **FR-006**: Every visible face MUST be evaluated independently. Identification MUST require a best
  score at or above the threshold and a best-minus-second score at or above the minimum margin.
- **FR-007**: The identification screen MUST continuously replace results for all current track IDs
  and clear results when no face is visible; it MUST NOT require a button press.
- **FR-008**: Anonymous face identities and anonymous face clusters MUST NOT be exposed or updated.
- **FR-009**: The app MUST persist and validate face threshold, minimum margin, analysis intervals,
  stable time, pose ranges, and smoothing sample count.
- **FR-010**: The UI MUST show face count, current smoothed yaw/pitch/roll, target pose, hold progress,
  model readiness, and errors needed to complete registration and identification.
- **FR-011**: The app MUST NOT persist face images or video; only embeddings and person metadata may
  be stored.
- **FR-012**: The face path MUST remain offline and run on Nothing Phone (3a) for development and on
  Pepper API 23/ARMv7 for acceptance.
- **FR-013**: Face detection, pose, embedding, comparison, and total request durations MUST be emitted
  as structured events so device performance can be evaluated later.
- **FR-014**: Dataset files MUST remain external to the APK; BIWI and Pointing'04 evaluation is a
  later phase and does not block completion of this feature.
- **FR-015**: Speaker registration and identification MUST remain a later implementation phase. Its
  fixed contract is 16 kHz mono PCM16, Silero VAD, minimum 1,000 ms voiced audio, threshold plus
  top-two margin, and Unknown/INSUFFICIENT_AUDIO outcomes.
- **FR-016**: Only real-time anonymous face identification and its temporary clusters MUST be removed;
  real-time multi-person comparison against explicitly enrolled profiles MUST remain available.
- **FR-017**: The UI MUST explain stored/non-stored data, purpose, app-private location, retention,
  deletion, and that no external transmission occurs.
- **FR-018**: Operators MUST be able to delete one person's profile or all profiles and evaluation
  logs, with explicit scope shown before full deletion.
- **FR-019**: A guided re-registration MUST replace that person's three embeddings for the selected
  model only after all poses succeed; cancellation or interruption MUST preserve the prior profile.
- **FR-020**: Registration MUST offer explicit cancel, and leaving the face screen MUST discard the
  in-progress session without persisting partial embeddings.
- **FR-021**: Registration MUST remain single-person, while identification MUST support all faces in
  the current analyzed frame and associate each result with its detector track ID.

### Key Entities

- **Head pose**: Smoothed yaw, pitch, and roll associated with a current face track.
- **Registration session**: Person identity, ordered target poses, hold start, completed poses, and
  current progress.
- **Identification frame**: The current set of detector track IDs and their per-track comparison results.
- **Face identity result**: Identified or Unknown status, best and second scores, margin, threshold,
  and processing duration.
- **Face settings**: Validated threshold, margin, analysis intervals, pose bounds, stable duration,
  and smoothing count.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A user can complete front, left, and right enrollment with exactly three stored samples
  and no saved samples from interrupted holds.
- **SC-002**: At each configured identification interval, every visible face receives one enrolled-name
  or Unknown result, and stale track results disappear when faces leave the frame.
- **SC-003**: All domain and repository tests for pose ranges, timed stability, margin rejection,
  settings validation, and three-sample persistence pass.
- **SC-004**: The full face flow installs and opens on Nothing Phone (3a), and camera registration plus
  real-time registered-person identification are directly exercised without a crash.
- **SC-005**: The ARMv7 build installs and opens on Pepper; device-only tuning values may remain
  labelled provisional, but install/runtime status is reported separately from accuracy status.
- **SC-006**: No APK asset or app-private output contains captured face images or video.

## Traceability

| Requirements | Acceptance / success evidence | Tests | Tasks | Main files |
| --- | --- | --- | --- | --- |
| FR-001..005, FR-019..020 | US1 scenarios, SC-001/003 | HeadPoseGuidanceTest, FaceIdentityCoordinatorTest | T004, T006, T008..T011 | domain/face, application/face, infrastructure/face, CameraPreviewScreen.kt |
| FR-006..008, FR-016, FR-021 | US2 scenarios, SC-002/003 | FaceIdentifierTest, FaceIdentityCoordinatorTest | T005, T007, T012..T014 | FaceIdentifier.kt, FaceIdentityCoordinator.kt, CameraPreviewScreen.kt, AppScreen.kt |
| FR-009..010 | US3 settings scenarios, SC-003 | PocSettingsTest | T015..T017 | PocSettings.kt, SharedPreferencesSettingsRepository.kt, SettingsScreen.kt |
| FR-011, FR-017..018 | US3 deletion scenario, SC-006 | FaceIdentityCoordinatorTest, repository tests | T017, T021 | PersonRepository.kt, FilePersonRepository.kt, SettingsScreen.kt, CameraPreviewScreen.kt |
| FR-012..013 | SC-004/005 | builds, adb smoke and instrumentation | T018..T020 | YuNetFaceDetector.kt, BenchmarkEvent.kt, quickstart.md |
| FR-014..015 | Deferred boundary | Later feature validation | Not in face MVP | Canonical specification supplied 2026-07-18 |

## Assumptions

- SFace remains the default face embedding model; converted 0095 remains selectable for comparison.
- Nothing Phone (3a) arm64 evidence accelerates development but does not replace Pepper acceptance.
- Face dataset performance and tuning, speaker implementation, encryption, and long-duration load
  tests are explicitly deferred and tracked outside this face MVP.
