# Android verification

## Automated commands

Use JDK 17, SDK 35, and the Gradle wrapper from `mobile/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
# Requires a connected emulator or physical device:
.\gradlew.bat :app:connectedDebugAndroidTest
# From repository root:
python -m unittest tests.test_mobile_model -v
python scripts/train_mobile_centroid.py --synthetic --output .tools/centroid-test.json
```

JVM tests cover timestamp windows and overlap, duplicate/backward timestamps, interruption, missing/irregular samples, gravity removal, analytic sine features, absent gyro, sensor synchronization, stale/future/inaccurate and missing GPS, thresholds, temporal confirmation, event merging, cooldown, invalid models/settings, permission-denied policy, CSV quoting/absence, and deterministic synthetic event generation. Python tests independently check the same analytic sine feature contract, training normalization/output validation, and bad timestamps. Android instrumentation tests cover Room persistence/migration, acknowledgement, interrupted-session recovery, cascade deletion, model asset load failures and synthetic service lifecycle across activity recreation. See [the Android verification report](android_verification.md) for what was actually executed in this environment.

## Physical Android device checklist (not a claim of completed tests)

- Install on API 26 and API 33–35 devices where available. Confirm launch, readable layout, system insets, app icon and no crash.
- Start with fine location, approximate-only location, denied location, permanently denied location, disabled GPS and denied notifications. Detection should remain available without GPS; no fabricated coordinates.
- Deny/revoke a previously granted location permission. Android may kill the process on revocation; check interrupted recovery, no automatic restart, and graceful new session.
- Monitor in airplane mode. Verify live samples/scores/history/export work. Map tile failure must not stop detection.
- Check missing gyro / missing accelerometer on emulator or suitable hardware. Missing gyro labels baseline ACCEL_ONLY; missing accelerometer prevents real monitoring.
- Compare actual sample timestamps with requested rates at 20, 50 and 100 Hz. Introduce callback gaps, CPU pressure and device sleep; check quality, bounded queues and dropped count.
- Rotate, navigate between all screens, lock/unlock, switch apps, and return. Verify one foreground service, continuous session, no duplicate sensors and no extra trip.
- Use Stop in app and notification, including rapid start/stop. Verify sensor/location release, writer close, ended trip and no further samples. Repeated Start must not duplicate the session.
- Run 20-second synthetic mode; expect one merged candidate near the generated burst, marked SYNTHETIC_TEST, with no GPS. Confirm acknowledgement persists and repeated overlapping windows do not re-notify.
- Missing/malformed/wrong-feature/wrong-rate centroid assets must produce a visible start error. Confirm baseline still works after changing model selection.
- Force-stop mid-trip and reopen. Expect INTERRUPTED at last checkpoint, no spontaneous collection. Export the partial recording and start a separate trip.
- Simulate low storage and export failures (cancel picker, remove provider access). Confirm visible errors rather than success labels. Verify local deletion and retained separately exported copies.
- Confirm event details, GPS timestamps/uncertainty, feature vector, waveform and min/max/mean scores agree between UI and JSON. CSV timestamps should be monotonic and blank optional readings should stay blank.
- Inspect map attribution, markers, current/latest recorded fix, no invented railway line, graceful offline behavior, and network traffic only when map enabled.
- Measure model-only latency displayed in Live, whole-pipeline latency with a profiler, sampling gaps, battery drain and memory during at least a one-hour real-device session. Label emulator measurements as emulator measurements. No fixed performance is promised.

Railway accuracy testing additionally requires real multi-journey/device/placement data and independent authorized labels. Passing software tests or shaking a phone cannot validate detection of railway damage.
