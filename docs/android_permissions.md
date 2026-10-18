# Android permissions and background lifecycle

The app targets API 35 and supports API 26+. Motion sensors used here do not require a runtime permission at the configured 20–100 Hz. Missing accelerometer blocks a real session with an error; missing gyro permits the acceleration-only baseline. Synthetic demonstration does not subscribe to phone sensors or location.

- **Fine/coarse location**: requested contextually after the Start explanation, only when GPS collection is enabled. Approximate permission is accepted, but fixes over the uncertainty limit are excluded. Denial leaves detection available without GPS. Location services must also be enabled. No background-location permission is requested.
- **POST_NOTIFICATIONS (API 33+)**: optional, requested for notifications. Denial does not block sensor monitoring or in-app candidate review.
- **FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE (API 34+)**: continuous user-started inertial vibration monitoring is declared as `specialUse`, with a manifest subtype explanation. It is not a fitness service. Google Play distribution requires the appropriate foreground-service declaration/review; a successful sideload build does not establish Play approval.
- **FOREGROUND_SERVICE_LOCATION**: included in the runtime service type only when GPS was enabled and location permission was granted at session start. Start occurs while the activity is visible. The app does not try to bypass while-in-use restrictions or restart recording at boot.
- **WAKE_LOCK**: timed partial wake lock for an explicitly active session, released when stopped. It increases battery usage. Android/OEM policy can still interrupt the process.
- **INTERNET**: optional OpenStreetMap tile display only. No cloud detector or automatic data upload. Loading tiles discloses ordinary tile requests/IP to the provider; users must explicitly enable the map view.
- **ACCESS_NETWORK_STATE**: reports whether map networking is available. It does not enable GPS or send data.
- **FileProvider sharing**: grants temporary read access to one user-selected generated export through Android's share chooser. Only the exports cache directory is exposed; no broad file access permission is granted.
- **Storage**: app-private recording files need no broad storage permission. Export uses the Storage Access Framework and a destination selected by the user. Android backup is disabled to avoid automatic backup of recordings.

The persistent notification includes Stop. It uses its own low-importance channel; optional event notifications use a separate channel. Foreground notifications remain subject to Android notification controls. User-initiated force-stop or process death ends recording; next launch marks an unfinished trip interrupted and requires a new explicit Start.

Official references: [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions), [notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission). Map use follows [OpenStreetMap tile policy](https://operations.osmfoundation.org/policies/tiles/), with attribution and identifiable user agent, no prefetch/bulk download.
