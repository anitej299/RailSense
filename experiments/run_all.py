from __future__ import annotations

import argparse, json, platform, sys, time
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
import torch

from src.config import ROOT, load_config
from src.data.synthetic import SyntheticConfig, generate_dataset
from src.preprocessing.pipeline import preprocess_sensor_frame
from src.preprocessing.windowing import make_windows, DEFAULT_CHANNELS
from src.features.extraction import extract_features
from src.datasets.sensor_dataset import split_indices
from src.models.baselines import baseline_models
from src.models.deep import build_model
from src.training.trainer import train_model, predict_proba
from src.evaluation.metrics import classification_metrics
from src.evaluation.efficiency import benchmark_model
from src.evaluation.plots import comparison_chart, signal_spectrum_plot
from src.evaluation.explainability import tree_feature_importance, temporal_saliency
from src.robustness.perturbations import perturb_windows
from src.adaptation.coral import coral_transform
from src.inference.export import export_onnx
from src.utils.reproducibility import seed_everything


def _json(value):
    if isinstance(value, (np.integer,)): return int(value)
    if isinstance(value, (np.floating,)): return float(value)
    if isinstance(value, np.ndarray): return value.tolist()
    raise TypeError(type(value).__name__)


def _probabilities(model, x):
    if hasattr(model, "predict_proba"):
        raw = model.predict_proba(x)
    else:
        decision = model.decision_function(x)
        exp = np.exp(decision - decision.max(1, keepdims=True)); raw = exp / exp.sum(1, keepdims=True)
    classes = np.asarray(model.classes_, dtype=int)
    if raw.shape[1] == 4 and np.array_equal(classes, np.arange(4)):
        return raw
    expanded = np.zeros((len(raw), 4), dtype=float)
    expanded[:, classes] = raw
    return expanded


def run(config_path=None, quick=False):
    cfg = load_config(config_path); seed_everything(cfg["seed"])
    output = ROOT / "results" / "generated"; output.mkdir(parents=True, exist_ok=True)
    models_dir = ROOT / "models"; models_dir.mkdir(exist_ok=True)
    data_path = ROOT / "data" / "synthetic" / "sensors.csv"
    d = cfg["data"]
    if quick:
        d = {**d, "journeys": max(6, min(8, d["journeys"])), "duration_seconds": min(12, d["duration_seconds"])}
        cfg["training"] = {**cfg["training"], "epochs": min(2, cfg["training"]["epochs"])}
    synthetic_cfg = SyntheticConfig(d["journeys"], d["duration_seconds"], d["sampling_rate"], tuple(d["devices"]), tuple(d["placements"]), tuple(d["routes"]), d["noise"], d["anomaly_rate"], cfg["seed"])
    raw = generate_dataset(synthetic_cfg, data_path)
    processed = preprocess_sensor_frame(raw, cfg["preprocessing"]["filter"], cfg["preprocessing"]["low_hz"], cfg["preprocessing"]["high_hz"], cfg["preprocessing"]["normalize_orientation"])
    windows, labels, metadata = make_windows(processed, cfg["preprocessing"]["window_seconds"], cfg["preprocessing"]["stride_seconds"])
    features, feature_names = extract_features(windows, d["sampling_rate"], DEFAULT_CHANNELS)
    np.savez_compressed(ROOT / "data" / "processed" / "windows.npz", windows=windows, labels=labels, features=features)
    metadata.to_csv(ROOT / "data" / "processed" / "metadata.csv", index=False)
    (ROOT / "data" / "processed" / "feature_names.json").write_text(json.dumps(feature_names))

    train, val, test = split_indices(labels, metadata, "journey", cfg["seed"])
    rows=[]; fitted_baselines={}
    for name, model in baseline_models(cfg["experiments"].get("include_xgboost", True), cfg["seed"]).items():
        start=time.perf_counter(); model.fit(features[train], labels[train]); elapsed=time.perf_counter()-start
        metrics=classification_metrics(labels[test], _probabilities(model, features[test]))
        row={"model": name, "family":"baseline", "protocol":"journey", "training_seconds":elapsed, **{k:v for k,v in metrics.items() if k != "confusion_matrix"}}
        rows.append(row); fitted_baselines[name]=model
    deep_models={}; histories={}; efficiencies={}
    deep_names = ["cnn", "lstm", "cnn_bilstm", "transformer"]
    for name in deep_names:
        model=build_model(name, windows.shape[2], 4, cfg["training"]["hidden_size"])
        start=time.perf_counter(); model, history=train_model(model, (windows[train],labels[train]), (windows[val],labels[val]), cfg["training"]); elapsed=time.perf_counter()-start
        metrics=classification_metrics(labels[test], predict_proba(model, windows[test]))
        rows.append({"model":name,"family":"deep","protocol":"journey","training_seconds":elapsed,**{k:v for k,v in metrics.items() if k != "confusion_matrix"}})
        deep_models[name]=model; histories[name]=history; efficiencies[name]=benchmark_model(model, windows[test][0], 10 if quick else 30)
    results=pd.DataFrame(rows).sort_values("f1_macro", ascending=False); results.to_csv(output/"model_comparison.csv", index=False)
    comparison_chart(rows, output/"model_comparison.png")

    protocols=[]
    rf=fitted_baselines["random_forest"]
    for protocol in ["random","journey","device","placement","speed","route"]:
        try:
            tr, va, te=split_indices(labels, metadata, protocol, cfg["seed"])
            candidate=baseline_models(False,cfg["seed"])["random_forest"].fit(features[tr],labels[tr])
            met=classification_metrics(labels[te],candidate.predict_proba(features[te]))
            protocols.append({"protocol":protocol,"train_windows":len(tr),"test_windows":len(te),**{k:v for k,v in met.items() if k!="confusion_matrix"}})
        except ValueError as error: protocols.append({"protocol":protocol,"status":str(error)})
    pd.DataFrame(protocols).to_csv(output/"cross_condition_generalization.csv",index=False)

    robustness=[]; base=classification_metrics(labels[test],rf.predict_proba(features[test]))["f1_macro"]
    for kind,severity in [("noise",.1),("noise",.25),("missing",.1),("missing",.25),("sampling_rate",.5)]:
        altered=perturb_windows(windows[test],kind,severity,cfg["seed"]); altered_features,_=extract_features(altered,d["sampling_rate"],DEFAULT_CHANNELS)
        score=classification_metrics(labels[test],rf.predict_proba(altered_features))["f1_macro"]
        robustness.append({"condition":kind,"severity":severity,"baseline_f1":base,"perturbed_f1":score,"absolute_drop":base-score,"relative_degradation":(base-score)/max(base,1e-9)})
    for window_seconds in [1.0, 3.0]:
        alt_windows, alt_labels, alt_meta = make_windows(processed, window_seconds, max(.5, window_seconds/2))
        alt_features, _ = extract_features(alt_windows, d["sampling_rate"], DEFAULT_CHANNELS)
        alt_train, alt_val, alt_test = split_indices(alt_labels, alt_meta, "journey", cfg["seed"])
        alt_model = baseline_models(False,cfg["seed"])["random_forest"].fit(alt_features[alt_train],alt_labels[alt_train])
        alt_score = classification_metrics(alt_labels[alt_test],_probabilities(alt_model,alt_features[alt_test]))["f1_macro"]
        robustness.append({"condition":"window_seconds","severity":window_seconds,"baseline_f1":base,"perturbed_f1":alt_score,"absolute_drop":base-alt_score,"relative_degradation":(base-alt_score)/max(base,1e-9)})
    pd.DataFrame(robustness).to_csv(output/"robustness.csv",index=False)

    ablations=[]
    variants={"accelerometer_only":[0,1,2,6],"gyroscope_only":[3,4,5,7],"accelerometer_gyroscope":list(range(8))}
    for name, channels in variants.items():
        f,_=extract_features(windows[:,:,channels],d["sampling_rate"],[DEFAULT_CHANNELS[i] for i in channels])
        model=baseline_models(False,cfg["seed"])["random_forest"].fit(f[train],labels[train])
        met=classification_metrics(labels[test],_probabilities(model,f[test]))
        ablations.append({"ablation":name,**{k:v for k,v in met.items() if k!="confusion_matrix"}})
    time_idx=[i for i,name in enumerate(feature_names) if not any(token in name for token in ["dominant_freq","spectral_"])]
    freq_idx=[i for i,name in enumerate(feature_names) if any(token in name for token in ["dominant_freq","spectral_"])]
    feature_variants={"time_domain_only":features[:,time_idx],"frequency_domain_only":features[:,freq_idx],"time_plus_frequency":features,"no_speed_information":features,"speed_aware_features":np.column_stack([features,metadata.speed.to_numpy()/max(metadata.speed.max(),1)])}
    for name, matrix in feature_variants.items():
        model=baseline_models(False,cfg["seed"])["random_forest"].fit(matrix[train],labels[train])
        met=classification_metrics(labels[test],_probabilities(model,matrix[test])); ablations.append({"ablation":name,**{k:v for k,v in met.items() if k!="confusion_matrix"}})
    raw_orientation=preprocess_sensor_frame(raw,cfg["preprocessing"]["filter"],cfg["preprocessing"]["low_hz"],cfg["preprocessing"]["high_hz"],False)
    raw_windows,raw_labels,raw_meta=make_windows(raw_orientation,cfg["preprocessing"]["window_seconds"],cfg["preprocessing"]["stride_seconds"])
    raw_features,_=extract_features(raw_windows,d["sampling_rate"],DEFAULT_CHANNELS); rtr,rva,rte=split_indices(raw_labels,raw_meta,"journey",cfg["seed"])
    for name,matrix,yy,tr_idx,te_idx in [("orientation_normalized",features,labels,train,test),("no_orientation_normalization",raw_features,raw_labels,rtr,rte)]:
        model=baseline_models(False,cfg["seed"])["random_forest"].fit(matrix[tr_idx],yy[tr_idx]);met=classification_metrics(yy[te_idx],_probabilities(model,matrix[te_idx]));ablations.append({"ablation":name,**{k:v for k,v in met.items() if k!="confusion_matrix"}})
    pd.DataFrame(ablations).to_csv(output/"ablation.csv",index=False)

    device_train, device_val, device_test=split_indices(labels,metadata,"device",cfg["seed"])
    source_idx=device_train; target_idx=device_test
    aligned=coral_transform(features[source_idx],features[target_idx])
    no_adapt=baseline_models(False,cfg["seed"])["logistic_regression"].fit(features[source_idx],labels[source_idx])
    adapt=baseline_models(False,cfg["seed"])["logistic_regression"].fit(aligned,labels[source_idx])
    adaptation=pd.DataFrame([
        {"method":"no_adaptation","target_domain":str(metadata.iloc[target_idx].device_model.iloc[0]),**{k:v for k,v in classification_metrics(labels[target_idx],_probabilities(no_adapt,features[target_idx])).items() if k!="confusion_matrix"}},
        {"method":"CORAL","target_domain":str(metadata.iloc[target_idx].device_model.iloc[0]),**{k:v for k,v in classification_metrics(labels[target_idx],_probabilities(adapt,features[target_idx])).items() if k!="confusion_matrix"}},
    ]); adaptation.to_csv(output/"domain_adaptation.csv",index=False)

    best_deep=max(deep_models, key=lambda name: next(row["f1_macro"] for row in rows if row["model"]==name))
    checkpoint={"model_name":best_deep,"channels":windows.shape[2],"classes":4,"hidden":cfg["training"]["hidden_size"],"state_dict":deep_models[best_deep].state_dict(),"window_shape":list(windows.shape[1:]),"data_source":"SYNTHETIC"}
    torch.save(checkpoint,models_dir/"best_model.pt"); joblib.dump(rf,models_dir/"random_forest.joblib")
    try: export_onnx(deep_models[best_deep],windows.shape[1:],models_dir/"railsense_mobile.onnx")
    except Exception as error: (output/"onnx_export_error.txt").write_text(str(error))
    try:
        quantized=torch.ao.quantization.quantize_dynamic(deep_models[best_deep],{torch.nn.Linear,torch.nn.LSTM},dtype=torch.qint8)
        efficiencies[f"{best_deep}_dynamic_int8"]=benchmark_model(quantized,windows[test][0],10 if quick else 30)
        torch.save(quantized.state_dict(),models_dir/"best_model_dynamic_int8.pt")
    except Exception as error: efficiencies[f"{best_deep}_dynamic_int8"]={"status":str(error)}
    pd.DataFrame([{"model":name,**values} for name,values in efficiencies.items()]).to_csv(output/"efficiency.csv",index=False)
    pd.DataFrame(tree_feature_importance(rf,feature_names)).to_csv(output/"tree_feature_importance.csv",index=False)
    saliency,predicted=temporal_saliency(deep_models[best_deep],windows[test][0]);np.save(output/"temporal_saliency.npy",saliency)
    (output/"temporal_saliency_metadata.json").write_text(json.dumps({"predicted_class":int(predicted),"window_index":int(test[0])},indent=2))
    normal=windows[np.where(labels==0)[0][0],:,6]; anomaly=windows[np.where(labels>0)[0][0],:,6]
    signal_spectrum_plot(normal,anomaly,d["sampling_rate"],output/"signal_frequency_comparison.png")
    statistics={"data_source":"Synthetic development benchmark","observations":len(raw),"windows":len(windows),"journeys":int(metadata.journey_id.nunique()),"devices":int(metadata.device_model.nunique()),"placements":int(metadata.placement_id.nunique()),"routes":int(metadata.route_id.nunique()),"class_counts":pd.Series(labels).value_counts().sort_index().to_dict()}
    (output/"dataset_statistics.json").write_text(json.dumps(statistics,indent=2,default=_json))
    summary={"data_source":"Synthetic development benchmark","best_model":str(results.iloc[0].model),"best_macro_f1":float(results.iloc[0].f1_macro),"best_mobile_candidate":best_deep,"environment":{"python":sys.version,"platform":platform.platform(),"torch":torch.__version__},"warning":"Results are synthetic development measurements, not evidence of railway safety or real-world defect detection."}
    (output/"summary.json").write_text(json.dumps(summary,indent=2))
    report="# RailSense Experimental Results\n\n> **Synthetic development benchmark.** These results are not real-world validation and do not establish railway safety.\n\n## Run summary\n\n"+"\n".join(f"- **{k.replace('_',' ').title()}:** {v}" for k,v in summary.items() if k!="environment")+"\n\n## Model comparison\n\n"+results.to_markdown(index=False)+"\n\n## Cross-condition experiments\n\n"+pd.DataFrame(protocols).to_markdown(index=False)+"\n\n## Robustness\n\n"+pd.DataFrame(robustness).to_markdown(index=False)+"\n\n## Ablation\n\n"+pd.DataFrame(ablations).to_markdown(index=False)+"\n\n## Domain adaptation\n\n"+adaptation.to_markdown(index=False)
    (output/"experiment_report.md").write_text(report)
    print(json.dumps(summary,indent=2)); return summary


if __name__ == "__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("--config"); parser.add_argument("--quick",action="store_true"); args=parser.parse_args(); run(args.config,args.quick)
