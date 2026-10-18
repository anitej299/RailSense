from __future__ import annotations

import numpy as np
import pandas as pd
from src.data.schema import LABEL_TO_INDEX

DEFAULT_CHANNELS = ["accel_x", "accel_y", "accel_z", "gyro_x", "gyro_y", "gyro_z", "accel_mag", "gyro_mag"]


def make_windows(frame: pd.DataFrame, window_seconds=2.0, stride_seconds=1.0, channels=None):
    channels = channels or DEFAULT_CHANNELS
    windows, labels, metadata = [], [], []
    for journey_id, group in frame.groupby("journey_id", sort=False):
        group = group.sort_values("timestamp").reset_index(drop=True)
        rate = int(round(group.sampling_rate.median()))
        size, stride = max(4, int(window_seconds * rate)), max(1, int(stride_seconds * rate))
        for start in range(0, len(group) - size + 1, stride):
            chunk = group.iloc[start:start + size]
            label = chunk.label.value_counts().index[0]
            if (chunk.label != "NORMAL").any():
                label = max(chunk.label, key=lambda x: LABEL_TO_INDEX[x])
            windows.append(chunk[channels].to_numpy(np.float32))
            labels.append(LABEL_TO_INDEX[label])
            center = chunk.iloc[len(chunk) // 2]
            metadata.append({
                "journey_id": journey_id, "device_model": center.device_model,
                "placement_id": center.placement_id, "route_id": center.route_id,
                "speed": float(chunk.speed.mean()), "latitude": float(center.latitude),
                "longitude": float(center.longitude), "timestamp": str(center.timestamp),
            })
    return np.asarray(windows, dtype=np.float32), np.asarray(labels, dtype=np.int64), pd.DataFrame(metadata)

