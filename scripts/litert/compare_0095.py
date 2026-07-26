from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np


def preprocess_bgr(image_bgr: np.ndarray) -> np.ndarray:
    if image_bgr.shape != (128, 128, 3):
        raise ValueError(f"expected 128x128x3 BGR input, got {image_bgr.shape}")
    if not np.isfinite(image_bgr).all():
        raise ValueError("input contains non-finite values")
    # The converted 0095 graph retains the IR's BGR-to-RGB and /255 operations.
    return image_bgr.astype(np.float32).transpose(2, 0, 1)[None]


def normalize(embedding: np.ndarray) -> np.ndarray:
    vector = np.asarray(embedding, dtype=np.float32).reshape(-1)
    if vector.shape != (256,):
        raise ValueError(f"expected 256 values, got {vector.shape}")
    if not np.isfinite(vector).all():
        raise ValueError("embedding contains non-finite values")
    norm = np.linalg.norm(vector)
    if not np.isfinite(norm) or norm == 0:
        raise ValueError("embedding norm is zero or non-finite")
    return vector / norm


def compare_embeddings(
    reference: np.ndarray,
    candidate: np.ndarray,
    minimum_cosine: float,
    maximum_absolute_error: float,
) -> dict[str, object]:
    reference_vector = normalize(reference)
    candidate_vector = normalize(candidate)
    difference = np.abs(reference_vector - candidate_vector)
    cosine = float(np.dot(reference_vector, candidate_vector))
    maximum_error = float(difference.max(initial=0.0))
    return {
        "cosine": cosine,
        "minimumCosine": minimum_cosine,
        "maximumAbsoluteError": maximum_error,
        "maximumAllowedAbsoluteError": maximum_absolute_error,
        "passed": cosine >= minimum_cosine and maximum_error <= maximum_absolute_error,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("reference", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--minimum-cosine", type=float, default=0.9999)
    parser.add_argument("--maximum-absolute-error", type=float, default=1e-3)
    args = parser.parse_args()
    result = compare_embeddings(
        np.load(args.reference),
        np.load(args.candidate),
        args.minimum_cosine,
        args.maximum_absolute_error,
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
