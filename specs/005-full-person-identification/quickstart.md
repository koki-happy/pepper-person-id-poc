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
  :speaker-benchmark:test `
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

java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon `
  -PtargetAbi=arm64-v8a `
  :app:assembleBenchmarkDebug `
  :app:assembleBenchmarkDebugAndroidTest

adb -s $AndroidSerial install -r app\build\outputs\apk\benchmark\debug\app-benchmark-debug.apk
adb -s $AndroidSerial install -r app\build\outputs\apk\androidTest\benchmark\debug\app-benchmark-debug-androidTest.apk
adb -s $AndroidSerial shell am start -n com.example.pepper_person_id_poc.benchmark/.MainActivity
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

Pepper execution is prohibited until all required `A-*` scenarios pass.

## 3. Build candidate after benchmark selection

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon `
  -PtargetAbi=armeabi-v7a `
  :app:verifyCandidateModelLicenses `
  :app:assembleCandidateDebug `
  :app:auditCandidateDebugApk
```

Expected:

- candidate contains only selected artifacts/runtimes
- `armeabi-v7a` exists
- API 23/native coexistence audit passes
- license gate passes

## 4. Pepper after Android pass

```powershell
$PepperSerial = "<adb serial for Pepper>"

adb -s $PepperSerial install -r app\build\outputs\apk\candidate\debug\app-candidate-debug.apk
adb -s $PepperSerial shell am start -n com.example.pepper_person_id_poc.candidate/.MainActivity
```

Repeat the same scenarios as `P-*` using identical inputs and thresholds:

- `P-DIAG-001`
- `P-FACE-001` through `P-FACE-004`
- `P-SPK-001` through `P-SPK-004`
- `P-PRIV-001`
- `P-SOAK-015`, `P-SOAK-030`, `P-SOAK-060`

## 5. Acceptance boundaries

- Build success is not install success.
- Install success is not launch success.
- Launch/no-crash is not face-present or speech-present inference success.
- ARM64 success is not Pepper acceptance.
- Fixed WAV success is not live microphone/VAD/overlap success.
- No-face telemetry is stability evidence only.
- License blockers remain blockers; do not weaken the gate.
- Features explicitly excluded: observation history, ID merge/split, face/voice links, persistent embeddings.
