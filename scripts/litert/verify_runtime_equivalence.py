from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

import numpy as np
import onnxruntime as ort
from ai_edge_litert.interpreter import Interpreter


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run_onnx(model: Path, input_name: str, values: np.ndarray) -> list[np.ndarray]:
    session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
    return [
        np.asarray(value, dtype=np.float32)
        for value in session.run(None, {input_name: values})
    ]


def run_litert(model: Path, values: np.ndarray) -> list[np.ndarray]:
    interpreter = Interpreter(model_path=str(model))
    interpreter.allocate_tensors()
    inputs = interpreter.get_input_details()
    if len(inputs) != 1:
        raise ValueError(f"expected one LiteRT input, found {len(inputs)}")
    input_detail = inputs[0]
    if tuple(input_detail["shape"]) != values.shape:
        raise ValueError(
            f"LiteRT input shape mismatch: expected {values.shape}, "
            f"actual {tuple(input_detail['shape'])}"
        )
    interpreter.set_tensor(input_detail["index"], values)
    interpreter.invoke()
    return [
        np.asarray(interpreter.get_tensor(detail["index"]), dtype=np.float32)
        for detail in interpreter.get_output_details()
    ]


def tensor_error(reference: np.ndarray, candidate: np.ndarray) -> dict[str, Any]:
    if reference.shape != candidate.shape:
        return {
            "passed": False,
            "reason": "SHAPE_MISMATCH",
            "referenceShape": list(reference.shape),
            "candidateShape": list(candidate.shape),
        }
    difference = np.abs(reference - candidate)
    return {
        "passed": bool(np.isfinite(candidate).all()),
        "shape": list(reference.shape),
        "maximumAbsoluteError": float(difference.max(initial=0.0)),
        "meanAbsoluteError": float(difference.mean()),
    }


def normalized_cosine(left: np.ndarray, right: np.ndarray) -> float:
    left_vector = left.reshape(-1).astype(np.float64)
    right_vector = right.reshape(-1).astype(np.float64)
    denominator = np.linalg.norm(left_vector) * np.linalg.norm(right_vector)
    if denominator == 0:
        raise ValueError("cannot compare a zero-norm embedding")
    return float(np.dot(left_vector, right_vector) / denominator)


def correct_yunet_kps32_layout(outputs: list[np.ndarray]) -> list[np.ndarray]:
    corrected = list(outputs)
    kps32 = corrected[11]
    if kps32.shape != (1, 100, 10):
        raise ValueError(f"unexpected YuNet kps_32 shape: {kps32.shape}")
    corrected[11] = (
        kps32.reshape(1, 10, 10, 10)
        .transpose(0, 2, 3, 1)
        .reshape(1, 100, 10)
    )
    return corrected


def verify(
    sface_onnx: Path,
    sface_tflite: Path,
    yunet_onnx: Path,
    yunet_tflite: Path,
    minimum_sface_cosine: float,
    maximum_yunet_absolute_error: float,
) -> dict[str, Any]:
    sface_input = np.linspace(
        -1.0,
        1.0,
        num=1 * 3 * 112 * 112,
        dtype=np.float32,
    ).reshape(1, 3, 112, 112)
    yunet_input = np.random.default_rng(20260726).uniform(
        0.0,
        255.0,
        size=(1, 3, 320, 320),
    ).astype(np.float32)

    sface_reference = run_onnx(sface_onnx, "data", sface_input)
    sface_candidate = run_litert(sface_tflite, sface_input)
    if len(sface_reference) != 1 or len(sface_candidate) != 1:
        raise ValueError("SFace must expose exactly one output")
    sface_error = tensor_error(sface_reference[0], sface_candidate[0])
    sface_cosine = normalized_cosine(sface_reference[0], sface_candidate[0])
    sface_error.update(
        {
            "cosine": sface_cosine,
            "minimumCosine": minimum_sface_cosine,
            "passed": bool(sface_error["passed"] and sface_cosine >= minimum_sface_cosine),
        }
    )

    yunet_reference = run_onnx(yunet_onnx, "input", yunet_input)
    yunet_candidate = correct_yunet_kps32_layout(
        run_litert(yunet_tflite, yunet_input)
    )
    if len(yunet_reference) != len(yunet_candidate):
        raise ValueError(
            f"YuNet output count mismatch: ONNX={len(yunet_reference)}, "
            f"LiteRT={len(yunet_candidate)}"
        )
    yunet_errors = [
        {"index": index, **tensor_error(reference, candidate)}
        for index, (reference, candidate) in enumerate(
            zip(yunet_reference, yunet_candidate, strict=True)
        )
    ]
    yunet_passed = all(
        item["passed"]
        and item["maximumAbsoluteError"] <= maximum_yunet_absolute_error
        for item in yunet_errors
    )

    return {
        "schemaVersion": 1,
        "fixedInput": {
            "sface": "linspace(-1,1,[1,3,112,112])",
            "yunet": "PCG64(seed=20260726), uniform(0,255,[1,3,320,320])",
        },
        "artifacts": {
            "sfaceOnnxSha256": sha256(sface_onnx),
            "sfaceLiteRtSha256": sha256(sface_tflite),
            "yunetOnnxSha256": sha256(yunet_onnx),
            "yunetLiteRtSha256": sha256(yunet_tflite),
        },
        "sface": sface_error,
        "yunet": {
            "maximumAllowedAbsoluteError": maximum_yunet_absolute_error,
            "adapterCorrection": (
                "Identity_11/kps_32 [1,100,10] is restored from the converter's "
                "[kps,y,x] flattening to ONNX [y,x,kps] before decoding."
            ),
            "outputs": yunet_errors,
            "passed": yunet_passed,
        },
        "passed": bool(sface_error["passed"] and yunet_passed),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--sface-onnx", type=Path, required=True)
    parser.add_argument("--sface-tflite", type=Path, required=True)
    parser.add_argument("--yunet-onnx", type=Path, required=True)
    parser.add_argument("--yunet-tflite", type=Path, required=True)
    parser.add_argument("--minimum-sface-cosine", type=float, default=0.999)
    parser.add_argument("--maximum-yunet-absolute-error", type=float, default=1e-3)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = verify(
        args.sface_onnx,
        args.sface_tflite,
        args.yunet_onnx,
        args.yunet_tflite,
        args.minimum_sface_cosine,
        args.maximum_yunet_absolute_error,
    )
    encoded = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
