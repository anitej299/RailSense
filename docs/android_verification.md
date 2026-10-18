# Android implementation verification — 12 October 2026

## Executed checks

| Check | Result |
|---|---|
| Kotlin/JVM pipeline tests | 22 passed, zero failures/errors/skips |
| Python mobile model tests | 3 passed |
| Synthetic centroid training/JSON validation | Passed; 38 normal synthetic windows; output `.tools/centroid-test.json`, not bundled into app |
| Debug APK | Built successfully |
| Unsigned release APK | Built successfully; requires owner release signing |
| Android instrumentation test APK | Compiled successfully |
| Android lint | Zero errors, 17 warnings (mainly older pinned dependency/target versions and Kotlin extension suggestions) |
| `adb devices -l` | No connected device/emulator |
| Device instrumentation tests | Not executed: no connected Android runtime |
| Live physical sensors, background behavior, battery drain, device latency, map rendering | Not tested on hardware/emulator |
| Real railway accuracy | Not evaluated; no claim of field validation |

Build environment: Windows 11, Android SDK 35, Gradle 9.3.0 wrapper, AGP 8.13.2, Kotlin 2.0.10, Microsoft JDK 17.0.20.1. The initially installed JDK 25 did not satisfy the project's JDK 17 toolchain. A project-local Microsoft JDK 17 was downloaded under ignored `.tools/`; build then succeeded. An initial lint permission issue was corrected with an explicit `SecurityException` handler. No lint baseline or ignored-error setting was introduced.

Final command from `mobile/`, with JDK 17 in `JAVA_HOME`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug
```

Reports: `mobile/app/build/reports/tests/testDebugUnitTest/index.html`, `mobile/app/build/reports/lint-results-debug.html`, and the local `.tools/android-final-build.log`. APKs: `mobile/app/build/outputs/apk/debug/app-debug.apk`, `mobile/app/build/outputs/apk/release/app-release-unsigned.apk`, and `mobile/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

## Main implementation files

- `mobile/app/src/main/java/org/railsense/mobile/Pipeline.kt`: validated configuration, synchronization, windows, features, model interface/baseline/centroid, event merging and synthetic source.
- `SensorCollector.kt`: independent motion/location callbacks and sensor availability.
- `MonitoringService.kt`: foreground monitoring lifecycle, bounded worker queue, file recording, inference, notifications, quality/status snapshots.
- `MainActivity.kt`: Home, Live, alert/details, Map, History, Settings, permission flow and export picker.
- `Storage.kt`, `SensorRecord.kt`, `RailSenseApp.kt`: Room tables, migration and interrupted-trip recovery.
- `Preferences.kt`: persistent DataStore settings.
- `OnDeviceInference.kt`: portable model loading and validation.
- `RecordingCsv.kt`, `CsvExporter.kt`: raw recording format and streaming exports.
- `mobile/app/src/main/AndroidManifest.xml`, `res/drawable/ic_railsense.xml`: service/permissions, privacy settings and app icon.
- `mobile/app/schemas/org.railsense.mobile.RailSenseDatabase/2.json`: generated Room schema.
- `mobile/app/src/test/.../PipelineTest.kt`: executed JVM tests.
- `mobile/app/src/androidTest/.../PersistenceTest.kt`, `MonitoringLifecycleTest.kt`: compiled persistence/migration/model-failure/service-lifecycle instrumentation checks; not run on a device.
- `scripts/train_mobile_centroid.py`, `tests/test_mobile_model.py`: model training/export/validation and Python tests.
- `mobile/README.md`, `docs/architecture.md`, `docs/model_training.md`, `docs/data_collection.md`, `docs/android_permissions.md`, `docs/testing.md`, updated root README and limitations: setup, design, interpretation and hardware test instructions.

The workspace has no Git repository metadata, so no commit or PR was created. Existing research/backend/frontend code and legacy sensor rows are retained. New live detection does not depend on those services or the legacy ONNX artifact.
