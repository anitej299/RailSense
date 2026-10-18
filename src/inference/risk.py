from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone


@dataclass
class RiskDecision:
    risk_score: float
    risk_level: str
    should_alert: bool
    reason: str


class RiskEngine:
    def __init__(self, thresholds=None, consecutive_windows=2, cooldown_seconds=30):
        self.thresholds = thresholds or {"medium": .40, "high": .65, "critical": .85}
        self.required = consecutive_windows; self.cooldown = cooldown_seconds
        self.consecutive = 0; self.last_alert = None

    def evaluate(self, probabilities, now=None):
        now = now or datetime.now(timezone.utc)
        score = float(1 - probabilities[0])
        level = "LOW"
        for candidate in ("medium", "high", "critical"):
            if score >= self.thresholds[candidate]: level = candidate.upper()
        self.consecutive = self.consecutive + 1 if level in {"HIGH", "CRITICAL"} else 0
        cooled = self.last_alert is None or (now - self.last_alert).total_seconds() >= self.cooldown
        alert = self.consecutive >= self.required and cooled
        if alert: self.last_alert = now; self.consecutive = 0
        reason = "consecutive threshold met" if alert else "suppressed by threshold, confirmation, or cooldown"
        return RiskDecision(score, level, alert, reason)

