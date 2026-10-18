from __future__ import annotations

from copy import deepcopy
import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader
from src.datasets.sensor_dataset import SensorDataset


def train_model(model, train_data, val_data, config, device=None):
    device = device or ("cuda" if torch.cuda.is_available() else "cpu")
    model = model.to(device)
    train_x, train_y = train_data
    val_x, val_y = val_data
    train_loader = DataLoader(SensorDataset(train_x, train_y), batch_size=config.get("batch_size", 32), shuffle=True)
    val_loader = DataLoader(SensorDataset(val_x, val_y), batch_size=config.get("batch_size", 32))
    counts = np.bincount(train_y, minlength=4).astype(float)
    weights = counts.sum() / np.maximum(counts, 1)
    weights = weights / weights.mean()
    criterion = nn.CrossEntropyLoss(weight=torch.tensor(weights, dtype=torch.float32, device=device) if config.get("class_weighting", True) else None)
    optimizer = torch.optim.AdamW(model.parameters(), lr=config.get("learning_rate", 1e-3))
    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(optimizer, patience=1, factor=.5)
    scaler = torch.amp.GradScaler("cuda", enabled=device == "cuda" and config.get("mixed_precision", True))
    best_state, best_loss, remaining = None, float("inf"), config.get("patience", 3)
    history = []
    for epoch in range(config.get("epochs", 8)):
        model.train(); train_loss = 0.0
        for x, y in train_loader:
            x, y = x.to(device), y.to(device); optimizer.zero_grad(set_to_none=True)
            with torch.autocast(device_type=device, enabled=device == "cuda" and config.get("mixed_precision", True)):
                loss = criterion(model(x), y)
            scaler.scale(loss).backward(); scaler.unscale_(optimizer)
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(optimizer); scaler.update(); train_loss += loss.item() * len(y)
        model.eval(); val_loss = 0.0
        with torch.no_grad():
            for x, y in val_loader:
                x, y = x.to(device), y.to(device)
                val_loss += criterion(model(x), y).item() * len(y)
        val_loss /= max(1, len(val_y)); scheduler.step(val_loss)
        history.append({"epoch": epoch + 1, "train_loss": train_loss / max(1, len(train_y)), "val_loss": val_loss})
        if val_loss < best_loss - 1e-5:
            best_loss, best_state, remaining = val_loss, deepcopy(model.state_dict()), config.get("patience", 3)
        else:
            remaining -= 1
            if remaining <= 0: break
    if best_state: model.load_state_dict(best_state)
    return model.cpu(), history


def predict_proba(model, windows, batch_size=64):
    loader = DataLoader(SensorDataset(windows, np.zeros(len(windows))), batch_size=batch_size)
    model.eval(); outputs = []
    with torch.no_grad():
        for x, _ in loader:
            outputs.append(torch.softmax(model(x), dim=1).numpy())
    return np.concatenate(outputs) if outputs else np.empty((0, 4))

