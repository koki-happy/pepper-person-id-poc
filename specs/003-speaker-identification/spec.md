# Feature Specification: Registered-speaker Identification

**Feature Branch**: `codex/face-identification`  
**Status**: Current implementation documented; constitution alignment, method selection, and evaluation remain incomplete

## Source of truth

- Project principles: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
- Code-grounded overview: [`../../docs/openwiki-current-state.md`](../../docs/openwiki-current-state.md)
- Implementation plan: [`plan.md`](plan.md)
- Remaining work: [`tasks.md`](tasks.md)

## Scope

This feature defines registered-speaker registration and identification using local PCM input and speaker embeddings. It does not include speech recognition, speaker diarization, overlapping-speaker separation, face-speaker fusion, or conversation history.

The feature distinguishes two independent axes:

1. **Speaker embedding model**: converts an utterance into an embedding.
2. **Template aggregation and matching method**: combines multiple enrollment embeddings and produces a person score.

A model conclusion MUST NOT be inferred from a comparison that also changed the matching method. A method conclusion MUST NOT be inferred from a comparison that also changed the embedding model.

## Models

| ID | Model | Runtime dimension | State |
| --- | --- | ---: | --- |
| `Voice-Model-1` | CAM++ English VoxCeleb | 512 | Implemented |
| `Voice-Model-2` | ERes2Net English VoxCeleb | 192 | Implemented; current default |
| `Voice-Reference` | RyuseiNet | model-dependent | PC reference; not deployed to Pepper |

Runtime dimension is read from `SpeakerEmbeddingExtractor.dim()`.

## Matching methods

| ID | Enrollment aggregation | Person score | State |
| --- | --- | --- | --- |
| `Voice-Method-1` | Store every accepted utterance separately | Maximum cosine similarity across all templates | Current implementation |
| `Voice-Method-2` | L2-normalize each template, average, then L2-normalize the centroid | Cosine similarity to one person centroid | Required comparison candidate |
| `Voice-Method-3` | Quality-weighted L2-normalized centroid | Cosine similarity to weighted centroid | Optional after Method-2 |
| `Voice-Method-4` | PLDA-compatible enrollment representation | PLDA log-likelihood ratio | PC research candidate; not initial Pepper scope |

## User Story 1 — Register speaker samples

An operator selects a person, requests registration, and provides at least one second of voiced audio. Each accepted utterance adds one embedding for the selected model.

### Acceptance scenarios

1. Given a pending registration and at least 1,000 ms voiced audio, one embedding is appended to the selected person's selected-model templates.
2. Given less than 1,000 ms voiced audio, return `INSUFFICIENT_AUDIO` and store no template.
3. Templates remain separated by person and model.
4. PCM/model errors store no invalid or partial template.
5. Stored templates remain available for all supported matching methods; changing method does not require re-extracting embeddings from raw audio.

## User Story 2 — Identify a registered speaker

For every completed utterance, the application creates one query embedding, calculates one score per enrolled person using the selected method, and returns a person or `Unknown`.

### Acceptance scenarios

1. Every valid 16 kHz completed utterance is evaluated once.
2. A best score below threshold returns `Unknown`.
3. A best-minus-second score below minimum margin returns `Unknown` after the margin implementation is complete.
4. Reports include `model_id`, `method_id`, threshold, margin, and template count.
5. Overlapping speakers are not claimed to be separated.

## Mathematical specification

### PCM normalization

For mono PCM16 sample $x[n]\in[-32768,32767]$:

$$
z[n]=\frac{x[n]}{32768}
$$

The recorder tries 16 kHz before 44.1 kHz. Current speaker models require 16 kHz; unsupported sample rates must be rejected or resampled explicitly before embedding.

### VAD and utterance segmentation

At 16 kHz, Silero VAD uses threshold 0.5, window 512, minimum silence 0.6 s, minimum speech 1.0 s, maximum speech 10.0 s, CPU provider, and one thread.

For chunk $k$, speech decision $v_k\in\{0,1\}$, sample count $N_k$, and sample rate $F_s$:

$$
T_{voice}=\frac{1000}{F_s}\sum_kN_kv_k
$$

$$
\operatorname{SufficientAudio}\iff T_{voice}\ge1000\,\mathrm{ms}
$$

### Speaker embedding

For utterance $U$ and model $m$:

$$
\mathbf q^{voice}=g_m(U)\in\mathbb R^{d_m}
$$

For person $p$, model $m$, and accepted enrollment utterances:

$$
R^{voice}_{p,m}=\{\mathbf r_{p,m,1},\ldots,\mathbf r_{p,m,M_p}\}
$$

The raw enrollment embeddings are stored separately. Aggregated centroids are derived data and need not replace the stored templates.

### Voice-Method-1 — current maximum-template score

$$
c^{voice}_{p,j}=\cos(\mathbf q^{voice},\mathbf r_{p,m,j})
$$

$$
s^{voice,\max}_p=\max_{1\le j\le M_p}c^{voice}_{p,j}
$$

This method can favor people with more stored templates because more comparisons increase the chance of an extreme score. Template count MUST therefore be reported.

### Voice-Method-2 — normalized centroid

Normalize each enrollment embedding:

$$
\widetilde{\mathbf r}_{p,m,j}=\frac{\mathbf r_{p,m,j}}{\|\mathbf r_{p,m,j}\|_2}
$$

Create and normalize the person centroid:

$$
\overline{\mathbf r}^{voice}_{p,m}
=
\frac{\sum_{j=1}^{M_p}\widetilde{\mathbf r}_{p,m,j}}
{\left\|\sum_{j=1}^{M_p}\widetilde{\mathbf r}_{p,m,j}\right\|_2}
$$

Normalize the query and score:

$$
\widetilde{\mathbf q}^{voice}=\frac{\mathbf q^{voice}}{\|\mathbf q^{voice}\|_2}
$$

$$
s^{voice,centroid}_p
=
\widetilde{\mathbf q}^{voice\top}\overline{\mathbf r}^{voice}_{p,m}
$$

`Voice-Method-2` is the first comparison candidate because it reduces person-score dependence on enrollment-template count and keeps one comparison per person.

### Voice-Method-3 — quality-weighted centroid

For non-negative quality weights $w_j$ with $\sum_jw_j=1$:

$$
\overline{\mathbf r}^{voice,w}_{p,m}
=
\operatorname{Normalize}\left(\sum_{j=1}^{M_p}w_j\widetilde{\mathbf r}_{p,m,j}\right)
$$

Quality may use voiced duration, SNR, clipping, or another versioned and testable measure. This method is not adopted until the quality signal is defined and Method-2 has been evaluated.

### Decision rule

Let $s_p$ be the score produced by the selected matching method:

$$
p_1=\arg\max_ps_p,\qquad p_2=\arg\max_{p\ne p_1}s_p
$$

$$
d^{voice}=s_{p_1}-s_{p_2}
$$

$$
\widehat y^{voice}=
\begin{cases}
p_1,&s_{p_1}\ge\tau_{voice}\land d^{voice}\ge\delta_{voice}\\
\mathrm{Unknown},&\text{otherwise}
\end{cases}
$$

Current code implements threshold-only `Voice-Method-1`; top-two margin and selectable method support are not yet implemented.

## Functional Requirements

- **FR-001**: Capture MUST prefer 16 kHz mono PCM16 and normalize samples by 32768.
- **FR-002**: VAD and segmentation settings MUST be explicit and logged.
- **FR-003**: Less than 1,000 ms voiced duration MUST return `INSUFFICIENT_AUDIO` and store no template.
- **FR-004**: Every accepted registration utterance MUST append one model-separated raw embedding.
- **FR-005**: Model selection and matching-method selection MUST be separate settings/report fields.
- **FR-006**: `Voice-Method-1` and `Voice-Method-2` MUST be implemented in a pure, testable domain component before final evaluation.
- **FR-007**: Reports MUST include model, method, template count, threshold, second score, margin, and decision.
- **FR-008**: Identification MUST return `Unknown` below threshold or margin.
- **FR-009**: Anonymous-speaker IDs, clusters, navigation, UI, and logs MUST be removed.
- **FR-010**: PCM/WAV MUST NOT be persisted.
- **FR-011**: Unsupported sample rates MUST be rejected or resampled explicitly.
- **FR-012**: Japanese and Pepper-microphone evaluation MUST compare models under a fixed method and methods under a fixed model.
- **FR-013**: Threshold and margin MUST be selected separately for every model-method pair using development data only.
- **FR-014**: Overlapping-speaker separation and diarization remain out of scope.

## Current status

| Capability | State |
| --- | --- |
| PCM16 capture and 16 kHz Silero VAD | Implemented |
| Minimum voiced duration | Implemented |
| CAM++ / ERes2Net embedding | Implemented |
| Separate raw templates per person/model | Implemented |
| `Voice-Method-1` maximum score | Implemented |
| `Voice-Method-2` normalized centroid | Not implemented |
| Top-two margin | Not implemented |
| Anonymous-speaker removal | Not implemented |
| Japanese / Pepper microphone qualification | Not complete |

## Success criteria

- Anonymous-speaker functionality is absent from code, UI, tests, and logs.
- `Voice-Method-1` and `Voice-Method-2` have deterministic unit tests.
- Model and method are separately selectable or separately evaluated without ambiguity.
- Unsupported sample rates cannot reach speaker embedding silently.
- J-SpAW, JVS, and Pepper evidence report metrics for each evaluated model-method pair.
- Final model, method, threshold, and margin are selected from Japanese and Pepper evidence rather than smoke tests.
