# Quickstart Validation

## Prerequisites

- Nothing Phone (3a) and Pepper visible in `adb devices -l`
- Local model assets verified in `app/src/main/assets/models`
- Java runtime used through `gradle/wrapper/gradle-wrapper.jar` if `gradlew.bat` is unreliable

## Unit verification

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar :app:testDebugUnitTest
```

Expected: pose range/stability, face threshold+margin, multi-track real-time coordinator, settings codec,
and profile tests pass.

## Nothing Phone (3a)

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar :app:assembleDebug -PtargetAbi=arm64-v8a
adb -s 00154253Q000392 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 00154253Q000392 shell am start -n com.example.pepper_person_id_poc/.MainActivity
```

Exercise front/left/right registration, then open face identification with multiple registered and
unregistered people visible. Confirm each current face receives a periodically refreshed name or
Unknown overlay, and that stale results disappear after faces leave.

## Pepper

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar :app:assembleDebug -PtargetAbi=armeabi-v7a
adb -s 192.168.10.106:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.10.106:5555 shell am start -n com.example.pepper_person_id_poc/.MainActivity
```

Report installed, camera/model runtime, and app-operable status separately. Nothing Phone (3a) uses
user-left=positive yaw and user-right=negative yaw after display-orientation rotation. Dataset accuracy
is a later phase.

## Evidence recorded 2026-07-18

- Unit tests and lint: passed. ARM64 and ARMv7 debug builds: passed.
- Nothing Phone (3a): three-pose registration stored three samples; mirrored camera guidance showed
  `user left ->` and `<- user right`; registered person `person1` was identified continuously at score
  0.84; removing the face cleared the stale result. The operator confirmed both SFace and 0095 flows.
- Multi-face logic: unit test passed with two simultaneous tracks (one enrolled, one Unknown). A
  two-person physical-device frame remains to be captured as expanded acceptance evidence.
- Pepper: ARMv7 APK installed (`primaryCpuAbi=armeabi-v7a`), MainActivity resumed, permissions granted,
  and no crash was recorded. Face-screen operator interaction on Pepper remains for T020 completion.
- No captured image/video/audio file exists in app-private storage; persisted outputs are model assets,
  person embeddings/metadata, and structured benchmark records.

### LombardGRID three-image smoke

This is a wiring/separation smoke, not full FAR/FRR or threshold qualification. Enrollment is
`s22 plain`, same-person is `s22 Lombard`, and different-person is `s16 plain`; threshold is 0.60.

| Device | Model | Same | Different | Detection ms | Embedding ms |
|---|---|---:|---:|---|---|
| Nothing Phone (3a) | 0095 | 0.9548381 | 0.29214054 | 24-53 | 23-28 |
| Nothing Phone (3a) | SFace | 0.9542215 | 0.3344767 | 24-41 | 17-35 |
| Pepper ARMv7 | 0095 | 0.9548413 | 0.29214254 | 492-620 | 396-467 |
| Pepper ARMv7 | SFace | 0.95423216 | 0.3345171 | 489-516 | 604-737 |

Both model tests passed on both devices: same-person accepted and different-person rejected. Full
LombardGRID/BIWI/Pointing'04 manifests and FAR/FRR evaluation remain a later phase.
