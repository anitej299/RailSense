from pathlib import Path
import json

topics=["01_dataset_exploration","02_signal_processing","03_frequency_analysis","04_baseline_models","05_deep_model_comparison","06_robustness_analysis","07_ablation_analysis","08_domain_adaptation"]
template=lambda title:{"cells":[{"cell_type":"markdown","metadata":{},"source":[f"# {title.replace('_',' ').title()}\n","This notebook calls reusable RailSense modules; generated results are synthetic unless explicitly labelled otherwise."]},{"cell_type":"code","execution_count":None,"metadata":{},"outputs":[],"source":["from pathlib import Path\n","import pandas as pd\n","RESULTS = Path('../results/generated')\n","sorted(p.name for p in RESULTS.glob('*')) if RESULTS.exists() else 'Run experiments/run_all.py first'\n"]}],"metadata":{"kernelspec":{"display_name":"Python 3","language":"python","name":"python3"},"language_info":{"name":"python","version":"3.11"}},"nbformat":4,"nbformat_minor":5}
root=Path(__file__).resolve().parents[1]/"notebooks";root.mkdir(exist_ok=True)
for topic in topics:(root/f"{topic}.ipynb").write_text(json.dumps(template(topic),indent=2))
print(f"Created {len(topics)} notebooks")

