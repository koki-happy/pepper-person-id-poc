# Face Evaluation Results

**Measured**: 2026-07-18

## Dataset acquisition status

| Dataset | Status | Evidence |
|---|---|---|
| Pointing'04 | COMPLETE | Official Figshare v2; 31/31 archives matched size/MD5; 2,790/2,790 canonical images decoded at 384×288 |
| BIWI | BLOCKED_UPSTREAM | Official 6,014,398,431-byte ETH archive returns HTTP 403; HF repository has loader only; HyperAI mirror has all 24-series annotations but its 6 GB RGB ZIP is an incomplete sparse aria2 target |

Pointing'04 uses subjects 01–09 as enrolled identities, 10–12 as development Unknown, and 13–15
as final Unknown. Series 1 supplies enrollment/development registered probes and series 2 supplies
final registered probes. Final metrics therefore include a capture-series change for every enrolled
subject. The final split contains 837 registered and 558 Unknown trials (1,395 total).

## Pointing'04 overall final results

Threshold and top-two margin were selected only on the development split at the closest measured
FAR/FRR point, then applied unchanged to the final split.

| Model | Enrollment | Threshold | Margin | Dev EER | FAR | Misidentification | Correct ID | FRR |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 0095 | front | 0.20 | 0.18 | 3.72% | 1.61% | 0.00% | 93.43% | 6.57% |
| 0095 | front/left/right | 0.34 | 0.18 | 2.92% | 0.72% | 0.00% | 95.58% | 4.42% |
| SFace | front | 0.28 | 0.10 | 8.35% | 6.45% | 0.12% | 92.71% | 7.17% |
| SFace | front/left/right | 0.34 | 0.08 | 7.77% | 3.23% | 0.00% | 94.03% | 5.97% |

For this protocol, multi-angle enrollment improved 0095 correct identification by 2.15 percentage
points and SFace by 1.31 points. It also reduced FAR and FRR for both models and removed the one SFace
wrong-person decision observed with front-only enrollment.

YuNet produced exactly one face for 2,756/2,790 images and no face for 34/2,790; no multi-face image
was observed. Detection failures remain trial outcomes and are not silently discarded.

## Pointing'04 multi-angle results by absolute yaw

| Model | Yaw | FAR | Misidentification | Correct ID | FRR |
|---|---:|---:|---:|---:|---:|
| 0095 | 0° | 0.00% | 0.00% | 83.95% | 16.05% |
| 0095 | 15° | 1.19% | 0.00% | 98.41% | 1.59% |
| 0095 | 30° | 0.00% | 0.00% | 97.62% | 2.38% |
| 0095 | 45° | 0.00% | 0.00% | 98.41% | 1.59% |
| 0095 | 60° | 0.00% | 0.00% | 96.83% | 3.17% |
| 0095 | 75° | 1.19% | 0.00% | 96.83% | 3.17% |
| 0095 | 90° | 2.38% | 0.00% | 92.86% | 7.14% |
| SFace | 0° | 3.70% | 0.00% | 83.95% | 16.05% |
| SFace | 15° | 8.33% | 0.00% | 98.41% | 1.59% |
| SFace | 30° | 2.38% | 0.00% | 98.41% | 1.59% |
| SFace | 45° | 2.38% | 0.00% | 97.62% | 2.38% |
| SFace | 60° | 3.57% | 0.00% | 96.83% | 3.17% |
| SFace | 75° | 1.19% | 0.00% | 93.65% | 6.35% |
| SFace | 90° | 1.19% | 0.00% | 85.71% | 14.29% |

Yaw 0° includes all pitch values, including ±90°, so its aggregate is not a frontal-only result.

## Pointing'04 multi-angle results by absolute pitch

| Model | Pitch | FAR | Misidentification | Correct ID | FRR |
|---|---:|---:|---:|---:|---:|
| 0095 | 0° | 1.28% | 0.00% | 99.15% | 0.85% |
| 0095 | 15° | 0.00% | 0.00% | 99.15% | 0.85% |
| 0095 | 30° | 0.00% | 0.00% | 98.72% | 1.28% |
| 0095 | 60° | 1.92% | 0.00% | 91.88% | 8.12% |
| 0095 | 90° | 0.00% | 0.00% | 33.33% | 66.67% |
| SFace | 0° | 3.85% | 0.00% | 98.29% | 1.71% |
| SFace | 15° | 1.92% | 0.00% | 98.29% | 1.71% |
| SFace | 30° | 3.21% | 0.00% | 99.15% | 0.85% |
| SFace | 60° | 4.49% | 0.00% | 87.18% | 12.82% |
| SFace | 90° | 0.00% | 0.00% | 33.33% | 66.67% |

## Reproducibility

- Manifest sample count: 2,790 for both protocols; duplicate sample/subject-series-pose keys: zero.
- Subject leakage between enrolled, development Unknown, and final Unknown: zero.
- Repeated SFace front evaluation produced identical `trials.csv` and threshold-sweep SHA-256 values.
- Trial SHA-256: `c501f5c1fb12af9f5361bfadd3c22c7ae13df4e417df491ca4950b88c85c5cd9`.
- Sweep SHA-256: `0342f11d9464a018e0a7cdff806369d45bffe0bbc6bc42bf2d8d3dcda6af34d6`.
- Timing summaries may vary because failed detections are intentionally retried; identity decisions and
  metric counts remain deterministic.

## Pepper initial performance evidence

The pre-telemetry baseline used the current ARMv7 app and 0095 with one registered person.

| Metric | Measured value |
|---|---|
| Face detection, face present | avg 560.37 ms / P95 663 ms / max 778 ms (n=190) |
| Pose estimation | avg 0.30 ms / P95 1 ms / max 54 ms (n=625; includes no-face frames) |
| 0095 identification | avg 529.60 ms / P95 581 ms / max 657 ms (n=50) |
| 0095 request total | avg 544.08 ms / P95 600 ms / max 1,153 ms (n=50) |
| Effective analysis frequency | approximately 0.97 fps |
| CPU during 77.7-second no-face run | avg 55.7% / max 61% (Android `top`, one-core-style process value) |
| TOTAL PSS | avg 123,234 KB / max 126,962 KB / start-to-end +1,052 KB |
| UI rendering | 55.12% janky frames, P95 40 ms (gfxinfo; not camera drop rate) |
| Crash/ANR | 0 |

The installed telemetry update adds analyzer input, analyzed, throttle-skipped, full pipeline, and
capture-to-state-ready counters. True camera-producer drops under `KEEP_ONLY_LATEST` and true
camera-to-visible-pixel latency remain unavailable; they must not be represented as zero.

### Latest ARMv7 telemetry smoke run

The telemetry build was installed on Pepper API 23 / ARMv7 and measured for 120,027 ms with no face
visible. The process remained alive for all 24 resource samples.

| Metric | Measured value |
|---|---|
| Full face pipeline | avg 544.32 ms / P95 673 ms / max 697 ms (n=116) |
| Capture to state ready | avg 644.36 ms / P95 781 ms / max 807 ms (n=116) |
| Face detection | avg 542.93 ms / P95 672 ms / max 696 ms (n=116) |
| Analyzer callback counters | input +990 / analyzed +113 / throttle-skipped +877; final throttle drop rate 88.53% |
| CPU | avg 40.5% / P95 80% / max 107% (Android process CPU may exceed 100% across cores) |
| TOTAL PSS | avg 140,679 KB / P95 145,294 KB / start-to-end +5,964 KB |
| UI rendering | 54.78% janky frames |

This no-face smoke run validates the instrumentation and stability path, not face-present identity
latency or the 30-minute acceptance condition.

## Remaining external evidence gates

- BIWI full evaluation: blocked by unavailable official archive. A byte/hash-identical archive is needed.
- Pepper 30-minute telemetry run: pending completion after installing the telemetry build.
- Pepper simultaneous multi-face run: requires two faces physically visible to the camera.
