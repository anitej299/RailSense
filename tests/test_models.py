import numpy as np
import pytest
from src.models.deep import build_model
from src.inference.risk import RiskEngine
from src.inference.segments import aggregate_segments


@pytest.mark.parametrize("name",["cnn","lstm","cnn_bilstm","transformer"])
def test_forward(name):
    model=build_model(name,8,4,16); output=model(__import__("torch").randn(3,40,8)); assert output.shape==(3,4)


def test_alert_confirmation_and_cooldown():
    engine=RiskEngine(consecutive_windows=2,cooldown_seconds=60)
    assert not engine.evaluate([.1,.1,.3,.5]).should_alert
    assert engine.evaluate([.1,.1,.3,.5]).should_alert
    assert not engine.evaluate([.1,.1,.3,.5]).should_alert


def test_segment_crowd_aggregation():
    frame=__import__("pandas").DataFrame({"latitude":[12.0,12.00001],"longitude":[77.0,77.00001],"risk_score":[.8,.7],"journey_id":["a","b"]})
    result=aggregate_segments(frame); assert result.iloc[0].journeys_reporting==2 and result.iloc[0].anomaly_count==2

