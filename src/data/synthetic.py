from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import argparse
import json
import numpy as np
import pandas as pd
from scipy.spatial.transform import Rotation

from src.data.schema import LABELS


@dataclass
class SyntheticConfig:
    journeys: int = 8
    duration: float = 24.0
    sampling_rate: int = 50
    devices: tuple[str, ...] = ("device_a", "device_b", "device_c")
    placements: tuple[str, ...] = ("seat", "pocket", "hand", "fixed_horizontal", "fixed_vertical")
    routes: tuple[str, ...] = ("route_a", "route_b", "route_c")
    noise: float = 0.08
    anomaly_rate: float = 0.12
    seed: int = 42


DEVICE_PROFILES = {
    "device_a": (0.00, 1.00, 1.00),
    "device_b": (0.05, 1.05, 1.25),
    "device_c": (-0.06, 0.94, 1.55),
}
PLACEMENT_EULER = {
    "seat": (0, 0, 0), "pocket": (22, 8, 35), "hand": (35, 15, 55),
    "fixed_horizontal": (0, 0, 0), "fixed_vertical": (90, 0, 0),
}


def _journey_frame(cfg: SyntheticConfig, journey_number: int, rng: np.random.Generator) -> pd.DataFrame:
    device = cfg.devices[journey_number % len(cfg.devices)]
    placement = cfg.placements[journey_number % len(cfg.placements)]
    route = cfg.routes[journey_number % len(cfg.routes)]
    bias, scale, noise_scale = DEVICE_PROFILES.get(device, (0.0, 1.0, 1.0))
    n = int(cfg.duration * cfg.sampling_rate)
    jitter = rng.normal(0, 0.0008, n)
    t = np.maximum.accumulate(np.arange(n) / cfg.sampling_rate + jitter)
    speed_base = [10.0, 20.0, 31.0][journey_number % 3]
    speed = np.clip(speed_base + 2.5 * np.sin(2 * np.pi * t / cfg.duration) + rng.normal(0, .3, n), 1, None)
    speed_factor = 0.65 + speed / 28.0
    phase = rng.uniform(0, 2 * np.pi)
    vertical = speed_factor * (0.34 * np.sin(2 * np.pi * 2.4 * t + phase) + 0.16 * np.sin(2 * np.pi * 7.2 * t))
    lateral = 0.14 * speed_factor * np.sin(2 * np.pi * 1.5 * t)
    longitudinal = 0.10 * speed_factor * np.sin(2 * np.pi * 3.3 * t + 0.4)
    labels = np.zeros(n, dtype=int)
    event_count = max(1, int(cfg.duration * cfg.anomaly_rate / 2))
    for event_number in range(event_count):
        center = rng.integers(cfg.sampling_rate, max(cfg.sampling_rate + 1, n - cfg.sampling_rate))
        severity = (journey_number + event_number) % 3 + 1
        width = max(4, int(cfg.sampling_rate * (0.08 + 0.05 * severity)))
        idx = np.arange(n)
        impulse = np.exp(-0.5 * ((idx - center) / width) ** 2)
        ringing = np.sin(2 * np.pi * (8 + severity * 2) * t) * impulse
        vertical += severity * (0.55 * impulse + 0.35 * ringing)
        lateral += severity * 0.18 * ringing
        labels[impulse > 0.18] = np.maximum(labels[impulse > 0.18], severity)
    accel = np.column_stack([longitudinal, lateral, vertical + 9.81])
    gyro = np.column_stack([np.gradient(lateral, t), np.gradient(longitudinal, t), np.gradient(vertical, t)]) * 0.07
    rotation = Rotation.from_euler("xyz", PLACEMENT_EULER.get(placement, (0, 0, 0)), degrees=True)
    accel = rotation.apply(accel) * scale + bias + rng.normal(0, cfg.noise * noise_scale, accel.shape)
    gyro = rotation.apply(gyro) * scale + bias * .1 + rng.normal(0, cfg.noise * .25 * noise_scale, gyro.shape)
    missing = rng.random(n) < 0.003
    accel[missing] = np.nan
    lat0, lon0 = 12.9716 + journey_number * .002, 77.5946 + journey_number * .002
    distance = np.cumsum(speed / cfg.sampling_rate)
    latitude = lat0 + distance / 111_320
    longitude = lon0 + np.sin(distance / 400) * .001
    start = pd.Timestamp("2026-01-01T00:00:00Z") + pd.Timedelta(hours=journey_number)
    timestamps = start + pd.to_timedelta(t, unit="s")
    frame = pd.DataFrame({
        "timestamp": timestamps, "latitude": latitude, "longitude": longitude,
        "accel_x": accel[:, 0], "accel_y": accel[:, 1], "accel_z": accel[:, 2],
        "gyro_x": gyro[:, 0], "gyro_y": gyro[:, 1], "gyro_z": gyro[:, 2],
        "linear_accel_x": accel[:, 0], "linear_accel_y": accel[:, 1],
        "linear_accel_z": accel[:, 2] - 9.81, "orientation_x": PLACEMENT_EULER.get(placement, (0,0,0))[0],
        "orientation_y": PLACEMENT_EULER.get(placement, (0,0,0))[1],
        "orientation_z": PLACEMENT_EULER.get(placement, (0,0,0))[2],
        "speed": speed, "device_id": f"anonymous-{device}", "device_model": device,
        "sampling_rate": cfg.sampling_rate, "journey_id": f"journey_{journey_number + 1:02d}",
        "placement_id": placement, "route_id": route,
        "label": [LABELS[value] for value in labels], "data_source": "SYNTHETIC",
    })
    return frame


def generate_dataset(cfg: SyntheticConfig, output: str | Path | None = None) -> pd.DataFrame:
    rng = np.random.default_rng(cfg.seed)
    result = pd.concat([_journey_frame(cfg, i, rng) for i in range(cfg.journeys)], ignore_index=True)
    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        result.to_csv(path, index=False)
        path.with_suffix(".metadata.json").write_text(json.dumps({**cfg.__dict__, "devices": list(cfg.devices), "placements": list(cfg.placements), "routes": list(cfg.routes), "data_source": "SYNTHETIC"}, indent=2))
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate clearly labelled synthetic RailSense data")
    parser.add_argument("--output", default="data/synthetic/sensors.csv")
    parser.add_argument("--journeys", type=int, default=8)
    parser.add_argument("--duration", type=float, default=24)
    parser.add_argument("--sampling-rate", type=int, default=50)
    parser.add_argument("--devices", nargs="+", default=list(SyntheticConfig.devices))
    parser.add_argument("--placements", nargs="+", default=list(SyntheticConfig.placements))
    parser.add_argument("--routes", nargs="+", default=list(SyntheticConfig.routes))
    parser.add_argument("--noise", type=float, default=.08)
    parser.add_argument("--anomaly-rate", type=float, default=.12)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    cfg = SyntheticConfig(args.journeys, args.duration, args.sampling_rate, tuple(args.devices), tuple(args.placements), tuple(args.routes), args.noise, args.anomaly_rate, args.seed)
    frame = generate_dataset(cfg, args.output)
    print(f"Wrote {len(frame):,} SYNTHETIC observations to {args.output}")


if __name__ == "__main__":
    main()
