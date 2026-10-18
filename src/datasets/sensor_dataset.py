from __future__ import annotations

import numpy as np
from sklearn.model_selection import train_test_split

try:
    import torch
    from torch.utils.data import Dataset
except ImportError:  # Group splitting remains usable in signal-only environments.
    torch = None
    class Dataset:  # type: ignore[no-redef]
        pass


class SensorDataset(Dataset):
    def __init__(self, windows, labels):
        if torch is None:
            raise RuntimeError("PyTorch is required for SensorDataset")
        self.windows = torch.as_tensor(windows, dtype=torch.float32)
        self.labels = torch.as_tensor(labels, dtype=torch.long)

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, index):
        return self.windows[index], self.labels[index]


def split_indices(labels, metadata, protocol="journey", seed=42):
    n = len(labels)
    all_idx = np.arange(n)
    if protocol == "random":
        train, test = train_test_split(all_idx, test_size=.2, random_state=seed, stratify=labels if len(np.unique(labels)) > 1 else None)
        train, val = train_test_split(train, test_size=.2, random_state=seed)
        return train, val, test
    if protocol == "speed":
        speeds = metadata["speed"].to_numpy(float)
        low, high = np.quantile(speeds, [.60, .80])
        train = all_idx[speeds <= low]
        val = all_idx[(speeds > low) & (speeds <= high)]
        test = all_idx[speeds > high]
        if min(len(train), len(val), len(test)) == 0:
            raise ValueError("speed split produced an empty partition")
        return train, val, test
    column = {"journey": "journey_id", "device": "device_model", "placement": "placement_id", "route": "route_id"}.get(protocol)
    if column is None:
        raise ValueError(f"Unsupported split protocol: {protocol}")
    groups = sorted(metadata[column].unique())
    if len(groups) < 3:
        raise ValueError(f"{protocol} split requires at least 3 groups")
    test_group, val_group = groups[-1], groups[-2]
    test = all_idx[metadata[column].to_numpy() == test_group]
    val = all_idx[metadata[column].to_numpy() == val_group]
    train = all_idx[~metadata[column].isin([test_group, val_group]).to_numpy()]
    return train, val, test
