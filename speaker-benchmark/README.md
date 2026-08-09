# Speaker benchmark CLI foundation

This module owns the Windows/JVM command line boundary, deterministic input validation, and report files. Model-specific ONNX preprocessing and inference are supplied through `BenchmarkRunner`; the validation path does not load a native inference runtime.

From the repository root:

```powershell
.\gradlew.bat :speaker-benchmark:run --args="--config config/speaker-benchmark.json --validate-only"
.\gradlew.bat :speaker-benchmark:run --args="--inspect-model models/model.onnx"
.\gradlew.bat :speaker-benchmark:test
```

Paths in the JSON config are resolved relative to the config file itself.

```json
{
  "manifestPath": "../data/manifests/speaker.csv",
  "outputDirectory": "../results/windows-speaker-benchmark",
  "models": [
    {
       "name": "campplus-zh-en",
       "adapter": "campplus-zh-en",
       "modelPath": "../models/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx",
      "expectedSha256": "0000000000000000000000000000000000000000000000000000000000000000",
      "sampleRate": 16000,
      "embeddingDimension": 192,
      "threshold": 0.6,
      "margin": 0.1
    }
  ],
  "durationsSeconds": [2, 3, 5],
  "warmupRuns": 1,
  "measuredRuns": 5,
  "vadEnabled": false,
  "thresholdSelection": "development",
  "validationProfile": "full"
}
```

The manifest header and order are exact:

```csv
speaker_id,utterance_id,source_group_id,split,path,language,sample_rate,duration_sec,recording_device,recording_type
```

Supported splits are `enrollment`, `development`, `test`, and `unknown`. Supported recording types are `pepper_live`, `pepper_replay`, `public_dataset`, and `external_microphone`. A `source_group_id` may occur multiple times only within one split; crossing any split boundary is rejected as leakage. Reusing a resolved WAV path or identical decoded audio content anywhere in the manifest is also rejected. WAV inputs must be 16 kHz mono PCM16 or IEEE float32.

`validationProfile` must make the run's purpose explicit. `full` is the acceptance profile: it hard-fails unless the dataset has at least four enrolled and four final-unknown speakers, the required per-speaker enrollment/development/test counts, four development-unknown speakers, Japanese speech, and complete 2/3/5-second test and unknown groups. Evaluation rows outside the configured duration tolerance are rejected. `smoke` permits the intentionally tiny pinned smoke corpus and must not be treated as an acceptance run.

The report writer creates `summary.md`, `metrics.csv`, `predictions.csv`, `thresholds.json`, `same-speaker-scores.csv`, `different-speaker-scores.csv`, `environment.json`, and `models.json` with atomic replacement.
