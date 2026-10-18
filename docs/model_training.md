# Android model contract and replacement

## Default baseline

`experimental-baseline-v2` is a deliberately untrained engineering baseline. It computes `max(RMS / 4 m/s², magnitude peak-to-peak / 12 m/s²)`, clipped to [0,1]. The default decision threshold is 0.65. There is no learned normalization or calibrated confidence. It runs on CPU without a model file or network.

## Portable normal-only ML

`scripts/train_mobile_centroid.py` trains a diagonal standardized-distance model on normal-only feature windows. This is the mobile alternative to scikit-learn Isolation Forest, whose Python object cannot execute directly in Android. It uses a JSON numeric contract, no Python runtime in the app. A neural model is not added merely to make an unsupported training claim; existing research ONNX models use a different raw-window preprocessing contract and are not loaded by the new app.

Feature order is fixed:

| Index | Name | Units |
|---|---|---|
| 0 | rms_m_s2 | m/s², DC-removed vector magnitude |
| 1 | std_m_s2 | m/s², population SD of magnitude |
| 2 | peak_to_peak_m_s2 | m/s², max minus min magnitude |
| 3 | jerk_rms_m_s3 | m/s³, vector differences divided by monotonic dt |
| 4 | gyro_rms_rad_s | rad/s, vector magnitude RMS |
| 5 | dominant_hz | Hz, dominant non-DC magnitude DFT bin |
| 6 | high_band_ratio | unitless, power ≥8 Hz / non-DC power |

Preprocessing is `window-dc-magnitude-v1` in both Python and Kotlin. Normalization means and population scales are learned per feature (minimum scale 1e-4). Distance is `sqrt(mean(((x-mean)/scale)^2))`. The 95th percentile training distance maps to score 0.65; scores are clipped to [0,1]. This is a training heuristic, not an estimate of anomaly probability or validation performance. Metadata records the expected rate/window, decision threshold, training window count, provenance, interpretation and version. Android rejects wrong schemas, features, configurations, dimensions, nonfinite means, nonpositive scales or a missing model.

```powershell
# Run from repository root; standard-library Python, no GPU required.
python scripts/train_mobile_centroid.py --synthetic --output .tools/centroid-test.json
python -m unittest tests.test_mobile_model -v
# Only after selecting a NORMAL training journey:
python scripts/train_mobile_centroid.py --input normal-trip.csv --normal-only --hz 50 --window 2 --output mobile/app/src/main/assets/centroid.json
cd mobile
.\gradlew.bat :app:installDebug
```

Set the matching rate/window and **Trained centroid** model in Settings. Synthetic export can also be copied into assets for a deployment demonstration, but its version and metadata explicitly include `synthetic`. The baseline works without this file. The settings threshold remains user-configurable; use the metadata's recommended value initially. The centroid requires synchronized gyro and regular timing; ineligible windows show quality but no candidate inference.

For real research, reserve entire journeys/devices/routes for validation, keep overlapping windows within one split, fit all normalization on training only, and report false candidate rate per hour, event recall against authorized independent inspection labels, localization error and cross-device transfer. Do not call vibration peaks damage labels. No railway dataset, accuracy, sensitivity, or safety certification is provided by this implementation.

To integrate a neural runtime later, implement `AnomalyModel`, publish its input contract and training/conversion script, compare Android outputs to Python reference vectors, measure on a physical device, and reject incompatible assets. Do not rename a score “confidence” without a calibration study.
