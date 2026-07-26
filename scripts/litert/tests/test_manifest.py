import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
SPEC = importlib.util.spec_from_file_location("convert_models", ROOT / "convert_models.py")
convert_models = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(convert_models)


class ManifestTest(unittest.TestCase):
    def test_environment_digest_changes_with_locked_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            lock = root / "requirements.lock"
            dockerfile = root / "Dockerfile"
            lock.write_text("package==1\n", encoding="utf-8")
            dockerfile.write_text("FROM python:3.11\n", encoding="utf-8")
            first = convert_models.environment_digest(lock, dockerfile)
            lock.write_text("package==2\n", encoding="utf-8")
            second = convert_models.environment_digest(lock, dockerfile)
            self.assertNotEqual(first, second)
            self.assertEqual(len(first), 64)

    def test_manifest_contains_pinned_source_and_output_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.onnx"
            output = root / "model.tflite"
            source.write_bytes(b"source")
            output.write_bytes(b"output")
            with mock.patch.object(convert_models.importlib.metadata, "version", return_value="1.2.3"):
                value = convert_models.manifest(
                    source=source,
                    output=output,
                    source_hash=hashlib.sha256(b"source").hexdigest(),
                    environment_hash="a" * 64,
                    command=["onnx2tf", "-i", "source.onnx"],
                    inspection={"inputs": [], "outputs": []},
                )
            encoded = json.loads(json.dumps(value))
            self.assertEqual(encoded["schemaVersion"], 1)
            self.assertEqual(encoded["source"]["sha256"], hashlib.sha256(b"source").hexdigest())
            self.assertEqual(encoded["output"]["sha256"], hashlib.sha256(b"output").hexdigest())
            self.assertEqual(encoded["conversion"]["environmentDigest"], "a" * 64)
            self.assertEqual(encoded["verification"]["numericEquivalence"], "UNMEASURED")


if __name__ == "__main__":
    unittest.main()
