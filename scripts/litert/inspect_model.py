from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect_onnx(path: Path) -> dict[str, Any]:
    import onnx

    model = onnx.load(str(path), load_external_data=False)
    initializer_names = {value.name for value in model.graph.initializer}

    def tensor(value: Any) -> dict[str, Any]:
        tensor_type = value.type.tensor_type
        shape = [
            dimension.dim_value if dimension.HasField("dim_value") else dimension.dim_param
            for dimension in tensor_type.shape.dim
        ]
        return {
            "name": value.name,
            "elementType": tensor_type.elem_type,
            "shape": shape,
        }

    return {
        "path": path.name,
        "sha256": sha256(path),
        "opsetImports": [
            {"domain": item.domain or "ai.onnx", "version": item.version}
            for item in model.opset_import
        ],
        "inputs": [
            tensor(value)
            for value in model.graph.input
            if value.name not in initializer_names
        ],
        "outputs": [tensor(value) for value in model.graph.output],
        "operators": sorted({node.op_type for node in model.graph.node}),
        "nodeCount": len(model.graph.node),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("model", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = inspect_onnx(args.model.resolve())
    text = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True)
    if args.output:
        args.output.write_text(text + "\n", encoding="utf-8")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
