# Contract: Metrics and Reports

## Measurement semantics

- Duration uses monotonic time.
- Missing values are null/`未測定`, never zero.
- Every event carries runId, scenarioId, device/API/ABI, build variant, artifact/model-space/runtime IDs, and thresholds.
- Face stages and speaker stages are measured separately.
- CPU, PSS, Java heap, native heap, device memory, drop count, stall count, and errors retain collection timestamps.

## Live display

- Current values update approximately once per second.
- 60-second and 300-second ring buffers preserve missing gaps.
- p50, p95, and max are derived only from measured values.
- Full candidates use a virtualized list.

## Structured logging

- benchmark: JSONL enabled.
- candidate: file logging disabled by default and requires explicit user action.
- Never log raw media, embeddings, or dataset bodies.

## Report generation

One JSONL source generates:

- CSV tables
- SVG charts
- Markdown summary
- warnings for missing fields, mixed conditions, dropped events, and incompatible comparisons

Comparisons are valid only when model, preprocessing, input, dataset, threshold, device conditions, and repetitions are recorded.
