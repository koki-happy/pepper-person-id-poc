# Data Model: Face Evaluation

## DatasetSample

- `dataset`, `subjectId`, `seriesId`, `imagePath`, `imageSha256`
- `yawDegrees`, `pitchDegrees`, optional `rollDegrees`
- `role`: enrollment, development-registered, development-unknown, final-registered, final-unknown
- Validation: path exists, pose finite/in range, role assignment deterministic, checksum stable

## EvaluationProtocol

- dataset/version/source/license, seed, subject assignments
- enrollment strategy: front-only or front-left-right
- model, detector threshold, identity threshold sweep, margin sweep
- pose-band definitions and permitted exclusions
- Validation: no subject/sample leakage, both registered and Unknown final trials exist

## IdentityTrial

- sample identity/pose/role, expected ID or Unknown
- detection outcome, best/second candidate, scores, margin, predicted ID or Unknown
- inference timings and error/exclusion reason

## AccuracySummary

- trial/sample counts and detection failures
- correct identification, misidentification, FAR, FRR, EER
- breakdown keys: dataset, model, protocol, split, yaw band, pitch band, series relation
- selected development threshold/margin and untouched final metrics

## PepperPerformanceRun

- device/app/model/settings/run scenario/start/end/duration
- raw event and host-sample file paths/checksums
- average/max/P95 stage and state-ready latencies
- effective/min FPS, CPU average/max, memory average/max/growth
- dropped count/rate, stalls/max stop, pose update rate
- `unavailableMetrics` with reasons; validity and interruption reason
