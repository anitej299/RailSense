from __future__ import annotations

import numpy as np
from sklearn.metrics import accuracy_score, balanced_accuracy_score, precision_recall_fscore_support, roc_auc_score, average_precision_score, confusion_matrix


def classification_metrics(y_true, probabilities):
    y_true, probabilities = np.asarray(y_true), np.asarray(probabilities)
    y_pred = probabilities.argmax(axis=1)
    precision, recall, f1, _ = precision_recall_fscore_support(y_true, y_pred, average="macro", zero_division=0)
    anomaly_true = (y_true > 0).astype(int)
    anomaly_score = 1 - probabilities[:, 0]
    fn = np.sum((anomaly_true == 1) & (y_pred == 0)); positives = max(1, anomaly_true.sum())
    try:
        roc_auc = roc_auc_score(anomaly_true, anomaly_score)
        pr_auc = average_precision_score(anomaly_true, anomaly_score)
    except ValueError:
        roc_auc = pr_auc = float("nan")
    return {
        "accuracy": float(accuracy_score(y_true, y_pred)), "balanced_accuracy": float(balanced_accuracy_score(y_true, y_pred)),
        "precision_macro": float(precision), "recall_macro": float(recall), "f1_macro": float(f1),
        "roc_auc_anomaly": float(roc_auc), "pr_auc_anomaly": float(pr_auc),
        "false_negative_rate": float(fn / positives), "confusion_matrix": confusion_matrix(y_true, y_pred, labels=[0,1,2,3]).tolist(),
    }

