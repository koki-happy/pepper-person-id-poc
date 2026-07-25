# Feature Specification: Session-scoped Anonymous Biometric Identification

**Status**: Approved

## User Scenarios

### User Story 1 - Anonymous face re-identification (P1)

An operator sees the same anonymous face ID reused for sufficiently similar observations during one app session.

### User Story 2 - Anonymous speaker re-identification (P1)

An operator sees the same anonymous speaker ID reused for sufficiently similar voiced utterances during one app session.

### User Story 3 - Session lifecycle and explicit exit (P1)

The app clears stale clusters before identification starts and offers a confirmed exit that stops active work and clears both repositories.

### User Story 4 - Live device load (P2)

While either identification screen is visible, app CPU, app PSS, device memory, and low-memory state update every second.

## Functional Requirements

- **FR-001**: Only settings, model selection, diagnostics, anonymous face identification, and anonymous speaker identification remain.
- **FR-002**: Face and speaker IDs, files, model namespaces, and counters remain independent.
- **FR-003**: Cluster centroids use an L2-normalized sum and normalized centroid.
- **FR-004**: Only same-model, same-dimension clusters participate in matching.
- **FR-005**: Join thresholds are configurable from 0.00 through 1.00; update limits from 1 through 100.
- **FR-006**: At the update limit the centroid freezes but the ID remains reusable.
- **FR-007**: Repositories atomically persist schema version, next counter, and cluster metadata in app-private storage.
- **FR-008**: Images, video, PCM, WAV, and complete embedding vectors are not logged or persisted outside cluster files.
- **FR-009**: Process startup clears both repositories before identification resources are created.
- **FR-010**: Navigation, recomposition, Activity recreation, and temporary backgrounding do not clear repositories.
- **FR-011**: Coordinator close releases screen resources without clearing repositories.
- **FR-012**: Explicit exit stops active work, clears both repositories and counters, then removes the task.
- **FR-013**: Corrupt or unknown-version files fail closed to an empty repository without affecting the other modality.
- **FR-014**: Device load monitoring runs once per visible identification screen at 1,000 ms intervals.
- **FR-015**: Final acceptance remains Pepper API 23 / ARMv7 and offline CPU inference.
- **FR-016**: Each identification screen lists the current query's cosine similarity for every anonymous cluster with the same model and embedding dimension, sorts the list by similarity descending, and marks the selected cluster.
- **FR-017**: The face bounding box and result list MUST present the temporary face detection ID and persistent anonymous face feature ID as separately labeled values.
- **FR-018**: The face preview MUST overlay the available left-eye, right-eye, nose, left-mouth-corner, and right-mouth-corner landmark points using the same preview coordinate transform as the face box.
- **FR-019**: New settings with no persisted speaker-model choice MUST default to CAM++ English and its model-specific candidate threshold; an existing saved choice MUST remain unchanged.

## Success Criteria

- Required lifecycle and repository scenarios pass deterministically.
- No registered-person route or control remains.
- A connected ARM64 development phone completes both anonymous flows without a crash.
- Pepper evidence distinguishes install, launch, operability, and face/speaker-present validation.

## Assumptions

- Cluster files are unencrypted for this local PoC.
- Switching models retains old-model clusters for the session but never matches across models.
- Startup deletion is the retention guarantee; exit deletion is best effort.
