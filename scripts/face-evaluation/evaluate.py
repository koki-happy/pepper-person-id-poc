from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path
import statistics
import sys

import numpy as np

from datasets import generate_pointing04, validate_manifest, write_manifest
from inference import EmbeddingCache, FaceInference, centroid, cosine
from metrics import UNKNOWN, decide, grouped_summary, select_eer, summarize, sweep


def main() -> None:
    parser = argparse.ArgumentParser(description="Reproducible face dataset evaluation")
    subparsers = parser.add_subparsers(dest="command", required=True)
    validate = subparsers.add_parser("validate")
    validate.add_argument("--dataset", choices=["pointing04", "biwi"], required=True)
    validate.add_argument("--root", type=Path, required=True)
    validate.add_argument("--protocol", choices=["front", "multi"], required=True)
    validate.add_argument("--output", type=Path, required=True)
    run = subparsers.add_parser("run")
    run.add_argument("--manifest", type=Path, required=True)
    run.add_argument("--model", choices=["sface", "0095"], required=True)
    run.add_argument("--model-path", type=Path, required=True)
    run.add_argument("--detector", type=Path, required=True)
    run.add_argument("--cache", type=Path, required=True)
    run.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "validate":
        if args.dataset == "pointing04":
            manifest = generate_pointing04(args.root, args.protocol)
        else:
            from datasets import generate_biwi
            manifest = generate_biwi(args.root, args.protocol)
        write_manifest(manifest, args.output)
        print(json.dumps({"samples": len(manifest["samples"]), "output": str(args.output)}))
        return
    evaluate(args)


def evaluate(args) -> None:
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    validate_manifest(manifest)
    engine = FaceInference(args.detector, args.model_path, args.model)
    cache = EmbeddingCache(args.cache, args.model, args.model_path)
    extracted = {}
    status_counts = {}
    detection_times, embedding_times = [], []
    for index, sample in enumerate(manifest["samples"], start=1):
        result = cache.load(sample)
        if result is None:
            result = engine.extract(Path(sample["image_path"]))
            cache.save(sample, result)
        extracted[sample["sample_id"]] = result
        status_counts[result["status"]] = status_counts.get(result["status"], 0) + 1
        if "detection_ms" in result:
            detection_times.append(result["detection_ms"])
        if "embedding_ms" in result:
            embedding_times.append(result["embedding_ms"])
        if index % 100 == 0:
            print(f"processed {index}/{len(manifest['samples'])}", file=sys.stderr)

    templates = {}
    for subject in manifest["enrolled_subjects"]:
        embeddings = [
            extracted[row["sample_id"]]["embedding"]
            for row in manifest["samples"]
            if row["subject_id"] == subject and row["role"] == "enrollment"
            and extracted[row["sample_id"]].get("embedding") is not None
        ]
        if embeddings:
            templates[subject] = centroid(embeddings)

    raw_trials = []
    for sample in manifest["samples"]:
        if sample["role"] == "enrollment":
            continue
        result = extracted[sample["sample_id"]]
        embedding = result.get("embedding")
        scores = {} if embedding is None else {
            person_id: cosine(template, embedding) for person_id, template in templates.items()
        }
        raw_trials.append(
            {
                **{key: value for key, value in sample.items() if key not in {"image_path", "image_sha256"}},
                "expected_id": sample["subject_id"] if "registered" in sample["role"] else UNKNOWN,
                "split": "development" if sample["role"].startswith("development") else "final",
                "inference_status": result["status"],
                "scores": scores,
            }
        )
    development = [row for row in raw_trials if row["split"] == "development"]
    threshold_values = [round(0.20 + index * 0.02, 2) for index in range(36)]
    margin_values = [round(index * 0.02, 2) for index in range(16)]
    sweep_rows = sweep(development, threshold_values, margin_values)
    selected = select_eer(sweep_rows)
    decided = []
    for row in raw_trials:
        choice = decide(row["scores"], selected["threshold"], selected["minimum_margin"])
        decided.append(
            {
                **row,
                "predicted_id": choice.predicted_id,
                "best_id": choice.best_id,
                "best_score": choice.best_score,
                "second_score": choice.second_score,
                "margin": choice.margin,
            }
        )
    final_rows = [row for row in decided if row["split"] == "final"]
    summary = {
        "schema_version": 1,
        "dataset": manifest["dataset"],
        "protocol": manifest["protocol"],
        "model": args.model,
        "manifest": str(args.manifest),
        "sample_count": len(manifest["samples"]),
        "template_count": len(templates),
        "status_counts": status_counts,
        "selected_on_development": selected,
        "development": summarize([row for row in decided if row["split"] == "development"]),
        "final": summarize(final_rows),
        "final_by_yaw": grouped_summary(final_rows, ("yaw_band",)),
        "final_by_pitch": grouped_summary(final_rows, ("pitch_band",)),
        "final_by_role": grouped_summary(final_rows, ("role",)),
        "timing_ms": {
            "detection": _timing(detection_times),
            "embedding": _timing(embedding_times),
        },
    }
    args.output.mkdir(parents=True, exist_ok=True)
    _write_trials(args.output / "trials.csv", decided)
    _write_sweep(args.output / "threshold-sweep.csv", sweep_rows)
    (args.output / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (args.output / "report.md").write_text(_render_report(summary), encoding="utf-8")
    print(json.dumps(summary["final"], sort_keys=True))


def _timing(values: list[float]) -> dict:
    if not values:
        return {"count": 0, "average": None, "maximum": None, "p95": None}
    ordered = sorted(values)
    p95 = ordered[min(len(ordered) - 1, int(np.ceil(len(ordered) * 0.95)) - 1)]
    return {"count": len(values), "average": statistics.fmean(values), "maximum": max(values), "p95": p95}


def _write_trials(path: Path, rows: list[dict]) -> None:
    keys = [
        "dataset", "sample_id", "subject_id", "series_id", "yaw_degrees", "pitch_degrees",
        "yaw_band", "pitch_band", "role", "split", "expected_id", "inference_status",
        "predicted_id", "best_id", "best_score", "second_score", "margin",
    ]
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=keys, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def _write_sweep(path: Path, rows: list[dict]) -> None:
    keys = [
        "threshold", "minimum_margin", "far", "misidentification_rate",
        "identification_accuracy", "frr", "false_accept_count", "misidentification_count",
        "correct_count", "false_reject_count", "registered_count", "unknown_count",
    ]
    with path.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=keys, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def _render_report(summary: dict) -> str:
    final = summary["final"]
    return (
        f"# {summary['dataset']} {summary['model']} {summary['protocol']}\n\n"
        f"- Samples: {summary['sample_count']}\n- Templates: {summary['template_count']}\n"
        f"- Development-selected threshold: {summary['selected_on_development']['threshold']}\n"
        f"- Development-selected margin: {summary['selected_on_development']['minimum_margin']}\n"
        f"- FAR: {final['far']}\n- Misidentification rate: {final['misidentification_rate']}\n"
        f"- Identification accuracy: {final['identification_accuracy']}\n- FRR: {final['frr']}\n"
        f"- Status counts: `{json.dumps(summary['status_counts'], sort_keys=True)}`\n"
    )


if __name__ == "__main__":
    main()
