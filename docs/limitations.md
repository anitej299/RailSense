# Limitations and Responsible Use

## Android implementation

- Version 3 UI follows the written palette/layout brief; its referenced image was not attached. Host-rendered previews and tests cannot substitute for final target-phone, TalkBack, large-font and share-target validation.
- Export v3 has explicit content selection; location is excluded by default. CSV includes a `record_type` column, so downstream tools should filter sensor rows. The mobile centroid training reader supports this format.
- With local session history off, the session is intentionally transient; only bounded in-memory events/GPS are retained. It cannot be exported after process death or reconstructed from Room.

- Default detection is an untrained statistical baseline. The optional normal-only centroid is experimental; synthetic training only demonstrates software deployment. No real railway accuracy is claimed.
- The live pipeline removes a window's mean acceleration vector but does not estimate global phone attitude. Rotation, handling and speed changes remain important confounders. Collected linear acceleration is not silently substituted into the model.
- Timestamp synchronization is causal nearest-previous association, not hardware clock synchronization. Gyro freshness is 100 ms; GPS is at most 5 seconds old with uncertainty increased for travel since the fix. The 100 m acceptance bound and unknown-speed assumption are engineering heuristics, not guaranteed localization accuracy.
- GPS and map tiles are optional. The displayed path is a polyline of recorded GPS points, never an inferred rail alignment. osmdroid supplies the optional map; long-term maintenance may require replacing this map adapter.
- Foreground operation and a timed wake lock reduce interruptions but cannot override Android/OEM battery policy, user force-stop or hardware limitations. Abrupt termination can lose the unflushed two-second recording tail. Start/stop must be exercised on real devices.
- Model-only inference time is measured on the device while monitoring. No phone/emulator was attached during implementation, so hardware behavior, battery drain and field latency remain unverified; see the verification report.
- Room's v1 migration preserves the original `sensor_records` table, but old pre-v2 rows have no trip/event metadata and are not retroactively fabricated into new trips. New sessions use streamed files and the new history tables.
- CSV exports raw sensor records; JSON includes trip metadata, events, locations and raw records. When raw recording is disabled, only metadata/events/GPS are retained. App-private storage is not an additional encryption layer; exported files contain sensitive journey locations.
- Trip and event list queries currently load their metadata into memory. Raw sensor recordings and GPS export are streamed/paged, and live buffers/map history are bounded. Very large multi-year metadata histories need pagination and retention work.
- The release APK is unsigned until the owner supplies a release key. A sideloadable build does not imply Google Play approval of the declared special-use foreground service.

## Research limitations

- Synthetic dynamics cannot reproduce the full train–track–vehicle–phone transfer function.
- Event classes are simulated vibration patterns, not verified rail defects.
- GPS error can shift events between adjacent segments, especially under cover.
- Phone mounting, carriage position, train type, speed, wheel condition, and passenger handling are confounders.
- CORAL uses unlabeled target features and can hurt when domain assumptions fail.
- Android sampling frequency is device- and power-policy-dependent.
- No result constitutes structural diagnosis, safety certification, or permission for operational deployment.

Required next work is a permissioned, preregistered field study with multiple phones, placements, routes, train types, repeated journeys, inspection-aligned labels, uncertainty calibration, and independent validation by railway-domain experts.
