from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np


def preprocess_bgr(image_bgr: np.ndarray) -> np.ndarray:
    if image_bgr.shape != (112, 112, 3):
        raise ValueError(f"expected 112x112x3 BGR input, got {image_bgr.shape}")
    if not np.isfinite(image_bgr).all():
        raise ValueError("input contains non-finite values")
    return ((image_bgr.astype(np.float32) - 127.5) / 128.0).transpose(2, 0, 1)[None]


def normalize(embedding: np.ndarray) -> np.ndarray:
    vector = np.asarray(embedding, dtype=np.float32).reshape(-1)
    if not np.isfinite(vector).all():
        raise ValueError("embedding contains non-finite values")
    norm = np.linalg.norm(vector)
    if norm == 0:
        raise ValueError("embedding norm is zero")
    return vector / norm


def cosine(left: np.ndarray, right: np.ndarray) -> float:
    return float(np.dot(normalize(left), normalize(right)))


def compare_embeddings(
    reference: np.ndarray,
    candidate: np.ndarray,
    minimum_cosine: float,
    decision_threshold: float,
) -> dict[str, object]:
    similarity = cosine(reference, candidate)
    reference_decision = cosine(reference, reference) >= decision_threshold
    candidate_decision = similarity >= decision_threshold
    return {
        "cosine": similarity,
        "minimumCosine": minimum_cosine,
        "cosinePassed": similarity >= minimum_cosine,
        "decisionThreshold": decision_threshold,
        "referenceDecision": reference_decision,
        "candidateDecision": candidate_decision,
        "decisionMatched": reference_decision == candidate_decision,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("reference", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--minimum-cosine", type=float, default=0.999)
    parser.add_argument("--decision-threshold", type=float, default=0.6)
    args = parser.parse_args()
    result = compare_embeddings(
        np.load(args.reference),
        np.load(args.candidate),
        args.minimum_cosine,
        args.decision_threshold,
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["cosinePassed"] and result["decisionMatched"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
