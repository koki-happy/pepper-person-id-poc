"""Generate reproducible CSV, SVG, and Markdown reports from benchmark JSONL."""

from __future__ import annotations

import argparse
import csv
import html
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence


@dataclass(frozen=True)
class ParsedJsonLines:
    events: list[dict[str, Any]]
    warnings: list[str]


@dataclass(frozen=True)
class NumericStatistics:
    measured_count: int
    missing_count: int
    p50: float
    p95: float
    maximum: float


@dataclass(frozen=True)
class ReportPaths:
    csv_path: Path
    svg_path: Path
    markdown_path: Path
    warnings: tuple[str, ...]


def parse_jsonl(lines: Iterable[str]) -> ParsedJsonLines:
    events: list[dict[str, Any]] = []
    warnings: list[str] = []
    for line_number, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line:
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            warnings.append(f"invalid JSON at line {line_number}: {error.msg}")
            continue
        if not isinstance(value, dict):
            warnings.append(f"non-object JSON value at line {line_number}")
            continue
        events.append(value)
    return ParsedJsonLines(events=events, warnings=warnings)


def load_jsonl(path: Path) -> ParsedJsonLines:
    with path.open("r", encoding="utf-8") as source:
        return parse_jsonl(source)


def flatten_event(
    value: Mapping[str, Any],
    prefix: str = "",
) -> dict[str, Any]:
    flattened: dict[str, Any] = {}
    for key in sorted(value):
        child = value[key]
        path = f"{prefix}.{key}" if prefix else key
        if isinstance(child, Mapping):
            flattened.update(flatten_event(child, path))
        elif isinstance(child, list):
            flattened[path] = json.dumps(
                child,
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        else:
            flattened[path] = child
    return flattened


def numeric_statistics(
    events: Sequence[Mapping[str, Any]],
) -> dict[str, NumericStatistics]:
    rows = [flatten_event(event) for event in events]
    keys = sorted(
        key
        for key in {key for row in rows for key in row}
        if _is_metric_field(key)
    )
    result: dict[str, NumericStatistics] = {}
    for key in keys:
        values = [
            float(row[key])
            for row in rows
            if _is_finite_number(row.get(key))
        ]
        if not values:
            continue
        values.sort()
        result[key] = NumericStatistics(
            measured_count=len(values),
            missing_count=len(rows) - len(values),
            p50=_nearest_rank(values, 0.50),
            p95=_nearest_rank(values, 0.95),
            maximum=values[-1],
        )
    return result


def detect_warnings(events: Sequence[Mapping[str, Any]]) -> list[str]:
    if not events:
        return ["no benchmark events were parsed"]
    rows = [flatten_event(event) for event in events]
    warnings: list[str] = []
    required_fields = (
        "eventType",
        "run.runId",
        "run.scenarioId",
        "run.device.model",
        "run.device.apiLevel",
        "run.device.abi",
        "run.buildVariant",
        "run.input.descriptor",
        "run.input.sha256",
        "run.input.preprocessingId",
        "run.input.datasetId",
        "run.input.repetition",
        "run.models",
        "run.thresholds.identification",
        "run.thresholds.minimumLead",
        "status",
    )
    for field in required_fields:
        missing = sum(row.get(field) is None for row in rows)
        if missing:
            warnings.append(f"missing field {field} in {missing}/{len(rows)} events")

    condition_fields = (
        "run.scenarioId",
        "run.device.manufacturer",
        "run.device.model",
        "run.device.apiLevel",
        "run.device.abi",
        "run.buildVariant",
        "run.input.descriptor",
        "run.input.sha256",
        "run.input.preprocessingId",
        "run.input.datasetId",
        "run.thresholds.identification",
        "run.thresholds.minimumLead",
    )
    for field in condition_fields:
        measured = {_stable_value(row.get(field)) for row in rows if row.get(field) is not None}
        if len(measured) > 1:
            warnings.append(f"mixed condition for {field}: {len(measured)} values")

    model_spaces_by_role: dict[str, set[str]] = {}
    for event in events:
        run = event.get("run")
        models = run.get("models") if isinstance(run, Mapping) else None
        if not isinstance(models, list):
            continue
        for model in models:
            if not isinstance(model, Mapping):
                continue
            role = model.get("role")
            model_space = model.get("modelSpaceId")
            if isinstance(role, str) and isinstance(model_space, str):
                model_spaces_by_role.setdefault(role, set()).add(model_space)
    for role, model_spaces in sorted(model_spaces_by_role.items()):
        if len(model_spaces) > 1:
            warnings.append(
                f"incompatible comparison for {role}: mixed model spaces "
                f"{', '.join(sorted(model_spaces))}"
            )

    dropped = sum(_nonnegative_integer(row.get("dropCount")) for row in rows)
    stalled = sum(_nonnegative_integer(row.get("stallCount")) for row in rows)
    if dropped:
        warnings.append(f"dropped events reported: {dropped}")
    if stalled:
        warnings.append(f"stalls reported: {stalled}")
    if any(row.get("status") == "FAILURE" for row in rows):
        warnings.append("one or more benchmark events failed")
    return warnings


def generate_reports(
    events: Sequence[Mapping[str, Any]],
    output_directory: Path,
    parser_warnings: Sequence[str] = (),
) -> ReportPaths:
    output_directory.mkdir(parents=True, exist_ok=True)
    csv_path = output_directory / "metrics.csv"
    svg_path = output_directory / "metrics.svg"
    markdown_path = output_directory / "summary.md"
    warnings = tuple(parser_warnings) + tuple(detect_warnings(events))
    _write_csv(events, csv_path)
    _write_svg(events, svg_path)
    _write_markdown(events, numeric_statistics(events), warnings, markdown_path)
    return ReportPaths(
        csv_path=csv_path,
        svg_path=svg_path,
        markdown_path=markdown_path,
        warnings=warnings,
    )


def _write_csv(events: Sequence[Mapping[str, Any]], path: Path) -> None:
    rows = [flatten_event(event) for event in events]
    fieldnames = sorted({key for row in rows for key in row})
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fieldnames, extrasaction="ignore")
        if fieldnames:
            writer.writeheader()
            writer.writerows(rows)


def _write_svg(events: Sequence[Mapping[str, Any]], path: Path) -> None:
    rows = [flatten_event(event) for event in events]
    stats = numeric_statistics(events)
    metric_keys = [
        key
        for key in stats
        if _is_chart_metric(key)
    ][:8]
    width = 960
    row_height = 120
    height = max(100, 40 + row_height * max(1, len(metric_keys)))
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="white"/>',
        '<style>text{font:12px sans-serif}.axis{stroke:#bbb}.series{fill:none;'
        'stroke:#1565c0;stroke-width:2}</style>',
    ]
    if not metric_keys:
        parts.append('<text x="20" y="50">No measured numeric metrics</text>')
    for metric_index, key in enumerate(metric_keys):
        top = 30 + metric_index * row_height
        left = 210
        chart_width = width - left - 20
        chart_height = 80
        values = [
            float(row[key]) if _is_finite_number(row.get(key)) else None
            for row in rows
        ]
        measured = [value for value in values if value is not None]
        low = min(measured)
        high = max(measured)
        parts.append(
            f'<text x="10" y="{top + 15}">{html.escape(key)}</text>'
        )
        parts.append(
            f'<line class="axis" x1="{left}" y1="{top + chart_height}" '
            f'x2="{left + chart_width}" y2="{top + chart_height}"/>'
        )
        for segment in _contiguous_segments(values):
            points: list[str] = []
            for index, value in segment:
                x = left if len(values) <= 1 else left + chart_width * index / (len(values) - 1)
                ratio = 0.5 if high == low else (value - low) / (high - low)
                y = top + chart_height - ratio * chart_height
                points.append(f"{x:.2f},{y:.2f}")
            parts.append(f'<polyline class="series" points="{" ".join(points)}"/>')
    parts.append("</svg>")
    path.write_text("\n".join(parts) + "\n", encoding="utf-8")


def _write_markdown(
    events: Sequence[Mapping[str, Any]],
    statistics: Mapping[str, NumericStatistics],
    warnings: Sequence[str],
    path: Path,
) -> None:
    lines = [
        "# Benchmark report",
        "",
        f"- Events: {len(events)}",
        f"- Numeric metrics: {len(statistics)}",
        "",
        "## Warnings",
        "",
    ]
    if warnings:
        lines.extend(f"- {warning}" for warning in warnings)
    else:
        lines.append("- None")
    lines.extend(
        [
            "",
            "## Statistics",
            "",
            "| Metric | Measured | Missing | p50 | p95 | Max |",
            "|---|---:|---:|---:|---:|---:|",
        ]
    )
    for key, value in sorted(statistics.items()):
        lines.append(
            f"| {key} | {value.measured_count} | {value.missing_count} | "
            f"{_format_number(value.p50)} | {_format_number(value.p95)} | "
            f"{_format_number(value.maximum)} |"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _nearest_rank(values: Sequence[float], percentile: float) -> float:
    rank = min(len(values), max(1, math.ceil(percentile * len(values))))
    return values[rank - 1]


def _is_finite_number(value: Any) -> bool:
    return (
        isinstance(value, (int, float))
        and not isinstance(value, bool)
        and math.isfinite(float(value))
    )


def _stable_value(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _nonnegative_integer(value: Any) -> int:
    if isinstance(value, int) and not isinstance(value, bool) and value > 0:
        return value
    return 0


def _is_chart_metric(key: str) -> bool:
    return (
        key.endswith("Millis")
        or key.endswith("Percent")
        or key.endswith("Bytes")
        or key.endswith("realTimeFactor")
    ) and "timestamp" not in key.lower() and "collectedAt" not in key


def _is_metric_field(key: str) -> bool:
    return (
        _is_chart_metric(key)
        or key.endswith("Count")
        or key in {"dropCount", "stallCount", "candidateCount"}
    )


def _contiguous_segments(
    values: Sequence[float | None],
) -> list[list[tuple[int, float]]]:
    segments: list[list[tuple[int, float]]] = []
    current: list[tuple[int, float]] = []
    for index, value in enumerate(values):
        if value is None:
            if current:
                segments.append(current)
                current = []
        else:
            current.append((index, value))
    if current:
        segments.append(current)
    return segments


def _format_number(value: float) -> str:
    return f"{value:.6g}"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="benchmark JSONL source")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("results/report"),
        help="directory for metrics.csv, metrics.svg, and summary.md",
    )
    arguments = parser.parse_args(argv)
    parsed = load_jsonl(arguments.input)
    result = generate_reports(
        parsed.events,
        arguments.output_dir,
        parser_warnings=parsed.warnings,
    )
    for warning in result.warnings:
        print(f"WARNING: {warning}")
    print(result.csv_path)
    print(result.svg_path)
    print(result.markdown_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
