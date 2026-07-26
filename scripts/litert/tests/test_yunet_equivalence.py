import importlib.util
import unittest
from pathlib import Path

import numpy as np


MODULE_PATH = Path(__file__).resolve().parents[1] / "compare_yunet.py"
SPEC = importlib.util.spec_from_file_location("compare_yunet", MODULE_PATH)
compare_yunet = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(compare_yunet)


def row(x: float, score: float) -> np.ndarray:
    value = np.zeros(15, dtype=np.float32)
    value[0] = x
    value[14] = score
    return value


class YuNetEquivalenceTest(unittest.TestCase):
    def test_stable_score_and_coordinate_order_matches_golden_rows(self):
        reference = np.stack([row(20.0, 0.8), row(10.0, 0.9)])
        candidate = np.stack([row(10.0001, 0.90001), row(20.0001, 0.80001)])
        result = compare_yunet.compare_rows(reference, candidate, 0.001, 0.0001)
        self.assertTrue(result["passed"])
        self.assertEqual(result["detectionCount"], 2)

    def test_detection_count_mismatch_is_explicit(self):
        result = compare_yunet.compare_rows(
            np.stack([row(10.0, 0.9)]),
            np.empty((0, 15), dtype=np.float32),
            0.001,
            0.0001,
        )
        self.assertEqual(result, {"passed": False, "reason": "DETECTION_COUNT_MISMATCH"})

    def test_invalid_shape_or_nonfinite_output_is_rejected(self):
        with self.assertRaises(ValueError):
            compare_yunet.stable_rows(np.zeros((1, 14), dtype=np.float32))
        invalid = row(1.0, 0.9)
        invalid[3] = np.nan
        with self.assertRaises(ValueError):
            compare_yunet.stable_rows(np.stack([invalid]))


if __name__ == "__main__":
    unittest.main()
