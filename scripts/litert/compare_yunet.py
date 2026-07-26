from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np


def stable_rows(rows: np.ndarray) -> np.ndarray:
    values = np.asarray(rows, dtype=np.float32)
    if values.ndim != 2 or values.shape[1] != 15:
        raise ValueError(f"expected Nx15 YuNet rows, got {values.shape}")
    if not np.isfinite(values).all():
        raise ValueError("YuNet rows contain non-finite values")
    order = np.lexsort((values[:, 1], values[:, 0], -values[:, 14]))
    return values[order]


def compare_rows(
    reference: np.ndarray,
    candidate: np.ndarray,
    coordinate_tolerance: float,
    score_tolerance: float,
) -> dict[str, object]:
    left = stable_rows(reference)
    right = stable_rows(candidate)
    if left.shape != right.shape:
        return {"passed": False, "reason": "DETECTION_COUNT_MISMATCH"}
    coordinate_error = float(np.max(np.abs(left[:, :14] - right[:, :14]), initial=0.0))
    score_error = float(np.max(np.abs(left[:, 14] - right[:, 14]), initial=0.0))
    return {
        "passed": coordinate_error <= coordinate_tolerance and score_error <= score_tolerance,
        "detectionCount": int(left.shape[0]),
        "maximumCoordinateError": coordinate_error,
        "maximumScoreError": score_error,
        "coordinateTolerance": coordinate_tolerance,
        "scoreTolerance": score_tolerance,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("reference", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--coordinate-tolerance", type=float, default=1e-3)
    parser.add_argument("--score-tolerance", type=float, default=1e-4)
    args = parser.parse_args()
    result = compare_rows(
        np.load(args.reference),
        np.load(args.candidate),
        args.coordinate_tolerance,
        args.score_tolerance,
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
