import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

MODULE_PATH = Path(__file__).parents[1] / "datasets.py"
SPEC = importlib.util.spec_from_file_location("face_datasets", MODULE_PATH)
datasets = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = datasets
SPEC.loader.exec_module(datasets)


class DatasetManifestTest(unittest.TestCase):
    def sample(self, root, sample_id, subject, role):
        path = Path(root) / f"{sample_id}.jpg"
        path.write_bytes(b"fixture")
        return {
            "dataset": "fixture",
            "sample_id": sample_id,
            "subject_id": subject,
            "image_path": str(path),
            "role": role,
        }

    def test_valid_subject_disjoint_manifest(self):
        with tempfile.TemporaryDirectory() as root:
            manifest = {
                "enrolled_subjects": ["A"],
                "development_unknown_subjects": ["B"],
                "final_unknown_subjects": ["C"],
                "samples": [
                    self.sample(root, "a-enroll", "A", "enrollment"),
                    self.sample(root, "a-final", "A", "final_registered"),
                    self.sample(root, "b-dev", "B", "development_unknown"),
                    self.sample(root, "c-final", "C", "final_unknown"),
                ],
            }
            datasets.validate_manifest(manifest)

    def test_subject_leakage_fails(self):
        with tempfile.TemporaryDirectory() as root:
            manifest = {
                "enrolled_subjects": ["A"],
                "development_unknown_subjects": ["A"],
                "final_unknown_subjects": ["C"],
                "samples": [self.sample(root, "a", "A", "enrollment")],
            }
            with self.assertRaisesRegex(ValueError, "Subject leakage"):
                datasets.validate_manifest(manifest)

    def test_pointing_filename_grammar(self):
        match = datasets.POINTING_RE.fullmatch("personne15192+60+90.jpg")
        self.assertIsNotNone(match)
        self.assertEqual("15", match.group("subject"))
        self.assertEqual("1", match.group("series"))
        self.assertEqual("+60", match.group("pitch"))
        self.assertEqual("+90", match.group("yaw"))


if __name__ == "__main__":
    unittest.main()
