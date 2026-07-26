from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
from ai_edge_litert.interpreter import Interpreter

from compare_0095 import compare_embeddings
from inspect_model import sha256


def verify(
    onnx_model: Path,
    litert_model: Path,
    minimum_cosine: float,
    maximum_absolute_error: float,
) -> dict[str, object]:
    fixed_input = np.random.default_rng(95).uniform(
        0.0,
        255.0,
        size=(1, 3, 128, 128),
    ).astype(np.float32)

    onnx_session = ort.InferenceSession(
        str(onnx_model),
        providers=["CPUExecutionProvider"],
    )
    onnx_input = onnx_session.get_inputs()
    if len(onnx_input) != 1:
        raise ValueError(f"expected one ONNX input, found {len(onnx_input)}")
    onnx_outputs = onnx_session.run(None, {onnx_input[0].name: fixed_input})
    if len(onnx_outputs) != 1:
        raise ValueError(f"expected one ONNX output, found {len(onnx_outputs)}")

    interpreter = Interpreter(model_path=str(litert_model))
    interpreter.allocate_tensors()
    litert_inputs = interpreter.get_input_details()
    litert_outputs = interpreter.get_output_details()
    if len(litert_inputs) != 1 or len(litert_outputs) != 1:
        raise ValueError("0095 LiteRT must expose exactly one input and one output")
    interpreter.set_tensor(litert_inputs[0]["index"], fixed_input)
    interpreter.invoke()
    litert_output = interpreter.get_tensor(litert_outputs[0]["index"])

    comparison = compare_embeddings(
        onnx_outputs[0],
        litert_output,
        minimum_cosine,
        maximum_absolute_error,
    )
    return {
        "schemaVersion": 1,
        "fixedInput": "PCG64(seed=95), uniform(0,255,[1,3,128,128])",
        "artifacts": {
            "onnxSha256": sha256(onnx_model),
            "liteRtSha256": sha256(litert_model),
        },
        "tensors": {
            "onnxInput": {
                "name": onnx_input[0].name,
                "shape": list(onnx_input[0].shape),
            },
            "onnxOutput": {
                "name": onnx_session.get_outputs()[0].name,
                "shape": list(onnx_outputs[0].shape),
            },
            "liteRtInput": {
                "name": litert_inputs[0]["name"],
                "shape": litert_inputs[0]["shape"].tolist(),
            },
            "liteRtOutput": {
                "name": litert_outputs[0]["name"],
                "shape": litert_outputs[0]["shape"].tolist(),
            },
        },
        "comparison": comparison,
        "passed": comparison["passed"],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--onnx", required=True, type=Path)
    parser.add_argument("--litert", required=True, type=Path)
    parser.add_argument("--minimum-cosine", type=float, default=0.9999)
    parser.add_argument("--maximum-absolute-error", type=float, default=1e-3)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = verify(
        args.onnx.resolve(),
        args.litert.resolve(),
        args.minimum_cosine,
        args.maximum_absolute_error,
    )
    encoded = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, encoding="utf-8")
    print(encoded, end="")
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
