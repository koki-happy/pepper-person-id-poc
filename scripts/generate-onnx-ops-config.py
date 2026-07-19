#!/usr/bin/env python3
"""Generate an ONNX Runtime reduced-kernel config from one or more ONNX models."""

from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path

import onnx


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("models", nargs="+", type=Path)
    args = parser.parse_args()

    operators: dict[tuple[str, int], set[str]] = defaultdict(set)
    for model_path in args.models:
        model = onnx.load(str(model_path), load_external_data=False)
        versions = {(item.domain or "ai.onnx"): item.version for item in model.opset_import}
        for node in model.graph.node:
            domain = node.domain or "ai.onnx"
            operators[(domain, versions[domain])].add(node.op_type)

    lines = [
        f"{domain};{version};{','.join(sorted(names))}"
        for (domain, version), names in sorted(operators.items())
    ]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(args.output)
    print("\n".join(lines))


if __name__ == "__main__":
    main()
