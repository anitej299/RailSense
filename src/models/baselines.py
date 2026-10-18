from __future__ import annotations

from sklearn.ensemble import RandomForestClassifier, HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.svm import SVC


def baseline_models(include_xgboost=True, seed=42):
    models = {
        "logistic_regression": make_pipeline(StandardScaler(), LogisticRegression(max_iter=1000, class_weight="balanced", random_state=seed)),
        "random_forest": RandomForestClassifier(n_estimators=120, class_weight="balanced", random_state=seed, n_jobs=-1),
        "svm": make_pipeline(StandardScaler(), SVC(probability=True, class_weight="balanced", random_state=seed)),
    }
    if include_xgboost:
        try:
            from xgboost import XGBClassifier
            models["xgboost"] = XGBClassifier(n_estimators=100, max_depth=4, learning_rate=.08, n_jobs=2, random_state=seed)
        except ImportError:
            models["hist_gradient_boosting_fallback"] = HistGradientBoostingClassifier(random_state=seed)
    return models

