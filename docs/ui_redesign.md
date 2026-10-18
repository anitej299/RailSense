# RailSense 3.0 — Android interface redesign

The brief contained text only; no reference image was attached. This native Compose redesign follows the specified navy/blue/cyan palette, typography, card spacing and outline icons. Button gradients use darker tonal stops for white-label contrast. It does not use a WebView or replace real readings with demonstration data.

## Architecture and screens

`MainActivity` handles Android permission requests and the document/share intents. `RailSenseViewModel` exposes immutable state through Flow/StateFlow and owns screen actions. `ui/RailSenseAppUi.kt` provides Navigation Compose, back navigation, single-top saved bottom destinations and pipeline-triggered alert routing. The sensor service remains independent of UI rendering. All eight requested screen types are implemented, plus trip details:

| Screen | Connected behavior |
|---|---|
| Home | Start/stop, real sensor availability, permission state, GPS speed, session event count and latest saved/live event |
| Live | Sensor axes, staleness/availability, delivered frequency, rolling waveform, window metrics and model-only latency |
| Alert | Candidate timestamp, experimental index, signal band, waveform, quality, location, acknowledgement and details |
| Map | Existing osmdroid/OpenStreetMap, recorded route, distinct markers, marker-to-details, legend, recenter, offline state and event list |
| History | Room-derived counts, times, duration, quality, empty/error states, trip details and deletion |
| Settings | Validated sensors/windows/thresholds/confirmation/cooldown, GPS, history, raw recording, notifications and model; DataStore persistence |
| Event details | Stored excerpt, feature summary, model, uncertainty, acknowledgement and optional surrounding raw signal |
| Export/share | Select sensor/GPS/event/metadata content, CSV/JSON, document picker, FileProvider share sheet, progress/success/error states |

The four bottom destinations are Home, Map, History and Settings. Live, alert, event details, trip details and export are pushed onto the stack. Repeated bottom taps use single-top navigation and state restoration. All monitoring buttons operate the actual foreground service. Synthetic demonstrations remain explicitly labeled in Settings and throughout their session.

## Charts, accessibility and interpretation

Reusable components are in `ui/Components.kt`; palette/type scale in `ui/RailTheme.kt`. Metric grids switch to one column on narrow/large-font layouts. Content scrolls, touch targets are at least 48 dp, icon actions have descriptions, and waveform descriptions expose ranges to accessibility services. Status uses text/icons as well as color.

Bounded Compose Canvas charts provide grid lines, axes, units and time labels. The raw magnitude chart includes gravity; window metrics and stored peak-window excerpts are DC-removed, explicitly distinguished in captions. Live chart snapshots publish at most five times per second from six seconds / at most 600 samples. Model features still update at window cadence. Candidate intervals use red highlighting. Stored peak excerpts use sample indices because the existing schema lacks exact per-excerpt timestamps. Surrounding raw context uses recorded wall time, capped at 1,000 samples and two seconds of context where available.

Scores remain experimental indices, not probabilities. Amber/red denote configured **signal bands**, not validated rail defect severity. Missing values are not presented as zero. No field accuracy is claimed.

## Map and external configuration

No paid API or map key is needed. The optional OpenStreetMap view uses the existing osmdroid dependency, app-private caching, visible attribution and identifiable user agent. Loading tiles sends ordinary network requests; core detection stays offline-capable. Map tiles are darkened visually. Coordinates and route points are real recorded GPS only. Offline/tile failures leave a coordinate/event list and retry control. Map view resources follow the activity lifecycle and are detached when disposed.

The optional trained centroid still requires a compatible `centroid.json` asset, matching rate/window, and the documented training workflow. The baseline needs no model download. Release distribution requires the owner's signing key.

## Persistence and export privacy

Room schema stays at v2; only observation/count queries changed. Existing trips and migration remain intact. A DataStore session-history flag defaults to true for existing installations. When disabled, trip/event/GPS/raw records are not written; up to 200 events and 2,000 GPS points remain in process memory. Raw recording is separate and only runs when history is enabled.

Export v3 makes location **off by default**. Opting out omits coordinates, accuracy, GPS speed and GPS timestamps from event/sensor data and omits location records entirely. JSON contains selected structured categories; CSV uses `record_type` values `trip`, `event`, `location`, `sensor` with a shared header. The training script filters to sensor rows. Provenance remains labeled. Files are prepared in app-private cache before saving/sharing; preparation errors delete partial files. FileProvider exposes only that exports directory with temporary read permissions. The user chooses a sharing app; nothing uploads automatically.

## Changed files

- `ui/RailTheme.kt`, `ui/Components.kt`: design system and reusable controls/charts.
- `ui/RailSenseAppUi.kt`: app scaffold and navigation.
- `ui/DashboardScreens.kt`, `ui/HistoryScreens.kt`, `ui/MapScreen.kt`, `ui/SettingsScreens.kt`: all screen types.
- `RailSenseViewModel.kt`, `MainActivity.kt`: state/actions and Android integration.
- `MonitoringService.kt`, `Pipeline.kt`, `Preferences.kt`, `Storage.kt`: bounded waveform, transient sessions, bands and history queries.
- `ExportOptions.kt`, `CsvExporter.kt`, manifest, provider/icon/theme resources: selective exports and sharing.
- `NavigationUiTest.kt`, `ExportOptionsTest.kt`, `ExportPrivacyTest.kt`: host navigation/rendering, export selection and device redaction tests.

## Exact Windows commands

```powershell
Set-Location 'F:\RailSense-complete-project(1)\mobile'
$env:JAVA_HOME='F:\RailSense-complete-project(1)\.tools\jdk17\jdk-17.0.20.1+1'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :app:lintDebug
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
.\gradlew.bat :app:installDebug
& $adb shell am start -n org.railsense.mobile/.MainActivity
```

Use your installed JDK 17 path on another computer. SDK 35 and the existing Gradle wrapper are required. Debug APK is under `mobile/app/build/outputs/apk/debug/`; release output is unsigned. Initial dependency resolution and Robolectric runtime setup need network access.

## Validation boundaries

Host Compose tests do not establish physical sensor behavior, OEM background reliability, map provider availability, TalkBack behavior or share-target compatibility. See the verification report for executed checks. No screenshot-reference match can be claimed without the missing image. Final visual acceptance should include the target phone. Railway accuracy remains unvalidated.
