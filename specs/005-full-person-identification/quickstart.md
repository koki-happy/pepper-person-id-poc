# Quickstart: Android端末からPepperへ段階受入

## Prerequisites

- JDK and Android SDK configured in `local.properties`
- `adb devices -l` shows one ARM64 Android development device and one Pepper
- Required local model/runtime assets pass catalog hash validation
- No evaluation dataset body or generated result is staged in Git

## 1. Host validation

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon `
  :speaker-core:test `
  :app:testBenchmarkDebugUnitTest `
  :app:verifyModelCatalog `
  :app:auditBenchmarkDebugApk
```

Expected:

- domain, catalog, quality, candidate ordering, HOLD non-mutation, conversion manifest, and report tests pass
- benchmark APK contains the expected candidates and only declared ABIs

## 2. ARM64 Android device first

```powershell
$AndroidSerial = "<adb serial for ARM64 phone>"
$AndroidApi = <exact API level reported for that serial>

.\scripts\acceptance\Get-DevicePreflight.ps1 `
  -Serial $AndroidSerial `
  -TargetClass Android `
  -ExpectedAbi arm64-v8a `
  -ExpectedApiLevel $AndroidApi `
  -OutputPath .\device-evidence\android-preflight.json

.\scripts\acceptance\Invoke-DeviceAcceptance.ps1 `
  -Serial $AndroidSerial `
  -TargetClass Android `
  -ExpectedAbi arm64-v8a `
  -ExpectedApiLevel $AndroidApi `
  -ScenarioResultsPath .\device-evidence\android-scenario-results.json
```

Run scenario IDs in order:

1. `A-DIAG-001`: API/ABI/front camera/16 kHz microphone/offline diagnostics
2. `A-FACE-001`: one face create/reuse/all-candidates
3. `A-FACE-002`: multiple faces and five landmarks
4. `A-FACE-003`: low-quality HOLD with repository unchanged
5. `A-FACE-004`: runtime switching with no fallback
6. `A-SPK-001`: one speaker create/reuse/all-candidates
7. `A-SPK-002`: alternating speakers/local tracking
8. `A-SPK-003`: partial overlap uses solo segments only
9. `A-SPK-004`: full overlap/ambiguous/multiple speakers HOLD
10. `A-PRIV-001`: no biometric/media files at rest; explicit exit clears session
11. `A-SOAK-015`, `A-SOAK-030`, `A-SOAK-060`

Capture for every scenario:

- screenshot/UI hierarchy
- targeted logcat and crash buffer
- device/API/ABI/package/variant
- model-space/artifact/runtime IDs
- structured metrics/report
- pass/fail and blocker

`android-scenario-results.json` must use `schemaVersion: 1`,
`scenarioSetId: person-identification-acceptance-v1`, `targetClass: Android`,
and one entry per scenario with `id`, `status`, optional `blocker`, and
`evidence[]` entries containing `kind` and an existing local `path`. The runner
computes SHA-256 itself and writes `acceptance-manifest.json`. `PASS` is not
issued when the build is explicitly skipped, any required evidence is absent,
privacy storage is not inspectable, or a package crash is present.

### Automated Android evidence (2026-07-26)

Exact target: `192.168.10.111:42047`, CPH2013, API 30,
`arm64-v8a,armeabi-v7a,armeabi`, benchmark debug.

- Host tests, catalog/hash validation, LiteRT artifact validation, ARM64 APK build,
  and APK audit passed.
- Installed application and instrumentation APKs with `adb -s ... install -r -g`.
- `OnnxRuntimeFaceEmbeddingSmokeTest`, `LiteRtModelSmokeTest`, and
  `WeSpeakerResNet34LmOnnxSmokeTest` passed:
  `OK (12 tests)` in 10.624 seconds.
- The executed exact runtimes were OpenCV SFace/0095, ONNX Runtime
  SFace/0095, ncnn SFace, MNN SFace, LiteRT YuNet/SFace, and WeSpeaker
  ResNet34-LM ONNX through sherpa-onnx, with no fallback.
- `SpeakerActivityPipelineTest` and `StrictSpeakerAudioPipelineTest` passed:
  `OK (4 tests)` in 0.978 seconds. This proves fixed alternating/overlap
  behavior and live 16 kHz mono PCM16 recorder/VAD initialization.
- These automated results do not satisfy face-present, speech-present, privacy,
  or soak checkpoints below. The Android acceptance manifest remains pending.

### Human-required checkpoints

The following are operator observations, not results that launch/no-crash,
no-face telemetry, a prerecorded WAV, or the runner may infer:

- `A-FACE-001` through `A-FACE-004`: place the required one or multiple real
  faces in view, confirm the UI result and all-candidate/quality/runtime fields,
  then save `face-present-checkpoint` evidence.
- `A-SPK-001` through `A-SPK-004`: produce live speech, alternating speakers,
  partial overlap, and full overlap as named by the scenario; confirm the
  live-microphone/VAD/segmentation result, then save
  `speech-present-checkpoint` evidence.
- A scenario stays `PENDING_HUMAN` or `BLOCKED` until its named condition was
  physically present. Never mark it `PASS` from app launch alone.
- The operator must explicitly exit the app before the final `A-PRIV-001`
  scan so session cleanup is exercised.

Collect bounded soak evidence separately:

```powershell
foreach ($Minutes in 15, 30, 60) {
  .\scripts\acceptance\Invoke-SoakTest.ps1 `
    -Serial $AndroidSerial `
    -DurationMinutes $Minutes `
    -OutputDirectory ".\device-evidence\android-soak-$Minutes"
}
```

Pepper execution is prohibited until all required `A-*` scenarios pass and
their evidence hashes validate.

## 3. Build candidate after validation review

Create `device-evidence/candidate-selection.json` only after reviewing the
Android validation evidence. The gate accepts exactly this schema and re-hashes every
referenced evidence file:

```json
{
  "schemaVersion": 1,
  "status": "APPROVED",
  "artifactIds": ["<selected artifactId>"],
  "runtimeIds": ["<selected Android runtimeId>"],
  "evidence": [
    {
      "kind": "validation-report",
      "path": "device-evidence/<reviewed report>.json",
      "sha256": "<64 lowercase hex SHA-256>"
    }
  ]
}
```

Without this file, with `status` other than `APPROVED`, or after evidence hash
drift, every candidate build fails closed.

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon `
  -PtargetAbi=armeabi-v7a `
  -PcandidateSelectionEvidence=device-evidence/candidate-selection.json `
  :app:verifyCandidateSelectionEvidence `
  :app:generateCandidateModelAllowlist `
  :app:assembleCandidateDebug `
  :app:auditCandidateDebugApk `
  :app:compareDistributionApkSizes
```

Expected:

- candidate contains only selected artifacts/runtimes
- `armeabi-v7a` exists
- API 23/native coexistence audit passes
- license gate passes
- `app/build/reports/apk-audit/size-comparison.json` records the benchmark and
  candidate APK sizes and their byte reduction

## 4. Pepper after Android pass

```powershell
$PepperSerial = "<adb serial for Pepper>"
$PepperApi = 23
$AndroidPassManifest = "<absolute path to the Android acceptance-manifest.json>"

.\scripts\acceptance\Assert-AndroidPassBeforePepper.ps1 `
  -AndroidPassManifestPath $AndroidPassManifest

.\scripts\acceptance\Invoke-DeviceAcceptance.ps1 `
  -Serial $PepperSerial `
  -TargetClass Pepper `
  -ExpectedAbi armeabi-v7a `
  -ExpectedApiLevel $PepperApi `
  -AndroidPassManifestPath $AndroidPassManifest `
  -ScenarioResultsPath .\device-evidence\pepper-scenario-results.json
```

Repeat the same scenarios as `P-*` using identical inputs and thresholds:

- `P-DIAG-001`
- `P-FACE-001` through `P-FACE-004`
- `P-SPK-001` through `P-SPK-004`
- `P-PRIV-001`
- `P-SOAK-015`, `P-SOAK-030`, `P-SOAK-060`

The Pepper runner performs only a read-only exact-serial preflight before the
Android-pass gate. It does not build, install, clear logs, or launch if the
Android manifest is missing, incomplete, has evidence hash drift, is not
ARM64, or does not map the complete scenario-key set to the exact `P-*` IDs.
Face-present and speech-present checkpoints must be repeated physically on
Pepper; Android evidence cannot be copied as Pepper evidence.

## 5. Acceptance boundaries

- Build success is not install success.
- Install success is not launch success.
- Launch/no-crash is not face-present or speech-present inference success.
- ARM64 success is not Pepper acceptance.
- Fixed WAV success is not live microphone/VAD/overlap success.
- No-face telemetry is stability evidence only.
- License blockers remain blockers; do not weaken the gate.
- Features explicitly excluded: observation history, ID merge/split, face/voice links, persistent embeddings.
# Logcat performance capture

Live face, speaker, device, and benchmark events are emitted as one-line JSON to
Logcat and are not persisted as app-private JSONL files.

```powershell
adb logcat -c
adb logcat -v threadtime PepperIdentityMetrics:I PepperIdentityBenchmark:I *:S
```

Redirect the second command on the development PC when evidence must be retained:

```powershell
adb logcat -v threadtime PepperIdentityMetrics:I PepperIdentityBenchmark:I *:S |
    Tee-Object -FilePath .\results\pepper-logcat.txt
```
