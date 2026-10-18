# Contributing to RailSense

RailSense welcomes reproducible improvements to Android monitoring, signal processing, evaluation and documentation. Describe the problem, expected behavior and evidence in an issue or pull request. Do not attach private journey locations, credentials or signing keys to public issues.

## Development setup

Use JDK 17 and Android SDK 35 for `mobile/`. The Gradle wrapper is committed; install the SDK and configure `mobile/local.properties` locally. Use Python 3.10–3.12 for the documented research environment, and Node.js 22.12 or newer for the Vite dashboard.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev,xgboost]"
Set-Location frontend
npm ci
npm run build
```

## Validate a change

Run the checks appropriate to the files you changed. From the repository root:

```powershell
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m unittest tests.test_mobile_model -v
Set-Location mobile
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# Requires a connected Android device or emulator:
.\gradlew.bat :app:connectedDebugAndroidTest
```

State exactly which checks ran. A compiled instrumentation APK is not an executed device test. Keep synthetic fixtures clearly labeled, and never report synthetic scores as field accuracy.

## Design contracts

- Preserve monotonic timestamps, explicit missing values, bounded live buffers and quality gating.
- Keep sensor collection and file/database operations off the UI thread.
- Update Python and Kotlin together if changing the mobile feature contract, and validate numerical agreement.
- Keep entire journeys together during research evaluation; fit learned preprocessing on training data.
- Preserve Room migrations and existing user records.
- Keep GPS excluded from exports unless the user selects it.
- Record models, preprocessing, configuration and data provenance in experimental outputs.

Generated data, model weights, APKs, SDK paths, IDE files and credentials are intentionally excluded from source control. Publish distributable APKs as release assets. Never commit a release signing key.

The repository uses the [MIT license](LICENSE). Contributions should include documentation and meaningful checks for changed behavior.
