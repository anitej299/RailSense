# Build Verification Record

Verification performed in the delivery environment:

- Python source compilation: passed for `src/`, `api/`, `experiments/`, `scripts/`, and `tests/`.
- Synthetic generation: passed (UTC timestamps, six journeys in smoke run, four configured classes).
- Signal processing: passed (interpolation, detrending, band-pass filtering, magnitudes, orientation-tolerant channels).
- Window/feature smoke test: passed with finite `(88, 40, 8)` raw windows and `(88, 96)` engineered features.
- Split checks: passed for random, journey, device, placement, speed, and route protocols; group holdouts were disjoint.
- Traditional-model smoke fit and metric calculation: passed.
- Robustness perturbation, CORAL alignment, risk cooldown, and GPS aggregation checks: passed.
- Notebook JSON and Android XML validation: passed.

The delivery sandbox did not contain PyTorch, FastAPI, ONNX, Node packages, Gradle, or an Android SDK, and its package network rejected dependency downloads. Consequently the deep-training run, API tests, production frontend build, and Android APK build could not be executed here. They are not reported as passing. Run the README quick-start commands on a connected development machine to generate the full measured report.

