import numpy as np


def perturb_windows(windows, kind, severity=.15, seed=42):
    rng = np.random.default_rng(seed); result = np.array(windows, copy=True)
    if kind == "noise": result += rng.normal(0, severity, result.shape)
    elif kind == "missing": result[rng.random(result.shape) < severity] = 0
    elif kind == "sampling_rate":
        down = result[:, ::2, :]
        positions = np.linspace(0, down.shape[1]-1, result.shape[1])
        result = np.stack([np.stack([np.interp(positions, np.arange(down.shape[1]), sample[:, c]) for c in range(down.shape[2])], axis=1) for sample in down])
    else: raise ValueError(f"Unknown perturbation: {kind}")
    return result.astype(np.float32)

