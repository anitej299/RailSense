# Live Android data collection

Use the phone as a passenger in a permitted public area with the device safely secured. Never put a phone on tracks, enter restricted railway property, or handle it unsafely while moving. This tool records phone motion, not direct rail geometry or verified damage.

1. Note train/journey, carriage, placement and orientation in a separate research log; avoid personal identifiers. Keep placement consistent during each session.
2. In Settings choose requested sampling rate/window, baseline model and optional GPS/raw recording. Default 50 Hz and 2 s windows are suitable for pipeline evaluation, not a validated diagnosis configuration.
3. Start monitoring explicitly. Verify **REAL_WORLD**, available sensors, actual rate, data quality and recording indicator. Synthetic demonstration is a separate, labeled source.
4. Let the device settle. Phone handling, seats, carriage dynamics, curves, points, braking and speed changes can trigger candidates. Review candidate waveform and GPS uncertainty without assuming rail defects.
5. Stop the trip. Export CSV and JSON using History. Inspect timestamps, coverage, dropped samples, quality and event details before accepting a recording into a study.
6. Remove sensitive locations before sharing. The app uploads nothing automatically. Delete local sessions after an appropriate study retention period.

Android device axes: +X to the right, +Y toward the top, +Z out of the screen in the device's natural orientation; screen rotation does not rotate sensor axes. Accelerometer/linear acceleration use m/s², gyro rad/s, speed m/s (UI converts to km/h), coordinates WGS84 degrees, accuracy metres. Accelerometer includes gravity. `monotonic_ns` uses elapsed time since boot for synchronization; `wall_time_ms` is a UTC epoch anchor for readable logs. GPS has its own monotonic and UTC columns. Monotonic timestamps must not be compared across device reboots.

CSV includes raw acceleration, timestamp-associated gyro and linear acceleration, optional GPS/speed/uncertainty, both GPS timestamps and data provenance. Blank fields mean unavailable, never zero-filled location. No raw sample resampling is hidden in exports. Approximate sample-associated GPS uncertainty includes travel since the original fix. JSON adds device/model/settings metadata, recording counts, locations and merged events. Raw files flush every two seconds; abrupt termination may lose the unflushed tail.

The older Python research schema differs from this raw Android format. Explicitly convert column names/units and supply study metadata before using the older research pipeline; do not assume raw Android CSV is immediately compatible. See [the research collection protocol](data_collection_protocol.md) for study grouping and independent labels.
