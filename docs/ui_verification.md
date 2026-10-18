# RailSense 3.0 verification — 13 October 2026

The final Gradle run completed successfully using JDK 17 and SDK 35:

```powershell
Set-Location 'F:\RailSense-complete-project(1)\mobile'
$env:JAVA_HOME='F:\RailSense-complete-project(1)\.tools\jdk17\jdk-17.0.20.1+1'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug
```

- 28 Kotlin host tests passed: 22 pipeline, five export/configuration, one Compose navigation test.
- Four Python model/training/export-reader tests passed.
- Android lint: zero errors, 21 warnings.
- Debug APK, unsigned release APK and device instrumentation test APK compiled successfully.
- Navigation exercised Home, Map, Settings, History, trip details, Export, back navigation, event details, acknowledgement and repeated Home selection against real composables and a synthetic Room fixture.
- Six host-rendered screenshots were inspected at 360 dp width: Home, Map, Settings, History, Export and event details. Images are under `mobile/app/build/reports/ui/`. Synthetic dates/readings in these images are test fixtures, not production readings.

The initial screenshot attempt timed out in PixelCopy on the host. Captures now draw the activity decor view into a native bitmap. The navigation test also waits for asynchronous Room observations after returning from Export.

The shareable installable debug-signed package is `releases/RailSense-v3.0.apk`, copied from the successful debug build and verified by matching SHA-256 hashes. The older v2 APK is retained. Store distribution requires an owner-controlled release signing key.

ADB returned no connected devices on this run. Version 3 was therefore not installed or physically tested. Instrumentation tests (including export redaction and lifecycle/persistence checks) compiled but were not run. Host rendering does not validate sensor timing, GPS, OEM background behavior, TalkBack or Android share targets. Live and automatic alert screens still need device visual acceptance. The supplied redesign attachment contained no reference image. See `ui_redesign.md` for changed files, all eight implemented screen types, configuration, architecture and build/run commands.
