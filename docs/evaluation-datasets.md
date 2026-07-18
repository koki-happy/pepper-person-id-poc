# Evaluation datasets

評価データ本体、展開ファイル、抽出フレーム、変換音声はGitへコミットしない。ローカルの`datasets/`以下へ保存する。

## Pointing'04（2026-07-18取得・検証）

- Official article: https://figshare.com/articles/dataset/Pointing04_DB/5142466/2
- Version: 2
- License: CC BY 4.0
- Citation: Gourier, Hall, Crowley, “Estimating Face Orientation from Robust Detection of Salient
  Facial Features,” Pointing 2004
- Official archives: 31 files, 28,282,725 bytes; every supplied size and MD5 verified
- Archive manifest SHA-256: `82a8ec3569d4bc276074a448fd95d5ff7e8de383220d24ac9161a1cf2ec5db12`
- Canonical evaluation body: 15 subjects × 2 series × 93 poses = 2,790 JPEG files
- Image verification: all 2,790 images decoded at 384×288; zero duplicate subject/series/pose keys
- Labels: 9 pitch values and 13 yaw values encoded in official filenames
- Local paths: `datasets/pointing04/archives/` and `datasets/pointing04/extracted/`
- User-supplied Figshare bundle: `5142466.zip`, 28,287,325 bytes, SHA-256
  `70876d36f9a6c3bd2af5c66b587c06db36e977c1acaec593fb47cb3a304b9c95`; all 31 embedded
  archive names, sizes, and MD5 values match the already verified official archive set

The separate `deFace` convenience directory contains 30 frontal files and is not part of the canonical
2,790 trials. Generated manifests under `config/face-evaluation/` use subjects 01–09 as enrolled,
10–12 as development Unknown, and 13–15 as final Unknown. Series 1 supplies enrollment/development
registered probes and series 2 supplies final registered probes.

## BIWI Kinect Head Pose（2026-07-18配布障害）

- Dataset page: https://huggingface.co/datasets/ETHZurich/biwi_kinect_head_pose
- Official archive URL: https://data.vision.ee.ethz.ch/cvl/gfanelli/kinect_head_pose_db.tgz
- License: non-commercial university research and education
- Expected archive size: 6,014,398,431 bytes
- Expected SHA-256: `d8fc0fee11b6b865b18b292de7c21dd2181492bd770c4fe13821e8dc630f5549`
- Expected body: 24 sequences, 20 subjects, over 15,000 RGB/depth frames and pose matrices

The Hugging Face repository contains a loader rather than the body, and the loader's ETH Zurich URL
currently returns HTTP 403. Hugging Face issue #3822 also records the unavailable source in April 2025.
The HyperAI torrent mirror contains a complete `db_annotations.zip` with 15,677 binary pose labels
across sequences 01–24. Its 6 GB `faces_0.zip`, however, is only an incomplete sparse aria2 target:
the logical size is preallocated, an `.aria2` control file remains, and RGB entries cannot be
enumerated. The mirror is also not proven byte-identical to the official ETH archive or covered by
verified redistribution terms. BIWI MUST remain `BLOCKED_UPSTREAM` until usable RGB images and their
provenance/permission are verified; annotation-only or incomplete mirrors must not be reported as full
BIWI evidence.

The supplied `biwi_kinect_head_pose.py` is a 7,234-byte Hugging Face loader, not the dataset body.
Its `_URLS` entry points to the same unavailable ETH archive. Internet Archive availability,
Arquivo.pt, Common Crawl, and Hugging Face-hosted files did not provide a publicly retrievable copy
whose origin, usage terms, byte count, and SHA-256 could all be verified. The next legitimate step is
to ask the ETH dataset owner to restore or reissue the archive and confirm that this company PoC is
permitted by the non-commercial research/education terms.

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
- Do not package Lombard GRID media into the production APK. Ignored benchmark media may be packaged only into the local androidTest APK.
- Keep only attribution, split manifests, aggregate metrics, and non-biometric benchmark metadata in version control.
- Mark every resampling, frame extraction, crop, normalization, or other transformation as a modification in reports derived from the corpus.

### Pepper smoke benchmark samples

2026-07-12のモデル配線・速度確認では、公式ページ掲載の次のサンプルだけを使用した。

- `s22_p_sgbe5s.wav`: talker s22, plain
- `s22_l_sgbe5s.wav`: talker s22, Lombard
- `s16_p_bgah2n.wav`: talker s16, plain
- 同名のfront-view `.mov` 3本: 顔識別用

配布WAVは16 kHz mono float32である。AndroidテストAPK内でのみPCM16へ変換してモデルへ入力したため、この変換を改変として扱う。動画は`ffmpeg -ss 1.0`で正面フレームを1枚抽出する。サンプルWAV、動画、抽出PNGはAndroidテストAPKへ一時的に含めるが、本番APKとGitには含めない。この3組だけの結果はモデル精度全体を示すものではなく、同一人物のplain/Lombard変化と別人物の最低限のスモーク試験である。

話者モデルの言語差確認には、sherpa-onnx公式配布の`fangjun-sr-1.wav`、`fangjun-test-sr-1.wav`、`leijun-test-sr-1.wav`も使用する。前2本を同一話者、最後を別話者とし、配布WAVは無改変でandroidTest APKだけに含める。取得URLとSHA-256は`model-provenance.md`および`setup-local-inference-assets.ps1`を正本とする。

上記は3サンプルずつのスモーク試験である。前節の複数人物・複数セッション評価セット、manifest作成、FAR/FRR測定は未実施であり、本番精度や閾値確定の根拠にはしない。
