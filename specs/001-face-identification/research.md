# Research: Guided Face Registration and Real-time Enrolled-person Identification

## Decision: Split pose observation from embedding extraction

**Rationale**: YuNet must run repeatedly to guide pose, while FR-005 permits expensive embedding only
at pose acceptance or, on the identification screen, at the configured periodic interval for every
visible track. A synchronous extraction selector lets the coordinator return only the track IDs whose
embeddings are required in the same frame.

**Alternatives considered**: Extract every frame and discard embeddings (violates FR-005); cache full
camera images for later extraction (adds privacy and lifecycle risk).

## Decision: Estimate pose from YuNet five-point landmarks using solvePnP

**Rationale**: The supplied specification explicitly selects OpenCV solvePnP, already available in
the APK. Median smoothing over five samples and tolerant pose bands reduce landmark jitter. Exact
angle accuracy and mirror sign remain device-adjusted and visible in the UI.

**Alternatives considered**: Eye/nose ratios only (simpler but does not provide pitch/roll consistently);
an additional head-pose neural model (larger APK and Pepper load).

## Decision: Rotate analysis input and use device-validated yaw signs

**Rationale**: CameraX supplies the Nothing Phone (3a) portrait front-camera analysis image with a
270-degree rotation. Detection, landmarks, pose, and embedding therefore operate on a Mat rotated into
display orientation. Live checks established that the user's left turn produces positive yaw and the
user's right turn produces negative yaw. The mirrored preview therefore shows a right-pointing arrow for
the user's left turn and a left-pointing arrow for the user's right turn. Guidance is overlaid on the
camera image, while interpreted direction and raw angles remain visible in the control panel.

**Alternatives considered**: Infer direction from mirrored preview coordinates (mixes display mirroring
with pose coordinates); leave the sign device-adjusted without visible guidance (caused reversed capture).

## Decision: Treat a missing second candidate as an infinite margin

**Rationale**: With one enrolled person there is no ambiguous second identity. Threshold still guards
acceptance. With two or more candidates, best-minus-second must meet the configured margin.

**Alternatives considered**: Use zero as the second score (distorts cosine score semantics); reject all
single-person galleries (prevents the minimal PoC).

## Decision: Keep existing binary profile format

**Rationale**: It already stores multiple embeddings grouped by model. Completing a guided session
adds three samples using the existing atomic repository without persisting captured media.

**Alternatives considered**: Add pose labels to persisted embeddings (not required for matching or the
current acceptance flow and would force a format migration).

## Decision: Bundle models in the APK and install once per ABI

**Rationale**: The existing runtime copies model assets from the APK to app-private storage. There is
no supported separate model sideload path, so an early Pepper transfer would not shorten the final APK
installation. Local assets are verified before builds; arm64 and ARMv7 APKs are built separately.

**Alternatives considered**: Push models with adb and alter runtime lookup (unnecessary new behavior).
