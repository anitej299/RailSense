import numpy as np
from src.adaptation.coral import coral_transform
from src.robustness.perturbations import perturb_windows


def test_coral_shape_and_perturbation():
    rng=np.random.default_rng(1); source=rng.normal(size=(30,6)); target=rng.normal(2,2,size=(20,6))
    assert coral_transform(source,target).shape==source.shape
    windows=rng.normal(size=(4,20,8)).astype("float32")
    assert perturb_windows(windows,"sampling_rate").shape==windows.shape

