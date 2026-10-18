from __future__ import annotations

import pandas as pd


def aggregate_segments(predictions: pd.DataFrame, precision=4):
    df = predictions.copy()
    df["segment_id"] = df.latitude.round(precision).astype(str) + ":" + df.longitude.round(precision).astype(str)
    return df.groupby("segment_id", as_index=False).agg(
        latitude=("latitude", "mean"), longitude=("longitude", "mean"),
        anomaly_count=("risk_score", lambda x: int((x >= .4).sum())),
        anomaly_density=("risk_score", lambda x: float((x >= .4).mean())),
        average_risk=("risk_score", "mean"), maximum_risk=("risk_score", "max"),
        journeys_reporting=("journey_id", "nunique"),
    )

