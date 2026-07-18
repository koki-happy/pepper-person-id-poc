# Feature Specification: Registered-speaker Identification

**Feature Branch**: `codex/face-identification`  
**Status**: Current implementation documented; constitution alignment and evaluation remain incomplete

## Source of truth

- Project principles: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
- Code-grounded overview: [`../../docs/openwiki-current-state.md`](../../docs/openwiki-current-state.md)
- Implementation plan: [`plan.md`](plan.md)
- Remaining work: [`tasks.md`](tasks.md)
- Current behavior: `AndroidPcmAudioRecorder`, `PcmUtteranceSegmenter`, `SherpaSileroVoiceActivityDetector`, `SherpaOnnxSpeakerEmbeddingEngine`, `SpeakerIdentifier`, and `SpeakerIdentityCoordinator`.

## Scope

This feature defines registered-speaker registration and identification using local PCM input and speaker embeddings. It does not include speech recognition, speaker diarization, overlapping-speaker separation, face-speaker fusion, or conversation history.

The current code already supports PCM capture, VAD segmentation, CAM++ / ERes2Net embeddings, multiple templates per registered person, and threshold-based 1-to-N identification. The feature remains incomplete because anonymous-speaker identification still exists, top-two margin is not implemented, and Japanese / Pepper microphone qualification is not complete.

## User Story 1 — Register speaker samples

An operator selects a person, requests registration, and provides at least one second of voiced audio. Each accepted utterance adds one speaker embedding for the selected model.

### Acceptance scenarios

1. Given a pending registration and at least 1,000 ms of voiced audio, one embedding is appended to that person's selected-model templates.
2. Given less than 1,000 ms of voiced audio, the result is `INSUFFICIENT_AUDIO` and no template is stored.
3. Given a different speaker model, templates remain separated by model name.
4. Given a PCM or model error, no invalid or partial embedding is stored.

## User Story 2 — Identify a registered speaker

For every completed utterance, the application compares one query embedding against all registered people for the selected speaker model and returns a registered person or `Unknown`.

### Acceptance scenarios

1. Given valid 16 kHz audio and registered templates, each utterance is evaluated once after segmentation.
2. Given the best person score below threshold, the result is `Unknown`.
3. Given a future top-two margin implementation, a best-minus-second score below the minimum margin also returns `Unknown`.
4. Given no registered templates, no registered person is returned.
5. Given overlapping speakers, the application does not claim to separate them; the mixed utterance remains outside the guaranteed acceptance scope.

## Mathematical specification

### PCM normalization

AudioRecord produces mono PCM16 samples $x[n]\in[-32768,32767]$. Inference uses

$$
z[n]=\frac{x[n]}{32768}
$$

The recorder tries 16 kHz first and 44.1 kHz second. The current speaker embedding engine requires 16 kHz, so 44.1 kHz capture is not a valid speaker-embedding path without resampling.

### VAD and utterance segmentation

At 16 kHz, Silero VAD uses threshold 0.5, window size 512, minimum silence 0.6 s, minimum speech 1.0 s, maximum speech 10.0 s, CPU provider, and one thread.

For chunk $k$, let $v_k\in\{0,1\}$ be the speech decision and $N_k$ its sample count. Voiced duration is

$$
T_{voice}=\frac{1000}{F_s}\sum_kN_kv_k
$$

The input is sufficient only when

$$
\operatorname{SufficientAudio}\iff T_{voice}\ge1000\,\mathrm{ms}
$$

The segment starts at the first speech chunk and ends after 600 ms trailing silence or 10,000 ms total duration.

### Speaker embedding

For utterance $U$ and selected model $m$:

$$
\mathbf q^{voice}=g_m(U)\in\mathbb R^{d_m}
$$

Current models are CAM++ and ERes2Net. Runtime dimension is taken from `SpeakerEmbeddingExtractor.dim()`; the current English models produce 512 and 192 dimensions respectively.

### Registered template storage

For person $p$ and model $m$, speaker templates are stored separately and appended per accepted registration utterance:

$$
R^{voice}_{p,m}=\{\mathbf r_{p,m,1},\ldots,\mathbf r_{p,m,M_p}\}
$$

The implementation does **not** average or concatenate the templates. $M_p$ is not fixed.

### Current identification aggregation

Cosine similarity is

$$
\cos(\mathbf a,\mathbf b)=\frac{\mathbf a^\top\mathbf b}{\|\mathbf a\|_2\|\mathbf b\|_2}
$$

For person $p$ and registered template $j$:

$$
c^{voice}_{p,j}=\cos(\mathbf q^{voice},\mathbf r_{p,m,j})
$$

The person's score is the maximum similarity across that person's templates:

$$
s^{voice}_{p}=\max_{1\le j\le M_p}c^{voice}_{p,j}
$$

The best person is

$$
p_1=\arg\max_ps^{voice}_p
$$

Current code uses threshold only:

$$
\widehat y^{voice}=\begin{cases}
p_1,&s^{voice}_{p_1}\ge\tau_{voice}\\
\mathrm{Unknown},&\text{otherwise}
\end{cases}
$$

Current default is $\tau_{voice}=0.60$.

### Required convergence: top-two margin

The target feature adds

$$
p_2=\arg\max_{p\ne p_1}s^{voice}_p
$$

$$
d^{voice}=s^{voice}_{p_1}-s^{voice}_{p_2}
$$

and decides

$$
\widehat y^{voice}_{target}=\begin{cases}
p_1,&s^{voice}_{p_1}\ge\tau_{voice}\land d^{voice}\ge\delta_{voice}\\
\mathrm{Unknown},&\text{otherwise}
\end{cases}
$$

This margin equation is a target requirement and is not yet the current `SpeakerIdentifier` behavior.

## Functional Requirements

- **FR-001**: Capture MUST prefer 16 kHz mono PCM16 and normalize samples by 32768 for VAD and speaker inference.
- **FR-002**: Silero VAD MUST segment 16 kHz audio with explicit threshold, minimum speech, minimum silence, and maximum duration settings.
- **FR-003**: Less than 1,000 ms voiced duration MUST return `INSUFFICIENT_AUDIO` and MUST NOT store a template.
- **FR-004**: Each accepted registration utterance MUST append one separate template for the selected model.
- **FR-005**: Registered templates MUST remain separated by person and speaker model.
- **FR-006**: Current per-person score MUST be the maximum cosine similarity across that person's registered templates.
- **FR-007**: Identification MUST return `Unknown` below threshold.
- **FR-008**: The completed feature MUST also return `Unknown` below the top-two minimum margin.
- **FR-009**: Anonymous-speaker IDs, clusters, navigation, result UI, and anonymous logging MUST be removed.
- **FR-010**: PCM and WAV MUST NOT be persisted; only embeddings, metadata, and structured performance events may be stored.
- **FR-011**: The feature MUST be evaluated with Japanese speech and Pepper microphone input before model and threshold selection is final.
- **FR-012**: Overlapping-speaker separation and diarization are out of scope and MUST NOT be represented as implemented.
- **FR-013**: Unsupported sample-rate behavior MUST be explicit; 44.1 kHz capture MUST be resampled or rejected before speaker embedding.

## Current status

| Capability | State |
| --- | --- |
| PCM16 capture | Implemented |
| 16 kHz Silero VAD | Implemented |
| 44.1 kHz energy-VAD fallback | Implemented for segmentation, but incompatible with current 16 kHz-only speaker embedding |
| Minimum 1,000 ms voiced duration | Implemented |
| CAM++ / ERes2Net embedding | Implemented |
| Multiple templates per person | Implemented |
| Per-person maximum similarity | Implemented |
| Threshold-based `Unknown` | Implemented |
| Top-two margin | Not implemented |
| Anonymous-speaker removal | Not implemented |
| Japanese / Pepper microphone qualification | Not complete |

## Success criteria

- Anonymous-speaker functionality is absent from navigation, code, tests, and logs.
- Threshold and margin decisions are covered by unit tests.
- Unsupported sample rates cannot reach speaker embedding silently.
- J-SpAW, JVS, and Pepper microphone evidence report FAR, MIR, Accuracy, FRR, and EER under a reproducible protocol.
- Selected models and thresholds are based on Japanese and Pepper measurements rather than English-only smoke evidence.
