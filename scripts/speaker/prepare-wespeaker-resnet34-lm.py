#!/usr/bin/env python3
"""Prepare the pinned WeSpeaker ResNet34-LM ONNX model for sherpa-onnx."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

import onnx


SOURCE_SHA256 = "7bb2f06e9df17cdf1ef14ee8a15ab08ed28e8d0ef5054ee135741560df2ec068"
SOURCE_SIZE = 26_530_309
OUTPUT_SHA256 = "df0cec64c3bba5dbc3637e50c4259de348a24124f4bb399f413fa1d4b44ba605"
OUTPUT_SIZE = 26_530_697
SOURCE_REVISION = "f0c48c298fd835726c27956a5d617bad7115627e"

SHERPA_METADATA = {
    "framework": "wespeaker",
    "language": "English",
    "url": (
        "https://huggingface.co/Wespeaker/wespeaker-voxceleb-resnet34-LM/"
        f"commit/{SOURCE_REVISION}"
    ),
    "comment": (
        "WeSpeaker VoxCeleb ResNet34 after large-margin fine-tuning; "
        "sherpa-onnx metadata added for Android runtime"
    ),
    "sample_rate": "16000",
    "output_dim": "256",
    "normalize_samples": "0",
    "feature_normalize_type": "global-mean",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def dimensions(value_info: onnx.ValueInfoProto) -> list[str | int]:
    return [
        dimension.dim_param if dimension.dim_param else dimension.dim_value
        for dimension in value_info.type.tensor_type.shape.dim
    ]


def main() -> None:
    args = parse_args()
    if args.source.stat().st_size != SOURCE_SIZE or sha256(args.source) != SOURCE_SHA256:
        raise ValueError("Pinned WeSpeaker source size or SHA-256 does not match")

    model = onnx.load(args.source)
    if [(value.name, dimensions(value)) for value in model.graph.input] != [
        ("feats", ["B", "T", 80])
    ]:
        raise ValueError("Unexpected WeSpeaker input contract")
    if [(value.name, dimensions(value)) for value in model.graph.output] != [
        ("embs", ["B", 256])
    ]:
        raise ValueError("Unexpected WeSpeaker output contract")

    del model.metadata_props[:]
    for key, value in SHERPA_METADATA.items():
        metadata = model.metadata_props.add()
        metadata.key = key
        metadata.value = value

    args.output.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, args.output)
    if args.output.stat().st_size != OUTPUT_SIZE or sha256(args.output) != OUTPUT_SHA256:
        raise ValueError("Prepared WeSpeaker artifact is not reproducible")


if __name__ == "__main__":
    main()
