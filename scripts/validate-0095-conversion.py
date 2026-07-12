import argparse
import json
from pathlib import Path

import cv2
import numpy as np
import onnx
import onnxruntime as ort
import openvino as ov


def cosine(left: np.ndarray, right: np.ndarray) -> float:
    left = left.reshape(-1).astype(np.float64)
    right = right.reshape(-1).astype(np.float64)
    if not np.isfinite(left).all() or not np.isfinite(right).all():
        raise ValueError("Embedding contains non-finite values")
    denominator = np.linalg.norm(left) * np.linalg.norm(right)
    if denominator == 0.0:
        raise ValueError("Embedding has zero norm")
    return float(np.dot(left, right) / denominator)


def compare(reference: np.ndarray, candidate: np.ndarray) -> dict[str, float]:
    reference = reference.reshape(-1)
    candidate = candidate.reshape(-1)
    if reference.shape != (256,) or candidate.shape != (256,):
        raise ValueError(f"Unexpected output shapes: {reference.shape}, {candidate.shape}")
    if not np.isfinite(reference).all() or not np.isfinite(candidate).all():
        raise ValueError("Model output contains non-finite values")
    difference = np.abs(reference - candidate)
    return {
        "max_abs_error": float(difference.max()),
        "mean_abs_error": float(difference.mean()),
        "cosine": cosine(reference, candidate),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ir", type=Path, required=True)
    parser.add_argument("--onnx", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--face-assets", type=Path, required=True)
    parser.add_argument("--detector", type=Path, required=True)
    args = parser.parse_args()

    onnx_model = onnx.load(args.onnx)
    onnx.checker.check_model(onnx_model)

    core = ov.Core()
    ir_model = core.read_model(args.ir)
    ir_compiled = core.compile_model(ir_model, "CPU")
    ir_input = ir_compiled.input(0)
    ir_output = ir_compiled.output(0)

    ort_session = ort.InferenceSession(str(args.onnx), providers=["CPUExecutionProvider"])
    ort_input_name = ort_session.get_inputs()[0].name

    cv_net = cv2.dnn.readNetFromONNX(str(args.onnx))
    rng = np.random.default_rng(95)
    inputs = {
        "zeros": np.zeros((1, 3, 128, 128), dtype=np.float32),
        "full": np.full((1, 3, 128, 128), 255.0, dtype=np.float32),
        "random": rng.uniform(0.0, 255.0, (1, 3, 128, 128)).astype(np.float32),
    }

    target = np.array(
        [
            [0.31556875, 0.4615741071428571],
            [0.6826229166666667, 0.4615741071428571],
            [0.5002625, 0.6405053571428571],
            [0.349471875, 0.8246919642857142],
            [0.6534364583333333, 0.8246919642857142],
        ],
        dtype=np.float32,
    ) * 128.0
    face_files = {
        "enrollment": "lombard-s22-plain.png",
        "same": "lombard-s22-lombard.png",
        "different": "lombard-s16-plain.png",
    }
    for role, filename in face_files.items():
        image = cv2.imread(str(args.face_assets / filename), cv2.IMREAD_COLOR)
        if image is None:
            raise FileNotFoundError(args.face_assets / filename)
        detector = cv2.FaceDetectorYN.create(
            str(args.detector), "", (image.shape[1], image.shape[0]), 0.80, 0.30, 5000
        )
        _, faces = detector.detect(image)
        if faces is None or len(faces) != 1:
            raise RuntimeError(f"Expected one face in {filename}, got {0 if faces is None else len(faces)}")
        source = faces[0, 4:14].reshape(5, 2).astype(np.float64)
        source_mean = source.mean(axis=0)
        target_mean = target.mean(axis=0)
        source_centered = source - source_mean
        target_centered = target.astype(np.float64) - target_mean
        denominator = np.square(source_centered).sum()
        real = np.sum(source_centered[:, 0] * target_centered[:, 0] + source_centered[:, 1] * target_centered[:, 1])
        imaginary = np.sum(source_centered[:, 0] * target_centered[:, 1] - source_centered[:, 1] * target_centered[:, 0])
        a = real / denominator
        b = imaginary / denominator
        transform = np.array(
            [
                [a, -b, target_mean[0] - a * source_mean[0] + b * source_mean[1]],
                [b, a, target_mean[1] - b * source_mean[0] - a * source_mean[1]],
            ],
            dtype=np.float64,
        )
        aligned = cv2.warpAffine(image, transform, (128, 128))
        inputs[role] = aligned.transpose(2, 0, 1)[None].astype(np.float32)

    cases = []
    embeddings: dict[str, dict[str, np.ndarray]] = {}
    for name, input_tensor in inputs.items():
        ir_result = np.asarray(ir_compiled({ir_input: input_tensor})[ir_output])
        ort_result = np.asarray(ort_session.run(None, {ort_input_name: input_tensor})[0])
        cv_net.setInput(input_tensor)
        cv_result = np.asarray(cv_net.forward())
        embeddings[name] = {"ir": ir_result, "onnxruntime": ort_result, "opencv_5": cv_result}
        cases.append(
            {
                "case": name,
                "ir_vs_onnxruntime": compare(ir_result, ort_result),
                "ir_vs_opencv_5": compare(ir_result, cv_result),
            }
        )

    for case in cases:
        for comparison_name in ("ir_vs_onnxruntime", "ir_vs_opencv_5"):
            comparison = case[comparison_name]
            if comparison["max_abs_error"] > 1e-4 or comparison["cosine"] < 0.9999:
                raise RuntimeError(
                    f"Conversion mismatch in case {case['case']} {comparison_name}: {comparison}"
                )

    face_scores = {}
    for backend in ("ir", "onnxruntime", "opencv_5"):
        same_score = cosine(embeddings["enrollment"][backend], embeddings["same"][backend])
        different_score = cosine(embeddings["enrollment"][backend], embeddings["different"][backend])
        if same_score < 0.60 or different_score >= 0.60 or same_score <= different_score:
            raise RuntimeError(
                f"Face separation failed for {backend}: same={same_score}, different={different_score}"
            )
        face_scores[backend] = {"same": same_score, "different": different_score}
    for backend in ("onnxruntime", "opencv_5"):
        if abs(face_scores[backend]["same"] - face_scores["ir"]["same"]) > 1e-4:
            raise RuntimeError(f"Same-person score changed after conversion for {backend}")
        if abs(face_scores[backend]["different"] - face_scores["ir"]["different"]) > 1e-4:
            raise RuntimeError(f"Different-person score changed after conversion for {backend}")

    report = {
        "ir": str(args.ir),
        "onnx": str(args.onnx),
        "onnx_opset": onnx_model.opset_import[0].version,
        "openvino_version": ov.__version__,
        "opencv_version": cv2.__version__,
        "onnxruntime_version": ort.__version__,
        "cases": cases,
        "face_scores": face_scores,
    }
    rendered = json.dumps(report, indent=2)
    print(rendered)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
