import json
import sys
import unittest
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import compare_0095


class Face0095EquivalenceTest(unittest.TestCase):
    def test_manifest_pins_source_output_and_exact_tensors(self):
        manifest = json.loads(
            (ROOT / "manifests" / "face-0095-litert.json").read_text(encoding="utf-8")
        )

        self.assertEqual(
            manifest["source"]["sha256"],
            "861d2edc47214f19fe973f97a05b2bd8ba61e103279fe232f0365534903dd589",
        )
        self.assertEqual(
            manifest["output"]["sha256"],
            "6ad2a160ab016b84a55442dcb2cd1d36b684aa9e8324357411b14a77756c68d8",
        )
        self.assertEqual(manifest["output"]["fileSizeBytes"], 4_475_508)
        self.assertEqual(
            manifest["output"]["inspection"]["inputs"],
            [{"elementType": "FLOAT32", "name": "0", "shape": [1, 3, 128, 128]}],
        )
        self.assertEqual(
            manifest["output"]["inspection"]["outputs"],
            [{"elementType": "FLOAT32", "name": "Identity", "shape": [1, 1, 1, 256]}],
        )
        self.assertEqual(manifest["verification"]["numericEquivalence"], "PASS")

    def test_preprocessing_is_raw_bgr_nchw_128(self):
        image = np.zeros((128, 128, 3), dtype=np.float32)
        image[0, 0] = [1.0, 127.0, 255.0]

        output = compare_0095.preprocess_bgr(image)

        self.assertEqual(output.shape, (1, 3, 128, 128))
        np.testing.assert_array_equal(output[0, :, 0, 0], [1.0, 127.0, 255.0])

    def test_finite_256_embedding_is_l2_normalized(self):
        normalized = compare_0095.normalize(np.arange(1, 257, dtype=np.float32))

        self.assertEqual(normalized.shape, (256,))
        self.assertAlmostEqual(float(np.linalg.norm(normalized)), 1.0, places=6)

    def test_nonfinite_zero_or_wrong_dimension_embedding_is_rejected(self):
        for values in (
            np.zeros(256, dtype=np.float32),
            np.full(256, np.nan, dtype=np.float32),
            np.ones(255, dtype=np.float32),
        ):
            with self.assertRaises(ValueError):
                compare_0095.normalize(values)

    def test_equivalent_embeddings_pass_cosine_and_absolute_error(self):
        reference = np.arange(1, 257, dtype=np.float32)
        candidate = reference + np.float32(1e-5)

        result = compare_0095.compare_embeddings(reference, candidate, 0.9999, 1e-3)

        self.assertTrue(result["passed"])


if __name__ == "__main__":
    unittest.main()
