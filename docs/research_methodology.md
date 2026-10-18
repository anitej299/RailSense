# RailSense Research Methodology

## Abstract

RailSense is a reproducible research prototype testing whether lightweight temporal models can identify potential railway vibration anomalies from commodity-smartphone accelerometer, gyroscope, GPS, and speed data across changes in device, placement, route, speed, and journey. It is not a certified inspection system.

## Research gap and question

Prior feasibility demonstrations do not alone establish robustness to hardware transfer, arbitrary placement, operating speed, or journey leakage. RailSense asks whether a lightweight model can retain useful anomaly-recall and latency characteristics under held-out conditions while operating locally on a phone.

## Hypotheses

H1–H5 are encoded as comparisons, never assumptions: deep versus engineered-feature baselines; accelerometer versus gyroscope versus fusion; within-domain versus held-out domains; preprocessing/adaptation versus no adaptation; and accuracy–latency comparison of CNN-BiLSTM with alternatives.

## Data acquisition

The current Android client requests a configurable sampling interval (default 50 Hz), retains monotonic timestamps and corresponding wall times, and associates optional GPS/speed with motion readings. Actual delivery rate is measured. Placement and route labels are research metadata and must be supplied in the collection workflow. Researchers must obtain operator permission, avoid passenger PII, secure devices, and never distract train staff.

Synthetic generation combines periodic vibration, speed-dependent amplitude, impulses/ringing, sensor noise/bias/scale, placement rotations, GPS trajectories, missing data, and timestamp jitter. It is always labelled **SYNTHETIC**.

## Signal processing and model architecture

Records are ordered per journey, deduplicated, interpolated conservatively, detrended, Butterworth-filtered, transformed to orientation-tolerant channels, and segmented into overlapping windows. Engineered time/spectral features feed logistic regression, random forest, SVM, and XGBoost/fallback. Raw windows feed CNN, LSTM, attention CNN-BiLSTM, and lightweight Transformer models.

## Experimental design

The primary split holds out entire journeys. Device, placement, route, and random comparisons quantify generalization and leakage optimism. Speed distribution tests, noise/missingness/downsampling perturbations, window-length variants, sensor ablations, and CORAL feature alignment test robustness. Metrics emphasize recall, false-negative rate, PR-AUC, balanced accuracy, latency, size, and parameter count.

## Edge deployment

The research runner can export a selected deep candidate to ONNX. That research artifact is not used by the current Android application, whose live pipeline uses an untrained statistical baseline or a separately supplied compatible centroid model. The Android pipeline uses its own DC-removal and seven-feature contract; the research preprocessing above must not be assumed interchangeable. Android requires consecutive eligible windows, enforces cooldown and issues local candidate notifications. See [the mobile model contract](model_training.md).

## Reproducibility

Configuration is externalized in YAML, seeds cover NumPy/PyTorch/scikit-learn, artifacts record environment versions, and `experiments/run_all.py` regenerates data through reports.

