import json
import tempfile
import unittest
from pathlib import Path

from scripts.reporting.generate_report import (
    detect_warnings,
    generate_reports,
    numeric_statistics,
    parse_jsonl,
)


def event(
    *,
    run_id: str = "run-1",
    device: str = "phone-a",
    duration: float | None = 10.0,
    dropped: int = 0,
) -> dict:
    return {
        "schemaVersion": 1,
        "eventType": "face_pipeline",
        "run": {
            "runId": run_id,
            "scenarioId": "A-FACE-001",
            "timestampEpochMillis": 1_000,
            "timestampElapsedRealtimeMillis": 500,
            "device": {
                "manufacturer": "Example",
                "model": device,
                "apiLevel": 30,
                "abi": "arm64-v8a",
            },
            "buildVariant": "benchmarkDebug",
            "input": {
                "descriptor": "fixed-face-frame",
                "sha256": "a" * 64,
                "preprocessingId": "opencv-sface-aligncrop-v1",
                "datasetId": "local-fixed-inputs-v1",
                "repetition": 1,
            },
            "models": [
                {
                    "role": "FACE_EMBEDDING",
                    "modelSpaceId": "sface-space",
                    "artifactId": "sface-onnx",
                    "runtimeId": "opencv",
                }
            ],
            "thresholds": {"identification": 0.55, "minimumLead": 0.05},
        },
        "face": {"detectionMillis": duration, "embeddingMillis": None},
        "dropCount": dropped,
        "stallCount": 0,
        "status": "SUCCESS",
    }


class ReportTest(unittest.TestCase):
    def test_parse_jsonl_accepts_blank_lines_and_reports_invalid_json(self) -> None:
        parsed = parse_jsonl(
            [
                json.dumps(event()),
                "",
                "{not-json}",
            ]
        )

        self.assertEqual(1, len(parsed.events))
        self.assertTrue(any("line 3" in warning for warning in parsed.warnings))

    def test_statistics_ignore_null_and_use_nearest_rank(self) -> None:
        events = [event(duration=float(value)) for value in range(1, 21)]
        events.append(event(duration=None))

        stats = numeric_statistics(events)["face.detectionMillis"]

        self.assertEqual(20, stats.measured_count)
        self.assertEqual(1, stats.missing_count)
        self.assertEqual(10.0, stats.p50)
        self.assertEqual(19.0, stats.p95)
        self.assertEqual(20.0, stats.maximum)
        self.assertNotIn("run.timestampEpochMillis", numeric_statistics(events))

    def test_warnings_identify_mixed_conditions_and_drops(self) -> None:
        warnings = detect_warnings(
            [
                event(device="phone-a"),
                event(run_id="run-2", device="phone-b", dropped=2),
            ]
        )

        self.assertTrue(any("mixed condition" in warning for warning in warnings))
        self.assertTrue(any("dropped events" in warning for warning in warnings))

    def test_generate_reports_writes_csv_svg_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)

            result = generate_reports([event()], output)

            self.assertEqual(output / "metrics.csv", result.csv_path)
            self.assertIn("face.detectionMillis", result.csv_path.read_text(encoding="utf-8"))
            self.assertIn("<svg", result.svg_path.read_text(encoding="utf-8"))
            self.assertIn("Benchmark report", result.markdown_path.read_text(encoding="utf-8"))

    def test_svg_doesNotConnectAcrossMissingMeasurements(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            result = generate_reports(
                [event(duration=1.0), event(duration=None), event(duration=3.0)],
                Path(directory),
            )

            svg = result.svg_path.read_text(encoding="utf-8")
            self.assertGreaterEqual(svg.count('<polyline class="series"'), 2)


if __name__ == "__main__":
    unittest.main()
