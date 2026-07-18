import importlib.util
from pathlib import Path
import sys
import unittest

MODULE_PATH = Path(__file__).parents[1] / "metrics.py"
SPEC = importlib.util.spec_from_file_location("face_metrics", MODULE_PATH)
metrics = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = metrics
SPEC.loader.exec_module(metrics)


class MetricsTest(unittest.TestCase):
    def test_threshold_and_margin_return_unknown(self):
        self.assertEqual("Unknown", metrics.decide({"A": 0.59, "B": 0.2}, 0.6, 0.05).predicted_id)
        self.assertEqual("Unknown", metrics.decide({"A": 0.8, "B": 0.78}, 0.6, 0.05).predicted_id)
        self.assertEqual("A", metrics.decide({"A": 0.8, "B": 0.7}, 0.6, 0.05).predicted_id)

    def test_priority_metrics(self):
        rows = [
            {"expected_id": "A", "predicted_id": "A"},
            {"expected_id": "B", "predicted_id": "A"},
            {"expected_id": "A", "predicted_id": "Unknown"},
            {"expected_id": "Unknown", "predicted_id": "A"},
            {"expected_id": "Unknown", "predicted_id": "Unknown"},
        ]
        value = metrics.summarize(rows)
        self.assertAlmostEqual(0.5, value["far"])
        self.assertAlmostEqual(1 / 3, value["misidentification_rate"])
        self.assertAlmostEqual(1 / 3, value["identification_accuracy"])
        self.assertAlmostEqual(1 / 3, value["frr"])

    def test_eer_selects_closest_far_frr(self):
        rows = [
            {"threshold": 0.4, "minimum_margin": 0.0, "far": 0.4, "frr": 0.1},
            {"threshold": 0.5, "minimum_margin": 0.0, "far": 0.2, "frr": 0.2},
        ]
        self.assertEqual(0.5, metrics.select_eer(rows)["threshold"])


if __name__ == "__main__":
    unittest.main()
