from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from typing import Iterable

UNKNOWN = "Unknown"


@dataclass(frozen=True)
class Decision:
    best_id: str | None
    best_score: float | None
    second_score: float | None
    margin: float | None
    predicted_id: str


def decide(scores: dict[str, float], threshold: float, minimum_margin: float) -> Decision:
    ranked = sorted(scores.items(), key=lambda item: (-item[1], item[0]))
    if not ranked:
        return Decision(None, None, None, None, UNKNOWN)
    best_id, best_score = ranked[0]
    second_score = ranked[1][1] if len(ranked) > 1 else None
    margin = best_score - second_score if second_score is not None else None
    accepted = best_score >= threshold and (margin is None or margin >= minimum_margin)
    return Decision(best_id, best_score, second_score, margin, best_id if accepted else UNKNOWN)


def summarize(trials: Iterable[dict]) -> dict:
    rows = list(trials)
    registered = [row for row in rows if row["expected_id"] != UNKNOWN]
    unknown = [row for row in rows if row["expected_id"] == UNKNOWN]
    correct = sum(row["predicted_id"] == row["expected_id"] for row in registered)
    rejected = sum(row["predicted_id"] == UNKNOWN for row in registered)
    misidentified = sum(
        row["predicted_id"] not in (UNKNOWN, row["expected_id"]) for row in registered
    )
    false_accepts = sum(row["predicted_id"] != UNKNOWN for row in unknown)
    return {
        "trial_count": len(rows),
        "registered_count": len(registered),
        "unknown_count": len(unknown),
        "correct_count": correct,
        "misidentification_count": misidentified,
        "false_reject_count": rejected,
        "false_accept_count": false_accepts,
        "identification_accuracy": _rate(correct, len(registered)),
        "misidentification_rate": _rate(misidentified, len(registered)),
        "frr": _rate(rejected, len(registered)),
        "far": _rate(false_accepts, len(unknown)),
    }


def grouped_summary(trials: Iterable[dict], keys: tuple[str, ...]) -> list[dict]:
    groups: dict[tuple, list[dict]] = defaultdict(list)
    for row in trials:
        groups[tuple(row.get(key) for key in keys)].append(row)
    return [
        {**dict(zip(keys, values)), **summarize(rows)}
        for values, rows in sorted(groups.items(), key=lambda item: tuple(str(v) for v in item[0]))
    ]


def sweep(
    raw_trials: Iterable[dict],
    thresholds: Iterable[float],
    margins: Iterable[float],
) -> list[dict]:
    rows = list(raw_trials)
    output = []
    for threshold in thresholds:
        for minimum_margin in margins:
            decided = []
            for row in rows:
                decision = decide(row.get("scores", {}), threshold, minimum_margin)
                decided.append({**row, "predicted_id": decision.predicted_id})
            output.append(
                {
                    "threshold": threshold,
                    "minimum_margin": minimum_margin,
                    **summarize(decided),
                }
            )
    return output


def select_eer(rows: Iterable[dict]) -> dict:
    candidates = [row for row in rows if row["far"] is not None and row["frr"] is not None]
    if not candidates:
        raise ValueError("EER requires registered and Unknown trials")
    selected = min(
        candidates,
        key=lambda row: (
            abs(row["far"] - row["frr"]),
            (row["far"] + row["frr"]) / 2.0,
            row["threshold"],
            row["minimum_margin"],
        ),
    )
    return {**selected, "eer": (selected["far"] + selected["frr"]) / 2.0}


def _rate(numerator: int, denominator: int) -> float | None:
    return numerator / denominator if denominator else None
