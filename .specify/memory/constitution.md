# Pepper Person Identification PoC Constitution

**Version**: 1.0.0  
**Ratified**: 2026-07-18  
**Last amended**: 2026-07-18

## I. Unknown-first identification

- Face and speaker identification MUST return `Unknown` when the best candidate does not meet the configured acceptance threshold.
- Identification MUST also return `Unknown` when the best candidate is not sufficiently separated from the second candidate, where a top-two margin is part of the applicable feature specification.
- Unenrolled people and speakers MUST NOT receive persistent or session-scoped anonymous identity IDs or anonymous clusters.
- Real-time preprocessing required for identification, including face detection, pose estimation, UI guidance, audio capture, and speech-segment detection, is allowed. This principle prohibits anonymous identity tracking, not real-time analysis.
- Registered-person face identification MAY run continuously while the face screen is active. The triggering and update cadence belong to the feature specification, not this constitution.

## II. Privacy by design

- Captured face images, video, PCM, and WAV data MUST NOT be persisted by the PoC.
- Face and speaker embeddings and person metadata MUST be treated as personal data used only for local enrolled-person comparison.
- The application MUST be able to explain what is stored, where it is stored, why it is stored, how long it is retained, how it is deleted, and whether it is transmitted externally.
- Production use requires separately reviewed encryption at rest, access control, and deletion confirmation. These production controls MUST NOT be represented as complete solely because the PoC runs locally.

## III. Pepper-first execution

- Acceptance target is the legacy Pepper tablet running Android 6.0 / API 23 / ARMv7 with approximately 1 GB RAM.
- Inference MUST run on-device CPU and MUST remain operable without network access unless an explicit later specification changes that boundary.
- A feature is not complete for Pepper acceptance until the ARMv7 build installs, opens, and its primary operator flow is exercised on Pepper.
- Performance decisions MUST be based on measured evidence. Unavailable metrics MUST be labelled unavailable and MUST NOT be reported as zero.
- Optimization follows measurement; speculative optimization MUST NOT replace functional and performance evidence.

## IV. Evidence and traceability

- OpenWiki-style As-Is documentation MUST be grounded in the current branch code. When prose and code disagree, the discrepancy MUST be recorded and the code is the source of truth for current behavior.
- GitHub Spec Kit artifacts MUST separate constitution, feature specification, implementation plan, tasks, and validation evidence.
- Each user story MUST be independently testable.
- Functional requirements MUST be traceable to acceptance scenarios, tests, implementation tasks, primary files, and device or dataset evidence where applicable.
- Completed tasks MUST remain in GitHub history but MUST NOT be shown as remaining work in the canonical Notion task summary.
- Changes MUST be developed on a work branch and reviewed before merging to `main`.

## Governance

- This constitution contains durable project principles only. Replaceable models, libraries, thresholds, timing values, and UI mechanics belong in feature specifications or plans.
- Amendments require: a documented reason, an updated version, an amendment date, and review of affected specifications and tasks.
- Feature specifications and plans MUST include a constitution check. Any exception MUST be explicit, justified, and time-bounded.
- The canonical Notion page may summarize this constitution in Japanese, but the GitHub file and Notion summary MUST remain semantically consistent.
