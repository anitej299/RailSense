from __future__ import annotations
from pathlib import Path
import json, torch
from src.config import ROOT
from src.data.schema import LABELS
from src.models.deep import build_model
from src.inference.risk import RiskEngine


class ModelService:
    def __init__(self):
        self.model=None; self.info={"status":"not_loaded"}; self.risk=RiskEngine()
        path=ROOT/"models"/"best_model.pt"
        if path.exists():
            checkpoint=torch.load(path,map_location="cpu",weights_only=True)
            self.model=build_model(checkpoint["model_name"],checkpoint["channels"],checkpoint["classes"],checkpoint["hidden"])
            self.model.load_state_dict(checkpoint["state_dict"]); self.model.eval()
            self.info={k:checkpoint[k] for k in ["model_name","channels","classes","window_shape","data_source"]}

    def predict(self, values):
        if self.model is None: raise RuntimeError("No trained model. Run python experiments/run_all.py --quick")
        tensor=torch.tensor([values],dtype=torch.float32)
        expected=self.info["window_shape"]
        if list(tensor.shape[1:]) != expected: raise ValueError(f"Expected window shape {expected}, got {list(tensor.shape[1:])}")
        with torch.no_grad(): probs=torch.softmax(self.model(tensor),dim=1)[0].numpy()
        decision=self.risk.evaluate(probs); index=int(probs.argmax())
        return {"prediction":LABELS[index],"confidence":float(probs[index]),"probabilities":dict(zip(LABELS,map(float,probs))),"risk_score":decision.risk_score,"risk_level":decision.risk_level,"should_alert":decision.should_alert}

service=ModelService()

