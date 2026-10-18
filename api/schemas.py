from pydantic import BaseModel, Field


class SensorWindow(BaseModel):
    values: list[list[float]] = Field(min_length=4)
    latitude: float | None = None
    longitude: float | None = None
    journey_id: str | None = None


class PredictionResponse(BaseModel):
    prediction: str
    confidence: float
    probabilities: dict[str, float]
    risk_score: float
    risk_level: str
    should_alert: bool
    disclaimer: str = "Potential vibration anomaly only; not a confirmed railway defect."

