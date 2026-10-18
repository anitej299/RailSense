# RailSense — Track Anomaly Detection

Version 3.0 adds a dark technical dashboard, four-tab Navigation Compose shell, dedicated live/alert/event/export screens, ViewModel state, labeled charts, private export choices and Android sharing. See [UI redesign](../docs/ui_redesign.md) for screen details, changed files and commands. The text brief's palette was used; no reference image was attached.

Native Kotlin / Compose / Material 3 application for **experimental vibration anomaly candidates**, with offline inference. No backend, Firebase, cloud inference, paid key, or Google Play Services is needed. This app does not diagnose rail damage or replace official inspection.

## Windows 11 setup

Install Android Studio, SDK Platform 35, platform-tools and JDK 17. Open this `mobile` folder in Android Studio. Set **Settings → Build Tools → Gradle → Gradle JDK** to JDK 17. Create `local.properties` with your SDK path (Android Studio normally does this). The repository wrapper is Gradle 9.3.0; AGP is 8.13.2 and Kotlin is 2.0.10. Dependencies are pinned in `app/build.gradle.kts`.

This workspace also has a downloaded Microsoft JDK under `.tools/jdk17/jdk-17.0.20.1+1` at the repository root. It is ignored, machine-local tooling, not an app dependency.

From PowerShell in this workspace:

```powershell
Set-Location 'F:\RailSense-complete-project(1)'
$env:JAVA_HOME = 'F:\RailSense-complete-project(1)\.tools\jdk17\jdk-17.0.20.1+1'
Set-Location mobile
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

On another machine replace `JAVA_HOME` with your JDK 17 installation. First Gradle sync requires internet to download dependencies. Installed detection works offline.

## Install and run on a physical phone

Enable Developer options and USB debugging, connect by USB, and approve the computer on the phone:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
.\gradlew.bat :app:installDebug
& $adb shell am start -n org.railsense.mobile/.MainActivity
```

Tap **Start monitoring**, review the explanation and confirm **Start monitoring**, grant optional location/notification permissions, and watch **Live monitoring**. Phone sensor samples, processing, inference and storage run independently of the UI. Denying location retains inertial detection. Denying notifications disables candidate notifications, but Android can still expose the foreground task in its system UI. Switch screens or rotate the device; monitoring belongs to the service. Tap **Stop** in the app or persistent notification to finish.

**Run synthetic demonstration** in Settings uses generated motion, no GPS and no hardware sensors. Its UI, trip metadata, CSV and JSON are labeled `SYNTHETIC_TEST`. Do not use this mode to claim hardware or field validation.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release artifact: `app/build/outputs/apk/release/app-release-unsigned.apk`; sign it with your own release key before distribution. No release key is generated or embedded here.

## History, export and deletion

After stopping, open **History → View trip details → Export & share**. Choose CSV or JSON and select sensor data, location, events and/or metadata. Location is off by default and is removed from all exported categories unless explicitly enabled. Raw sensor selection is unavailable if raw recording was disabled; other selected content can still be exported. CSV v3 has a `record_type` column (`sensor`, `event`, `location`, `trip`); JSON has structured selected categories. **Save to device** opens Android's document picker. **Share exported file** uses a restricted FileProvider and Android chooser. Deletion removes the app's trip, events, location records and raw file; separately exported copies remain at their chosen destinations.

Inspect a saved CSV after copying it to the computer:

```powershell
Get-Content .\trip.csv -TotalCount 5
python -c "import json; d=json.load(open('trip.json')); print(d['source'], len(d['events']))"
```

With session history enabled, raw samples are buffered to app-private CSV storage and flushed every two seconds, if raw recording is enabled. GPS rows and events use Room. A killed process is recorded as **INTERRUPTED** at the last checkpoint on next launch; it never silently starts a new recording. Turning **Save session history** off prevents persistent trip/event/GPS/raw writes and keeps only bounded session data in memory.

## Settings and models

Default: requested 50 Hz, 2 s windows, 50% overlap, detection threshold 0.65, high signal band 0.85, two consecutive candidate windows, 30 s notification cooldown. Configure sampling (20–100 Hz, constrained by the sensor's advertised rate), window (1–5 s), overlap (0–0.9), thresholds, confirmation, cooldown, GPS, session history, raw recording, notifications and model in **Settings**. Changes take effect on the next trip. The measured rate is displayed; hardware delivery is not assumed. Signal bands are UI interpretation thresholds, not calibrated railway severity.

The default `experimental-baseline-v2` requires no model asset and scores `clamp(max(RMS/4, peakToPeak/12), 0, 1)`. Scores are not probabilities. The optional portable centroid model is a normal-only statistical ML alternative to deploying scikit-learn Isolation Forest. It is trained separately and loaded with feature-order, preprocessing, shape, finite-value and configuration validation. No centroid is bundled by default.

From the repository root, train and install an explicitly synthetic deployment test:

```powershell
python scripts/train_mobile_centroid.py --synthetic --output mobile/app/src/main/assets/centroid.json
python -m unittest tests.test_mobile_model -v
Set-Location mobile
.\gradlew.bat :app:installDebug
```

Choose **Trained centroid** in Settings, keeping 50 Hz / 2 s and threshold 0.65. For real training replace `--synthetic` with `--input normal-trip.csv --normal-only`. This flag confirms you selected normal-only data; it does not establish ground truth. Read [model training](../docs/model_training.md) before interpreting results. The legacy ONNX artifact and Python research models are not used by this version's live pipeline.

## Optional map

The map loads only after you tap **Load optional map**. It shows the most recent 2,000 recorded GPS points, latest point and candidate markers, with OpenStreetMap attribution. Tap a candidate marker to open details; use Recenter to return to the latest recorded point. Polyline segments connect recorded points, not railway geometry. Tiles use network and provider caching; no bulk tile download or offline tile guarantee is provided. No API key is needed. Detection continues when maps/GPS/network are absent.

See [architecture](../docs/architecture.md), [permissions](../docs/android_permissions.md), [testing](../docs/testing.md), [collection](../docs/data_collection.md), and [limitations](../docs/limitations.md).
