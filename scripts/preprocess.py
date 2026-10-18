from pathlib import Path
import pandas as pd
import numpy as np
from src.preprocessing.pipeline import preprocess_sensor_frame
from src.preprocessing.windowing import make_windows

if __name__ == "__main__":
    raw=pd.read_csv("data/synthetic/sensors.csv"); processed=preprocess_sensor_frame(raw)
    windows,labels,metadata=make_windows(processed); Path("data/processed").mkdir(parents=True,exist_ok=True)
    np.savez_compressed("data/processed/windows.npz",windows=windows,labels=labels); metadata.to_csv("data/processed/metadata.csv",index=False)
    print(f"Created {len(windows)} windows")

