from __future__ import annotations

from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np


def comparison_chart(rows, output):
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    names = [r["model"] for r in rows]; values = [r["f1_macro"] for r in rows]
    fig, ax = plt.subplots(figsize=(8, 4)); ax.bar(names, values, color="#0ea5a4")
    ax.set_ylabel("Macro F1"); ax.set_ylim(0, 1); ax.tick_params(axis="x", rotation=30)
    fig.tight_layout(); fig.savefig(output, dpi=150); plt.close(fig)


def signal_spectrum_plot(normal, anomaly, rate, output):
    Path(output).parent.mkdir(parents=True, exist_ok=True)
    fig, axes = plt.subplots(2, 2, figsize=(10, 6))
    for row, values, title in [(0, normal, "Normal"), (1, anomaly, "Potential anomaly")]:
        axes[row,0].plot(np.arange(len(values))/rate, values); axes[row,0].set_title(f"{title} waveform")
        freq = np.fft.rfftfreq(len(values), 1/rate); power = np.abs(np.fft.rfft(values))**2
        axes[row,1].plot(freq, power); axes[row,1].set_title(f"{title} FFT")
    fig.tight_layout(); fig.savefig(output, dpi=150); plt.close(fig)

