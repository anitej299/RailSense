"""Portable normal-only model for the Android window-dc-magnitude-v1 pipeline.

No scikit-learn object is embedded in Android. Synthetic mode tests deployment only.
Uses Python's standard library; no GPU or inference runtime is required.
"""
from __future__ import annotations
import argparse
import csv
import json
import math
import statistics
from pathlib import Path

FEATURES = ["rms_m_s2", "std_m_s2", "peak_to_peak_m_s2", "jerk_rms_m_s3",
            "gyro_rms_rad_s", "dominant_hz", "high_band_ratio"]


def extract(rows, hz=50, seconds=2.0):
    """Same timestamp, centering, population moments and magnitude DFT as Kotlin."""
    n = len(rows)
    if n < 2:
        raise ValueError("Need at least two samples")
    dt = [(b[0] - a[0]) / 1e9 for a, b in zip(rows, rows[1:])]
    if min(dt) <= 0 or not all(math.isfinite(v) for r in rows for v in r):
        raise ValueError("Invalid timestamps or nonfinite values")
    actual_hz = 1 / statistics.mean(dt)
    irregular = any(abs(d - statistics.mean(dt)) > statistics.mean(dt) * .5 for d in dt)
    coverage = (rows[-1][0] - rows[0][0]) / 1e9 / seconds
    if coverage < .8 or actual_hz < hz * .7 or max(dt) > .2 or irregular:
        raise ValueError("Insufficient or irregular sampling")
    means = [statistics.mean(r[c] for r in rows) for c in (1, 2, 3)]
    mag = [math.sqrt(sum((r[c + 1] - means[c]) ** 2 for c in range(3))) for r in rows]
    avg = statistics.mean(mag)
    rms = math.sqrt(statistics.mean(v * v for v in mag))
    std = math.sqrt(statistics.mean((v - avg) ** 2 for v in mag))
    jerk = math.sqrt(statistics.mean(sum((b[c] - a[c]) ** 2 for c in (1, 2, 3)) / d ** 2
                                    for a, b, d in zip(rows, rows[1:], dt)))
    gyro = math.sqrt(statistics.mean(sum(r[c] ** 2 for c in (4, 5, 6)) for r in rows))
    peak = dominant = total = high = 0.0
    if n >= 32:
        for k in range(1, min(n // 2, 128) + 1):
            re = im = 0.0
            for i, value in enumerate(mag):
                value = (value - avg) * (.5 - .5 * math.cos(2 * math.pi * i / (n - 1)))
                angle = 2 * math.pi * k * i / n
                re += value * math.cos(angle)
                im -= value * math.sin(angle)
            power = re * re + im * im
            frequency = k * actual_hz / n
            total += power
            if frequency >= 8:
                high += power
            if power > peak:
                peak, dominant = power, frequency
    return [rms, std, max(mag) - min(mag), jerk, gyro, dominant, high / total if total > 1e-12 else 0.0]


def windows(rows, hz, seconds):
    buf = []
    next_end = None
    last = -1
    duration = round(seconds * 1e9)
    for row in rows:
        ns = row[0]
        if ns <= last:
            continue
        if last >= 0 and ns - last > 500_000_000:
            buf, next_end = [], None
        last = ns
        if next_end is None:
            next_end = ns + duration
        buf.append(row)
        buf = [r for r in buf if r[0] >= ns - duration][-2048:]
        if ns >= next_end:
            next_end = ns + round(duration * .5)
            try:
                yield extract(buf, hz, seconds)
            except ValueError:
                continue


def read_csv(path):
    with path.open(newline="", encoding="utf-8-sig") as stream:
        for r in csv.DictReader(stream):
            if r.get("record_type", "sensor") != "sensor":
                continue
            # Missing gyroscope is valid for baseline but is excluded from fusion-model training.
            fields = [r[k] for k in ("accel_x", "accel_y", "accel_z", "gyro_x", "gyro_y", "gyro_z")]
            if any(v == "" for v in fields):
                continue
            yield (int(r["monotonic_ns"]), *(float(v) for v in fields))


def synthetic(hz, seconds=40):
    for i in range(hz * seconds):
        t = i / hz
        yield (round(t * 1e9), .12 * math.sin(2 * math.pi * 6 * t), .1 * math.cos(t),
               9.81 + .12 * math.sin(2 * math.pi * 9 * t), .01, 0.0, .01)


def score(model, features):
    distance = math.sqrt(statistics.mean(((x - m) / s) ** 2 for x, m, s in
                                         zip(features, model["means"], model["scales"])))
    return min(1.0, max(0.0, distance / model["distanceScale"]))


def train(features, hz, seconds, synthetic_only, threshold=.65):
    if len(features) < 10:
        raise ValueError("Need at least ten valid normal windows")
    columns = list(zip(*features))
    means = [statistics.mean(c) for c in columns]
    scales = [max(statistics.pstdev(c), 1e-4) for c in columns]
    distances = sorted(math.sqrt(statistics.mean(((x - m) / s) ** 2 for x, m, s in zip(row, means, scales))) for row in features)
    model = dict(schema="railsense-centroid-v1", version="experimental-centroid-v1-" + ("synthetic" if synthetic_only else "unvalidated"),
                 preprocessing="window-dc-magnitude-v1", hz=hz, windowSeconds=seconds, features=FEATURES,
                 means=means, scales=scales, distanceScale=max(distances[math.ceil(.95 * len(distances)) - 1] / threshold, 1e-4),
                 decisionThreshold=threshold, trainingWindows=len(features), source="SYNTHETIC_TEST" if synthetic_only else "USER_NORMAL_RECORDING",
                 interpretation="Clipped normalized distance, NOT probability or confirmed rail damage",
                 training="Normal-only diagonal centroid; p95 training distance maps to configured threshold; no accuracy claim")
    validate(model)
    return model


def validate(model):
    assert model["schema"] == "railsense-centroid-v1"
    assert model["preprocessing"] == "window-dc-magnitude-v1"
    assert model["features"] == FEATURES
    assert 20 <= model["hz"] <= 100 and 1 <= model["windowSeconds"] <= 5
    assert len(model["means"]) == len(model["scales"]) == 7
    assert all(math.isfinite(x) for x in model["means"])
    assert all(math.isfinite(x) and x > 0 for x in model["scales"])
    assert math.isfinite(model["distanceScale"]) and model["distanceScale"] > 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path)
    parser.add_argument("--normal-only", action="store_true", help="Confirm the input is a selected normal-only training journey")
    parser.add_argument("--synthetic", action="store_true", help="Pipeline test only, never field validation")
    parser.add_argument("--hz", type=int, default=50)
    parser.add_argument("--window", type=float, default=2.0)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not 20 <= args.hz <= 100 or not 1 <= args.window <= 5:
        parser.error("Hz 20–100 and window 1–5 required")
    if bool(args.input) == args.synthetic or (args.input and not args.normal_only):
        parser.error("Choose --synthetic or --input FILE --normal-only")
    rows = synthetic(args.hz) if args.synthetic else read_csv(args.input)
    features = list(windows(rows, args.hz, args.window))
    model = train(features, args.hz, args.window, args.synthetic)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(model, indent=2, allow_nan=False), encoding="utf-8")
    # Kotlin loads this exact JSON contract. A separate round trip verifies finite numeric output.
    loaded = json.loads(args.output.read_text())
    validate(loaded)
    assert all(0 <= score(loaded, f) <= 1 for f in features)
    print(f"Wrote {args.output}: {len(features)} windows, {model['source']}; NOT field-validated")


if __name__ == "__main__":
    main()
