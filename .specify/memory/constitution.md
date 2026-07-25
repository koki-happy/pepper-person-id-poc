<!--
Sync Impact Report
- Version change: 1.0.0 -> 2.0.0
- Modified principle: Unknown-first now permits continuous multi-face comparison only against
  explicitly enrolled profiles while continuing to prohibit anonymous identity clusters
- Added sections: none
- Removed sections: none
- Templates: plan-template.md compatible; spec-template.md compatible; tasks-template.md compatible
- Runtime guidance: specs/001-face-identification synchronized; legacy history in
  docs/pepper-multimodal-identity-poc.md remains historical evidence
- Deferred items: none
-->
# pepper-person-id-poc Constitution

## Core Principles

### I. Anonymous-only Identification

The application MUST identify only unregistered faces and speakers with modality-specific anonymous
IDs. It MUST NOT register named people, expose person IDs, or link face and voice identities.
Anonymous clusters MUST use model-separated L2-normalized mean centroids and MUST be retained only
for the current application session.

### II. Privacy by Design

Face images, video, PCM, and WAV MUST NOT be persisted. Face and speaker embeddings MUST be treated
as personal data. The application MUST make the stored fields, storage location, purpose, retention,
deletion method, and external transmission status explainable. Encryption at rest, access control,
and verified deletion are release gates for production use, but not for this local PoC phase.

### III. Pepper-first

The acceptance target is the legacy Pepper tablet running Android 6.0, API 23, ARMv7, with about
1 GB RAM. Inference MUST run on-device CPU and MUST remain usable offline. A change is not complete
for Pepper acceptance until processing time, P95, throughput, CPU, memory, dropped work, and stalls
can be measured. Optimization MUST follow measurement.

### IV. Evidence and Traceability

Important claims MUST trace to code, a model publisher, evaluation data, or device measurements.
Every user story MUST be independently testable. Functional requirements MUST map to acceptance
criteria, tests, tasks, and affected files. Implementation MUST occur on a work branch and pass the
defined tests before review; direct implementation on `main` is prohibited.

## Platform and Data Constraints

- Application code MUST remain Kotlin-first and reuse existing OpenCV and sherpa-onnx runtimes.
- The production ABI is `armeabi-v7a`; arm64 phones are development/reference devices.
- Model and dataset licenses and provenance MUST be documented before release use.
- Dataset bodies MUST NOT be bundled in the APK.
- Persisted PoC data is limited to person metadata, model identifiers, embeddings, settings, and
  structured evaluation events in app-private storage.

## Development Workflow

Work MUST follow constitution -> specification -> plan -> tasks -> implementation. Current code and
target behavior MUST be described separately. Unknown or device-tuned values MUST be labelled as
unverified, provisional, or device-adjusted. Unit tests MUST cover domain rules; Android integration
tests and direct device evidence MUST cover model/runtime integration. Nothing Phone (3a) evidence
is development evidence; Pepper API 23/ARMv7 evidence is required for Pepper acceptance.

## Governance

This constitution governs the specification, plan, tasks, implementation, and review artifacts.
Amendments require a documented reason, impact review, and semantic version change. MAJOR changes
remove or redefine a governing rule, MINOR changes add a principle or material obligation, and PATCH
changes clarify wording without changing obligations. Every review MUST check constitution gates and
record any temporary exception in the implementation plan; no exception may weaken privacy or
Unknown-first behavior silently.

**Version**: 3.0.0 | **Ratified**: 2026-07-18 | **Last Amended**: 2026-07-25
