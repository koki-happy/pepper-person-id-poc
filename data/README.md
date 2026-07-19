# Speaker benchmark data

Audio, face images, embeddings, and other biometric data are local-only inputs and must not be
committed. The tracked CSV files under `manifests/` contain metadata and relative paths only.

The authoritative manifest schema is:

```csv
speaker_id,utterance_id,source_group_id,split,path,language,sample_rate,duration_sec,recording_device,recording_type
```

Allowed `split` values are `enrollment`, `development`, `test`, and `unknown`. A
`source_group_id` may occur in only one split. The benchmark rejects a manifest before inference
when this rule is violated, preventing transformed or replayed copies of the same source from
leaking across enrollment and evaluation.

`sherpa-chinese-smoke.csv` is a three-file wiring check, not a Japanese accuracy dataset. It has
one enrolled speaker and one unknown speaker, so it cannot establish production thresholds,
margin behavior, FAR/FRR confidence, or 2/3/5-second accuracy.

The local JVS evaluation is prepared with:

```powershell
.\scripts\windows\download-jvs.ps1 -AcceptTerms
.\scripts\windows\prepare-jvs-evaluation.ps1
.\scripts\windows\run-speaker-benchmark.ps1 -Config config/speaker-benchmark-jvs.json
```

The pinned local archive is 3,536,595,425 bytes with SHA-256
`37180e2f87bd1a3e668d7c020378f77cebf61dd57d4d74c71eb0114f386a3999`. This is a
locally measured reproducibility pin, not a publisher checksum. The preparer creates 168 WAVs:
24 enrollment, 48 development (24 registered and 24 unknown from separate speakers), 48 test,
and 48 final unknown. Each of 2, 3, and 5 seconds has 56 clips, and no `source_group_id` crosses
a split. `data/audio/jvs-evaluation/preparation-metadata.json` is the local generation record.

JVS media is local-only and must not be redistributed. The public studio corpus is useful for a
Japanese model comparison, but it does not replace recordings made through the Pepper microphone.
