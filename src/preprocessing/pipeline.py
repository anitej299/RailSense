from __future__ import annotations

import numpy as np
import pandas as pd
from scipy import signal
from src.data.schema import validate_columns

SENSOR_COLUMNS = ["accel_x", "accel_y", "accel_z", "gyro_x", "gyro_y", "gyro_z"]


def butter_filter(values: np.ndarray, sampling_rate: float, kind="bandpass", low_hz=.5, high_hz=20.0, order=3):
    nyquist = sampling_rate / 2
    if len(values) < 3 * (order + 1):
        return values
    if kind == "lowpass":
        sos = signal.butter(order, min(high_hz / nyquist, .99), btype="low", output="sos")
    elif kind == "highpass":
        sos = signal.butter(order, max(low_hz / nyquist, .001), btype="high", output="sos")
    elif kind == "bandpass":
        lo, hi = max(low_hz / nyquist, .001), min(high_hz / nyquist, .99)
        if lo >= hi:
            return values
        sos = signal.butter(order, [lo, hi], btype="band", output="sos")
    else:
        return values
    return signal.sosfiltfilt(sos, values, axis=0)


def preprocess_sensor_frame(frame: pd.DataFrame, filter_kind="bandpass", low_hz=.5, high_hz=20.0, orientation_normalize=True) -> pd.DataFrame:
    validate_columns(frame.columns)
    df = frame.copy()
    df["timestamp"] = pd.to_datetime(df["timestamp"], utc=True)
    df = df.sort_values(["journey_id", "timestamp"]).drop_duplicates(["journey_id", "timestamp"])
    for _, indices in df.groupby("journey_id").groups.items():
        block = df.loc[indices, SENSOR_COLUMNS].interpolate(limit_direction="both").fillna(0)
        sampling_rate = float(df.loc[indices, "sampling_rate"].median())
        filtered = butter_filter(signal.detrend(block.to_numpy(), axis=0), sampling_rate, filter_kind, low_hz, high_hz)
        df.loc[indices, SENSOR_COLUMNS] = filtered
    accel = df[["accel_x", "accel_y", "accel_z"]].to_numpy(float)
    gyro = df[["gyro_x", "gyro_y", "gyro_z"]].to_numpy(float)
    df["accel_mag"] = np.linalg.norm(accel, axis=1)
    df["gyro_mag"] = np.linalg.norm(gyro, axis=1)
    if orientation_normalize:
        eps = 1e-8
        df[["accel_x", "accel_y", "accel_z"]] = accel / (np.linalg.norm(accel, axis=1, keepdims=True) + eps)
        df[["gyro_x", "gyro_y", "gyro_z"]] = gyro / (np.linalg.norm(gyro, axis=1, keepdims=True) + eps)
    return df.reset_index(drop=True)

