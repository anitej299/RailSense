from __future__ import annotations
from pathlib import Path
import json, logging
import pandas as pd
from fastapi import FastAPI, HTTPException, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
from api.schemas import SensorWindow, PredictionResponse
from api.state import service
from src.config import ROOT
from src.inference.segments import aggregate_segments

logging.basicConfig(level=logging.INFO,format='%(asctime)s %(levelname)s %(message)s')
app=FastAPI(title="RailSense API",version="1.0.0",description="Research API for potential vibration anomaly screening")
app.add_middleware(CORSMiddleware,allow_origins=["http://localhost:5173","http://localhost:3000"],allow_methods=["*"],allow_headers=["*"])

def _csv(name):
    path=ROOT/"results"/"generated"/name
    return pd.read_csv(path).replace({float("nan"):None}).to_dict("records") if path.exists() else []

@app.get("/health")
def health(): return {"status":"ok","model_loaded":service.model is not None}
@app.get("/models")
def models(): return service.info
@app.get("/metrics")
def metrics(): return _csv("model_comparison.csv")
@app.post("/predict",response_model=PredictionResponse)
def predict(window:SensorWindow):
    try:return service.predict(window.values)
    except (ValueError,RuntimeError) as error: raise HTTPException(422,str(error)) from error
@app.post("/predict/batch")
def predict_batch(windows:list[SensorWindow]): return [predict(item) for item in windows]
@app.post("/upload")
async def upload(file:UploadFile=File(...)):
    if not file.filename or not file.filename.endswith(".csv"): raise HTTPException(415,"CSV files only")
    payload=await file.read(); destination=ROOT/"data"/"raw"/Path(file.filename).name; destination.parent.mkdir(parents=True,exist_ok=True); destination.write_bytes(payload)
    return {"filename":destination.name,"bytes":len(payload),"label":"Real-world smartphone dataset (unvalidated upload)"}
@app.get("/journeys")
def journeys():
    path=ROOT/"data"/"processed"/"metadata.csv"
    if not path.exists(): return []
    frame=pd.read_csv(path); return frame.groupby("journey_id").agg(windows=("journey_id","size"),route_id=("route_id","first"),device=("device_model","first")).reset_index().to_dict("records")
@app.get("/journeys/{journey_id}")
def journey(journey_id:str):
    matches=[item for item in journeys() if item["journey_id"]==journey_id]
    if not matches: raise HTTPException(404,"Journey not found")
    return matches[0]
@app.get("/segments")
def segments(): return _demo_segments()
@app.get("/segments/{segment_id}")
def segment(segment_id:str):
    matches=[item for item in _demo_segments() if item["segment_id"]==segment_id]
    if not matches: raise HTTPException(404,"Segment not found")
    return matches[0]
@app.get("/anomalies")
def anomalies(): return [x for x in _demo_segments() if x["average_risk"]>=.4]
@app.get("/map-data")
def map_data(): return {"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[x["longitude"],x["latitude"]]},"properties":x} for x in _demo_segments()]}
@app.get("/experiments")
def experiments():
    summary=ROOT/"results"/"generated"/"summary.json"; return json.loads(summary.read_text()) if summary.exists() else {"status":"not_run"}

def _demo_segments():
    meta=ROOT/"data"/"processed"/"metadata.csv"
    if not meta.exists(): return []
    frame=pd.read_csv(meta); frame["risk_score"] = ((frame.index.to_numpy() % 11) / 10).clip(0, 1)
    return aggregate_segments(frame).to_dict("records")
