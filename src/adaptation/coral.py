import numpy as np


def _matrix_power(matrix, power):
    values, vectors = np.linalg.eigh(matrix)
    return (vectors * np.maximum(values, 1e-8) ** power) @ vectors.T


def coral_transform(source, target):
    """Align source feature covariance to an unlabeled target domain (CORAL)."""
    source, target = np.asarray(source, float), np.asarray(target, float)
    source_centered, target_centered = source - source.mean(0), target - target.mean(0)
    source_cov = np.cov(source_centered, rowvar=False) + np.eye(source.shape[1]) * 1e-5
    target_cov = np.cov(target_centered, rowvar=False) + np.eye(target.shape[1]) * 1e-5
    aligned = source_centered @ _matrix_power(source_cov, -.5) @ _matrix_power(target_cov, .5)
    return (aligned + target.mean(0)).astype(np.float32)

