# Evaluation datasets

評価データ本体、展開ファイル、抽出フレーム、変換音声はGitへコミットしない。ローカルの`datasets/`以下へ保存する。

## Lombard GRID

- Official name: The Audio-Visual Lombard Grid Speech corpus
- Official page: https://spandh.dcs.shef.ac.uk/avlombard/
- License: Creative Commons Attribution 4.0 International
- License URL: https://creativecommons.org/licenses/by/4.0/
- Paper: Najwa Alghamdi, Steve Maddock, Ricard Marxer, Jon Barker and Guy J. Brown, “A corpus of audio-visual Lombard speech with frontal and profile views,” JASA 143, EL523 (2018)
- DOI: https://doi.org/10.1121/1.5042758
- Contents: 54 talkers, 100 utterances per talker, 50 plain and 50 Lombard utterances
- Modalities: audio, synchronized front video, synchronized side video, alignment, metadata
- Speaker labels: `s2` to `s55`; talker `s1` was discarded by the corpus authors
- File convention: `SPKR_COND_UTTERANCE.wav|.mov`; `p` is plain and `l` is Lombard

### Attribution

The evaluation uses the Audio-Visual Lombard Grid Speech corpus by Najwa Alghamdi, Steve Maddock, Ricard Marxer, Jon Barker and Guy J. Brown, licensed under CC BY 4.0. Source: https://spandh.dcs.shef.ac.uk/avlombard/. The local evaluation pipeline may extract frames, crop faces, resample audio to 16 kHz mono PCM16, split utterances, and generate embeddings. These are modifications to the downloaded corpus and are not redistributed by this repository.

### Purpose

Use Lombard GRID for:

- face detection under front and profile views
- face identification with held-out utterance videos
- speaker identification with held-out utterances
- robustness comparison between plain and Lombard speech
- registered-person, wrong-person and unknown-person error measurement
- face and speaker fusion tests using known ground-truth speaker IDs

Do not use Lombard GRID for:

- Japanese transcription accuracy
- final Pepper microphone noise calibration
- final Pepper camera distance or lighting calibration
- production identity thresholds without Pepper and Japanese-speaker measurements

### Initial two-person evaluation

Run two complementary subsets so that a single easy gender split does not overstate accuracy.

| Subset | Registered person A | Registered person B | Unknown speakers | Purpose |
|---|---|---|---|---|
| Smoke | `lombard-s2` | `lombard-s6` | `s3`, `s7` | End-to-end plumbing with two visibly and vocally distinct talkers |
| Confusion | `lombard-s2` | `lombard-s3` | `s4`, `s5` | Same-gender confusion measurement |

After the initial PoC works, repeat with a female same-gender pair such as `s6` and `s7` and at least two different unknown speakers.

### Split rules

For each registered talker:

1. Use a fixed subset of plain front-view utterances for registration.
2. Do not use registration utterances for evaluation.
3. Evaluate held-out plain front-view utterances first.
4. Evaluate held-out Lombard front-view utterances for voice-quality shift.
5. Evaluate side-view video separately for face-angle robustness.
6. Keep audio and video utterance IDs aligned for fusion tests.
7. Record the corpus speaker ID as ground truth, but do not expose a below-threshold best candidate as the final UI identity.

Proposed initial sample counts per registered talker:

- face registration: 5 plain front-view utterances, one quality-filtered frame per utterance
- speaker registration: 5 plain utterances, one embedding per utterance
- face evaluation: 10 held-out plain front, 10 Lombard front, 10 side-view samples
- speaker evaluation: 10 held-out plain and 10 Lombard utterances
- fusion evaluation: 10 held-out synchronized front-video/audio utterances per condition

The exact sample list and random seed must be written to a manifest before measuring model accuracy.

### Metrics

- false accept: unknown talker identified as person A or B
- false reject: registered talker classified as `Unknown`
- identity confusion: person A classified as B or person B classified as A
- face detection rate by front/profile and plain/Lombard condition
- face identification accuracy by view angle
- speaker identification accuracy by plain/Lombard condition
- `CONFIRMED`, `LIKELY`, `UNKNOWN`, `CONFLICT` fusion counts
- processing time and memory use per model and condition

### Data handling

- Keep downloaded archives and extracted content under ignored `datasets/lombard-grid/`.
- Do not put face images, video, WAV, PCM, or generated embeddings in application logs.
- Do not package Lombard GRID media into the APK.
- Keep only attribution, split manifests, aggregate metrics, and non-biometric benchmark metadata in version control.
- Mark every resampling, frame extraction, crop, normalization, or other transformation as a modification in reports derived from the corpus.

### Pepper smoke benchmark samples

2026-07-12のモデル配線・速度確認では、公式ページ掲載の次のサンプルだけを使用した。

- `s22_p_sgbe5s.wav`: talker s22, plain
- `s22_l_sgbe5s.wav`: talker s22, Lombard
- `s16_p_bgah2n.wav`: talker s16, plain

配布WAVは16 kHz mono float32である。AndroidテストAPK内でのみPCM16へ変換してモデルへ入力したため、この変換を改変として扱う。サンプルWAVはAndroidテストAPKへ一時的に含めるが、本番APKとGitには含めない。この3ファイルだけの結果はモデル精度全体を示すものではなく、同一話者のplain/Lombard変化と別話者の最低限のスモーク試験である。
