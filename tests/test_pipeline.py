import numpy as np
import pandas as pd
from src.data.synthetic import SyntheticConfig, generate_dataset
from src.data.schema import validate_columns
from src.preprocessing.pipeline import preprocess_sensor_frame, butter_filter
from src.preprocessing.windowing import make_windows
from src.features.extraction import extract_features
from src.datasets.sensor_dataset import split_indices


def tiny_data(): return generate_dataset(SyntheticConfig(journeys=6,duration=4,sampling_rate=20,seed=9))


def test_generation_and_validation():
    frame=tiny_data(); validate_columns(frame.columns)
    assert frame.data_source.eq("SYNTHETIC").all() and frame.journey_id.nunique()==6


def test_filter_and_window_features():
    processed=preprocess_sensor_frame(tiny_data(),high_hz=8)
    assert np.isfinite(processed[["accel_mag","gyro_mag"]]).all().all()
    windows,labels,meta=make_windows(processed,window_seconds=1,stride_seconds=.5)
    features,names=extract_features(windows,sampling_rate=20)
    assert windows.shape[2]==8 and len(features)==len(labels)==len(meta) and len(names)==features.shape[1]


def test_group_split_has_no_journey_leakage():
    processed=preprocess_sensor_frame(tiny_data(),high_hz=8); windows,labels,meta=make_windows(processed,1,.5)
    train,val,test=split_indices(labels,meta,"journey")
    assert set(meta.iloc[train].journey_id).isdisjoint(meta.iloc[test].journey_id)

