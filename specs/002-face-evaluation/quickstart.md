# Quickstart: Face Evaluation

1. Acquire BIWI and Pointing'04 into `datasets/` and verify publisher metadata/checksums.
2. Validate and create deterministic manifests for both datasets.
3. Run SFace and 0095 with front-only and multi-angle enrollment.
4. Confirm zero leakage and inspect overall/pose/cross-series metrics.
5. On Pepper, open the face screen, execute a short run, then a 30-minute continuous run.
6. Summarize timings/resources/drops/stalls and list unavailable measurements explicitly.

Expected evidence is recorded under `evaluation-results/face/` and summarized in this feature directory.
Dataset bodies and generated embedding caches must remain ignored and outside the APK.
