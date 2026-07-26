import importlib.util
import unittest
from pathlib import Path

import numpy as np


MODULE_PATH = Path(__file__).resolve().parents[1] / "compare_sface.py"
SPEC = importlib.util.spec_from_file_location("compare_sface", MODULE_PATH)
compare_sface = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(compare_sface)


class SFaceEquivalenceTest(unittest.TestCase):
    def test_preprocessing_has_exact_nchw_shape_and_sface_scale(self):
        image = np.full((112, 112, 3), 127.5, dtype=np.float32)
        output = compare_sface.preprocess_bgr(image)
        self.assertEqual(output.shape, (1, 3, 112, 112))
        self.assertEqual(np.max(np.abs(output)), 0)

    def test_cosine_and_decision_agreement_pass_golden_boundary(self):
        reference = np.array([1.0, 0.0], dtype=np.float32)
        candidate = np.array([0.9999, 0.01], dtype=np.float32)
        result = compare_sface.compare_embeddings(reference, candidate, 0.999, 0.6)
        self.assertTrue(result["cosinePassed"])
        self.assertTrue(result["decisionMatched"])

    def test_nonfinite_or_zero_embedding_is_rejected(self):
        with self.assertRaises(ValueError):
            compare_sface.normalize(np.array([np.nan, 0.0], dtype=np.float32))
        with self.assertRaises(ValueError):
            compare_sface.normalize(np.zeros(2, dtype=np.float32))


if __name__ == "__main__":
    unittest.main()
