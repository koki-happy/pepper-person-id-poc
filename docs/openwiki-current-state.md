# OpenWiki Current State: pepper-person-id-poc

**Branch**: `codex/face-identification`  
**Updated**: 2026-07-18  
**Purpose**: Code-grounded As-Is overview. Feature requirements and work tracking remain in `specs/`.

## Source order

1. Current branch code for implemented behavior.
2. `.specify/memory/constitution.md` for durable principles.
3. `specs/*/spec.md` for feature requirements.
4. `specs/*/plan.md` for implementation design.
5. `specs/*/tasks.md` for completed and remaining work.
6. `quickstart.md` and `results.md` for device and evaluation evidence.

When prose conflicts with code, record the discrepancy and treat code as the current behavior until the documentation is corrected.

## Runtime boundary

- Android application: Kotlin, Jetpack Compose, one `app` module.
- Acceptance device: legacy Pepper tablet, Android 6.0 / API 23 / ARMv7, approximately 1 GB RAM.
- Development device: Nothing Phone (3a), ARM64.
- Inference: on-device CPU, offline.
- Persistence: app-private profiles/settings and JSON Lines benchmark data.
- Captured face images, video, PCM, and WAV are not persisted.

## Current navigation

- Settings and device diagnostics
- Model selection
- Person registration
- Face identification
- Speaker registration and registered-speaker identification
- Anonymous-speaker identification — still present and contrary to the constitution; removal is remaining work
- Benchmark results
- Speech recognition, fusion, and conversation history placeholders

Anonymous-face navigation and clustering have been removed.

## Face path — implemented and Pepper-validated

### Registration

1. CameraX supplies the front-camera frame.
2. YuNet detects faces and five landmarks.
3. `HeadPoseEstimator` uses OpenCV `solvePnP` to estimate yaw, pitch, and roll.
4. `HeadPoseSmoother` calculates the median of the latest five samples for the active track.
5. Exactly one face must remain inside the target pose range for 1,000 ms.
6. Front, left, and right embeddings are held in the registration session.
7. After all three poses succeed, the selected model's three embeddings are atomically replaced.
8. Cancellation or interruption persists no partial embeddings.

### Identification

- The identification screen continuously processes all current detector tracks at the configured interval; no button press is required.
- System level: multiple current faces × multiple enrolled people, therefore N-to-N identification.
- Internal calculation: one query embedding per current track is compared 1-to-N against all enrolled people.
- A person's score is the maximum cosine similarity across that person's registered embeddings.
- The result is identified only when the best score meets the threshold and the best-minus-second score meets the minimum margin; otherwise it is `Unknown`.
- Stale track results are cleared when faces disappear or tracking IDs change.
- No anonymous face ID or anonymous face cluster is created.

### Face models

| Model | Dimension | Runtime | Status |
| --- | ---: | --- | --- |
| SFace 2021dec | 128 | OpenCV | Implemented; Nothing Phone (3a) and Pepper flow validated |
| face-reidentification-retail-0095 | 256 | OpenCV DNN with converted ONNX | Implemented; Nothing Phone (3a) and Pepper flow validated |

### Primary face code

| Responsibility | Primary file |
| --- | --- |
| Pose ranges, median smoothing, timed stability | `domain/face/HeadPoseGuidance.kt` |
| Threshold, second candidate, margin, Unknown | `domain/face/FaceIdentifier.kt` |
| Registration session and multi-track identification | `application/face/FaceIdentityCoordinator.kt` |
| YuNet landmarks, pose and embedding gating | `infrastructure/face/YuNetFaceDetector.kt` |
| 0095 alignment and inference | `infrastructure/face/FaceReidentificationRetail0095EmbeddingEngine.kt` |
| Guidance and result overlays | `ui/screen/CameraPreviewScreen.kt` |
| Persisted bounded settings | `domain/config/PocSettings.kt`, `infrastructure/repository/SharedPreferencesSettingsRepository.kt` |

## Speaker path — current implementation and known gap

- Audio is captured with `AudioRecord`; 16 kHz is preferred with fallback support.
- Speech segments are detected with Silero VAD at 16 kHz and an energy-based fallback elsewhere.
- CAM++ and ERes2Net are selectable speaker embedding models.
- One utterance embedding is compared against enrolled people using each person's maximum registered-template similarity and a threshold.
- Top-two margin rejection is not yet implemented for speaker identification.
- Anonymous-speaker IDs/clustering remain in code and must be removed under task T032.

## Spec Kit feature status

| Feature | State | Evidence / remaining work |
| --- | --- | --- |
| `001-face-identification` | Complete | T001–T021 complete; Nothing Phone (3a) and Pepper primary face flow validated |
| `002-face-evaluation` | Partial | Pointing'04 and short Pepper evidence complete; T011, T014, T019, T020, T022 remain |
| Speaker re-specification | Not yet separated into a new Spec Kit feature | Remove anonymous speaker path, add margin, evaluate Japanese speech and Pepper microphone |

## Measured face evidence

### Pointing'04 final split

| Model | Enrollment | FAR | Misidentification | Correct ID | FRR |
| --- | --- | ---: | ---: | ---: | ---: |
| 0095 | front | 1.61% | 0.00% | 93.43% | 6.57% |
| 0095 | front / left / right | 0.72% | 0.00% | 95.58% | 4.42% |
| SFace | front | 6.45% | 0.12% | 92.71% | 7.17% |
| SFace | front / left / right | 3.23% | 0.00% | 94.03% | 5.97% |

These results qualify the declared Pointing'04 protocol only. They do not establish production thresholds for Pepper camera conditions.

### Pepper evidence boundary

- Three-pose registration and continuous enrolled-person identification have been exercised on Pepper.
- Short telemetry and model smoke evidence exist.
- The 30-minute run and a physical simultaneous two-face run remain evaluation tasks.
- Metrics that the current pipeline cannot measure directly must remain unavailable, not zero.

## Remaining work

- `002-T011` and `002-T014`: full BIWI evaluation and reproducibility/leakage evidence; blocked on a complete verified archive.
- `002-T019`: Pepper 30-minute continuous run.
- `002-T020`: Pepper physical multiple-face run.
- `002-T022`: converge the final evaluation report with measured and unavailable values separated.
- `T032`: remove anonymous-speaker navigation, temporary IDs, and clustering.
- Create a dedicated speaker Spec Kit feature for margin rejection and Japanese/Pepper microphone evaluation.
