from __future__ import annotations

REQUIRED_SENSOR_COLUMNS = [
    "timestamp", "latitude", "longitude", "accel_x", "accel_y", "accel_z",
    "gyro_x", "gyro_y", "gyro_z", "speed", "device_id", "device_model",
    "sampling_rate", "journey_id", "placement_id", "route_id", "label",
]

LABELS = ["NORMAL", "MILD_ANOMALY", "MODERATE_ANOMALY", "SEVERE_ANOMALY"]
LABEL_TO_INDEX = {label: index for index, label in enumerate(LABELS)}


def validate_columns(columns) -> None:
    missing = sorted(set(REQUIRED_SENSOR_COLUMNS) - set(columns))
    if missing:
        raise ValueError(f"Missing required sensor columns: {', '.join(missing)}")

