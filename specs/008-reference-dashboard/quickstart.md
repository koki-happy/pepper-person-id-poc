# Quickstart: reference dashboard

1. Build the benchmark variant:

   ```powershell
   ./gradlew :app:compileBenchmarkDebugKotlin
   ```

2. Install the generated debug APK on an API-23+ Android target (Pepper uses
   `armeabi-v7a`) and launch the app.

3. Open the identification screen. The header is `顔・話者識別`; face and speaker
   controls are independent and both start ON. Granting neither permission is valid:
   the panels remain visible and show `—`/`未検出`.

4. Open `設定`, change a permitted model/parameter with the existing sliders/dropdowns,
   press `保存`, return to identification, and start the pipeline again. The next start
   uses the saved values.

5. Verify the dashboard: camera preview is 640×480 (4:3), the two result tables have
   local similarity legends and no Top2/Top3 columns, the metrics table shows average-only
   device/face/speaker totals, and the event log uses the shared elapsed time.

6. Press `リセット`. Anonymous session repositories and all display-only rows/events are
   cleared; settings remain saved.

Host compilation does not prove Pepper camera/model acceptance. Record physical Pepper
reinstall and runtime evidence separately when the device is available.
