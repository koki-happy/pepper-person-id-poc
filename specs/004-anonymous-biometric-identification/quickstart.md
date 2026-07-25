# Validation Quickstart

```powershell
java -jar gradle/wrapper/gradle-wrapper.jar testDebugUnitTest -PtargetAbi=arm64-v8a
java -jar gradle/wrapper/gradle-wrapper.jar assembleDebug -PtargetAbi=arm64-v8a
adb -s 8820a23a install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify startup gating, ID reuse, navigation and Activity recreation persistence, exit deletion, and live device load.

## 2026-07-25 OPPO evidence

- `CPH2013`, API 30, ARM64: app and AndroidTest APK installed.
- Instrumentation: `OK (5 tests)`.
- Face-present run produced `anonymous-face-*`, persisted across screen navigation, and displayed live CPU/PSS/device memory.
- Microphone run produced `anonymous-speaker-*` from voiced utterances and displayed live load.
- Confirmed exit removed the task and left `files/biometric/` empty.
- Pepper API 23/ARMv7 final acceptance remains pending target-device execution.
