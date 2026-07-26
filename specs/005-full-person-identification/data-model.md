# Data Model: Pepper匿名人物識別の完全実装

## Identity and model identifiers

### ModelSpaceId

特徴量を相互比較できる空間の安定ID。変換物が元モデルと同等性ゲートに合格した場合だけ共有できる。

Validation:

- 空文字不可。
- modality、embedding dimension、normalization semanticsが一意に定まる。
- 同等性未確認のartifact間では共有不可。

### ArtifactId

実モデルファイルと変換来歴の安定ID。

Validation:

- `ModelArtifactRecord`内で一意。
- ファイルhash、size、format、input/output contractを持つ。
- 変換物はsource artifactとconversion recordを持つ。

### RuntimeId

推論基盤と実装versionの安定ID。

Validation:

- runtime名とversionを含む。
- ABI/API/providerの互換性は`ModelRuntimeCompatibility`で管理する。

## Model catalog

### ModelArtifactRecord

| Field | Type | Rules |
|---|---|---|
| artifactId | ArtifactId | unique, required |
| modelSpaceId | ModelSpaceId? | embedding artifactのみ。未同等なら独立ID |
| modality | enum | FACE, SPEAKER, AUDIO_ACTIVITY |
| role | enum | DETECTION, EMBEDDING, VAD, SEGMENTATION |
| architecture | string | required |
| precision | enum | FP32, FP16, INT8, MIXED |
| format | enum | ONNX, TFLITE, NCNN, MNN, OPENVINO_IR, SDK_INTERNAL |
| filename | string? | SDK internal以外required |
| sourceUrl | URI | required |
| sourceRevision | string | immutable revision preferred |
| sha256 | string? | bundled/downloaded artifactは64桁hex |
| fileSizeBytes | long? | bundled/downloaded artifactはpositive |
| inputShape | list | dynamic axisを明示 |
| inputLayout | string | required |
| inputColorOrder | string? | image modelのみ |
| normalization | object | scale/mean/std/channel order |
| outputShape | list | required |
| outputSemantics | string | required |
| opset | int? | ONNXのみ |
| conversion | ConversionRecord? | converted artifactのみ |
| runtimeCompatibility | list | at least one status |
| weightLicense | string | release candidateはverified value |
| commercialUse | enum | ALLOWED, PROHIBITED, UNKNOWN |
| licenseEvidence | URI? | candidateはhttps URI required |

### ConversionRecord

sourceArtifactId、tool、toolRevision、environmentDigest、command、convertedAt、outputHash、tensorMetadata、warningsを保持する。

### ModelRuntimeCompatibility

| Field | Type |
|---|---|
| artifactId | ArtifactId |
| runtimeId | RuntimeId |
| abi | string |
| minApi | int |
| status | VERIFIED, BUILDABLE, CONVERSION_REQUIRED, UNSUPPORTED, BLOCKED |
| reason | string? |
| evidence | string? |

State transitions:

```text
CONVERSION_REQUIRED -> BUILDABLE -> VERIFIED
BUILDABLE -> BLOCKED
VERIFIED -> BLOCKED
any -> UNSUPPORTED
```

`VERIFIED`には実行端末、ABI、API、artifact hash、test resultが必要。

## Identification domain

### IdentificationCandidateScore

rank、anonymousId、score、modelSpaceId、embeddingDimension、updateCount、selectedを持つ。

Validation:

- scoreはfinite。
- rankは1始まりで連続。
- score降順、同点はanonymousId昇順。
- selectedは最大1件。

### IdentificationEvaluation

candidates、highestScore、secondHighestScore、highestCandidateLead、threshold、minimumLead、decisionを持つ。

Decision:

- `MATCHED_EXISTING`: thresholdとminimum leadを満たす既存候補。
- `CREATED_NEW`: 既存一致なしで作成可。
- `AMBIGUOUS`: threshold付近またはlead不足。
- `UNKNOWN`: 互換候補なし、非有限入力、または判定不能。

候補1件ではsecond scoreとleadはnullとし、lead条件は成立扱い。

### PersistencePolicyResult

createEligible、updateEligible、reasonsを持つ。`updateEligible`がtrueなら`createEligible`もtrueでなければならない。

### PersistenceOperation

`CREATE`, `UPDATE`, `HOLD`。

State transitions:

```text
evaluation + quality -> policy -> operation
CREATE -> new session cluster
UPDATE -> existing session cluster
HOLD   -> repository unchanged
```

## Session anonymous cluster

### AnonymousCluster

anonymousId、modality、modelSpaceId、embeddingDimension、normalizedEmbeddingSum、centroid、updateCount、createdAtElapsedRealtime、updatedAtElapsedRealtimeをメモリ内だけに保持する。

Validation:

- face ID prefixは`anonymous-face-`、speaker ID prefixは`anonymous-speaker-`。
- modality間で候補照合・ID関連付けを行わない。
- embeddingとcentroidはfinite、centroidはL2 norm 1。
- updateCountは1以上、最大件数を超えた場合もbounded aggregation規則を守る。
- serialize/deserialize契約をproductionへ持たない。

Lifecycle:

```text
Application session start -> empty
valid CREATE/UPDATE       -> in-memory mutation
navigation/recreation     -> retained in Application scope
explicit exit/process end -> discarded
```

## Face pipeline

### FaceQualityAssessment

faceWidthPixels、faceHeightPixels、detectionConfidence(nullable)、landmarkCompleteness、blurScore、brightnessMean、clippedRatio、yaw、pitch、roll、edgeTruncationRatio、trackDurationMillis、createEligible、updateEligible、rejectionReasons。

ML KitのdetectionConfidenceはnullであり、品質方針はnullを1.0として扱わない。

### FaceObservation

frameId、detectionId、boundingBox、fiveLandmarks、quality、model IDs、embeddingDimension、stage timings、evaluation、policy、operation。画面状態またはbenchmark eventであり、セッションを越えて生体特徴量を保持しない。

## Speaker pipeline

### SpeakerActivityState

`SILENCE`, `SINGLE_SPEAKER`, `OVERLAPPED_SPEECH`, `MULTIPLE_ACTIVE_SPEAKERS`, `UNSUPPORTED`, `ERROR`。

### DiarizationWindow

windowId、startSample、endSample、sampleRate、activity probabilities、active speaker count、overlap ratio、runtime IDs、inference timing。

### DiarizedSpeakerSegment

localSpeakerId、startSample、endSample、activityState、confidence、isSolo。

### LocalSpeakerTrack

localSpeakerId、firstSeen、lastSeen、window memberships、solo segments、state。

### SpeakerAudioQualityAssessment

sampleRate、sampleCount、durationMillis、voicedRatio、peak、rms、clippingRatio、snr(nullable)、overlapRatio、activeSpeakerCount、createEligible、updateEligible、rejectionReasons。

### SpeakerObservation

utteranceId、localSpeakerId、quality、model IDs、segment count、stage timings、RTF、evaluation、policy、operation。PCMやembeddingを評価ログへ含めない。

## Metrics and distribution

### BenchmarkEvent

runId、scenarioId、timestamp、device、API、ABI、build variant、model IDs、input descriptor/hash、thresholds、stage timings、RTF、CPU、PSS、Java/native heap、device memory、candidate count、drop/stall/error、nullable measurementsを持つ。

Prohibited fields:

- raw image/video/audio bytes
- face/speaker embedding vector
- dataset body or identifying source path

### DistributionManifest

variant、APK hash/size、ABIs、native libraries with hashes、model artifacts、runtime dependencies、license status、audit findings。

Candidate invariant:

- selected artifacts/runtimes以外を含まない。
- `armeabi-v7a`を含む。
- BLOCKED/UNKNOWN licenseを含まない。
