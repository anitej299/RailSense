from __future__ import annotations

import numpy as np
from scipy import signal, stats


def _spectral(values: np.ndarray, rate: float) -> dict:
    freq, power = signal.welch(values, fs=rate, nperseg=min(len(values), 128))
    total = power.sum() + 1e-12
    probs = power / total
    centroid = float((freq * probs).sum())
    return {
        "dominant_freq": float(freq[np.argmax(power)]), "spectral_energy": float(total),
        "spectral_centroid": centroid,
        "spectral_bandwidth": float(np.sqrt(((freq - centroid) ** 2 * probs).sum())),
        "spectral_entropy": float(stats.entropy(probs + 1e-12)),
    }


def extract_features(windows: np.ndarray, sampling_rate=50, channel_names=None):
    channel_names = channel_names or [f"channel_{i}" for i in range(windows.shape[2])]
    rows = []
    for window in windows:
        row = {}
        for idx, name in enumerate(channel_names):
            values = window[:, idx]
            row.update({
                f"{name}_mean": float(values.mean()), f"{name}_std": float(values.std()),
                f"{name}_rms": float(np.sqrt(np.mean(values ** 2))),
                f"{name}_peak": float(np.max(np.abs(values))),
                f"{name}_skew": float(np.nan_to_num(stats.skew(values))),
                f"{name}_kurtosis": float(np.nan_to_num(stats.kurtosis(values))),
                f"{name}_zcr": float(np.mean(np.diff(np.signbit(values)) != 0)),
            })
            row.update({f"{name}_{key}": value for key, value in _spectral(values, sampling_rate).items()})
        rows.append(row)
    names = list(rows[0]) if rows else []
    return np.asarray([[row[name] for name in names] for row in rows], dtype=np.float32), names
