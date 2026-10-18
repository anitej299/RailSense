# RailSense documentation

Start with the [project overview](../README.md). The native Android application and Python research platform have different inference contracts; research model results do not describe the default APK.

| Guide | What you will learn |
| --- | --- |
| [Android setup and usage](../mobile/README.md) | Build, install, monitor, change settings, export and use demonstration mode |
| [Architecture](architecture.md) | Sensor synchronization, worker queues, windows, quality checks, persistence and lifecycle |
| [Model contract and training](model_training.md) | Baseline formula, seven-feature centroid contract and Python-to-Android compatibility |
| [UI and screen behavior](ui_redesign.md) | Eight screen types, navigation, charts, export privacy and implementation files |
| [Permissions](android_permissions.md) | Location, foreground monitoring, notifications and file sharing |
| [Android data collection](data_collection.md) | Record and export phone data |
| [Research collection protocol](data_collection_protocol.md) | Provenance, placements, journeys and independent labels |
| [Research methodology](research_methodology.md) | Model comparisons, grouped evaluation and domain shift |
| [Research outputs](experimental_results.md) | Generate experimental tables and reports |
| [Test instructions](testing.md) | Automated commands and device validation checklist |
| [Version 3 verification](ui_verification.md) | Checks actually executed and remaining device validation |
| [Earlier Android verification](android_verification.md) | Historical version 2 build evidence |
| [Limitations](limitations.md) | Scientific, hardware, deployment and privacy boundaries |
| [Contributing](../CONTRIBUTING.md) | Development setup and change validation |

Machine-specific paths in historical verification reports describe the original development environment. Use your own checkout, SDK and JDK locations when reproducing them.
