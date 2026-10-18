from __future__ import annotations
import numpy as np
import torch


def tree_feature_importance(model, feature_names):
    candidate = model.steps[-1][1] if hasattr(model, "steps") else model
    values = getattr(candidate, "feature_importances_", None)
    if values is None:
        return []
    order = np.argsort(values)[::-1]
    return [{"feature": feature_names[i], "importance": float(values[i])} for i in order]


def temporal_saliency(model, window):
    model.eval(); sample=torch.tensor(window[None],dtype=torch.float32,requires_grad=True)
    logits=model(sample); predicted=int(logits.argmax(1)); logits[0,predicted].backward()
    return np.abs(sample.grad.detach().numpy()[0]).mean(axis=1), predicted
