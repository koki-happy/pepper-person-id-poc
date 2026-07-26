from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from inspect_model import inspect_onnx, sha256


def environment_digest(lock_file: Path, dockerfile: Path) -> str:
    digest = hashlib.sha256()
    for path in (lock_file, dockerfile):
        digest.update(path.name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def manifest(
    *,
    source: Path,
    output: Path,
    source_hash: str,
    environment_hash: str,
    command: list[str],
    inspection: dict[str, Any],
) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "createdAtUtc": datetime.now(timezone.utc).isoformat(),
        "source": {
            "filename": source.name,
            "sha256": source_hash,
            "inspection": inspection,
        },
        "output": {
            "filename": output.name,
            "sha256": sha256(output),
            "fileSizeBytes": output.stat().st_size,
        },
        "conversion": {
            "tool": "onnx2tf",
            "toolVersion": importlib.metadata.version("onnx2tf"),
            "environmentDigest": environment_hash,
            "command": command,
            "warnings": [
                "onnx2tf strict accuracy correction is disabled; numeric equivalence is verified separately on fixed inputs."
            ],
        },
        "verification": {
            "conversionCompleted": True,
            "numericEquivalence": "UNMEASURED",
            "arm64Runtime": "UNMEASURED",
            "pepperArmv7Runtime": "UNMEASURED",
        },
    }


def convert(
    source: Path,
    output: Path,
    expected_source_sha256: str,
    lock_file: Path,
    dockerfile: Path,
    input_shapes: list[str] | None = None,
) -> dict[str, Any]:
    source = source.resolve()
    output = output.resolve()
    if sha256(source) != expected_source_sha256:
        raise ValueError("source SHA-256 does not match the pinned input")
    source_hash_before = sha256(source)
    inspection = inspect_onnx(source)
    with tempfile.TemporaryDirectory(prefix="litert-convert-") as directory:
        temporary = Path(directory)
        immutable_copy = temporary / source.name
        shutil.copy2(source, immutable_copy)
        conversion_directory = temporary / "converted"
        command = [
            "onnx2tf",
            "-i",
            str(immutable_copy),
            "-o",
            str(conversion_directory),
            "--output_signaturedefs",
            "--disable_strict_mode",
        ]
        nchw_inputs = [
            tensor["name"]
            for tensor in inspection["inputs"]
            if len(tensor["shape"]) == 4 and tensor["shape"][1] in (1, 3)
        ]
        if nchw_inputs:
            command.extend(["--keep_ncw_or_nchw_or_ncdhw_input_names", *nchw_inputs])
        if input_shapes:
            command.extend(["--overwrite_input_shape", *input_shapes])
        subprocess.run(command, check=True)
        candidates = sorted(conversion_directory.rglob("*_float32.tflite"))
        if len(candidates) != 1:
            raise RuntimeError(f"expected one FP32 .tflite output, found {len(candidates)}")
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(candidates[0], output)
    if sha256(source) != source_hash_before:
        raise RuntimeError("source model changed during conversion")
    return manifest(
        source=source,
        output=output,
        source_hash=source_hash_before,
        environment_hash=environment_digest(lock_file, dockerfile),
        command=command,
        inspection=inspection,
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument(
        "--input-shape",
        action="append",
        default=[],
        help='Fix a dynamic input using onnx2tf syntax, for example "input:1,3,320,320".',
    )
    script_directory = Path(__file__).resolve().parent
    parser.add_argument("--requirements-lock", type=Path, default=script_directory / "requirements.lock")
    parser.add_argument("--dockerfile", type=Path, default=script_directory / "Dockerfile")
    args = parser.parse_args()
    result = convert(
        args.source,
        args.output,
        args.source_sha256,
        args.requirements_lock,
        args.dockerfile,
        args.input_shape,
    )
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(
        json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
