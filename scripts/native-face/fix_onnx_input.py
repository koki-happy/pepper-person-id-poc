import argparse
from pathlib import Path

import onnx


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--size", required=True, type=int)
    args = parser.parse_args()

    model = onnx.load(args.input)
    if len(model.graph.input) != 1:
        raise ValueError(f"YuNet must have exactly one input, got {len(model.graph.input)}")
    tensor = model.graph.input[0]
    if tensor.name != "input":
        raise ValueError(f"Unexpected YuNet input name={tensor.name!r}")
    dimensions = tensor.type.tensor_type.shape.dim
    if len(dimensions) != 4:
        raise ValueError(f"YuNet input must be rank four, got {len(dimensions)}")
    for dimension, value in zip(dimensions, (1, 3, args.size, args.size)):
        dimension.ClearField("dim_param")
        dimension.dim_value = value
    onnx.checker.check_model(model)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    onnx.save_model(model, args.output)


if __name__ == "__main__":
    main()
