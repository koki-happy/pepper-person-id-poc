from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
import re

import numpy as np

POINTING_RE = re.compile(
    r"personne(?P<subject>\d{2})(?P<series>[12])(?P<number>\d{2})"
    r"(?P<pitch>[+-]\d+)(?P<yaw>[+-]\d+)\.jpg$",
    re.IGNORECASE,
)

BIWI_SUBJECTS = {
    "01": "F01", "02": "F02", "03": "F03", "04": "F04", "05": "F05", "06": "F06",
    "07": "M01", "08": "M02", "09": "M03", "10": "M04", "11": "M05", "12": "M06",
    "13": "M07", "14": "M08", "15": "F03", "16": "M09", "17": "M10", "18": "F05",
    "19": "M11", "20": "M12", "21": "F02", "22": "M01", "23": "M13", "24": "M14",
}


def generate_pointing04(root: Path, protocol: str) -> dict:
    if protocol not in {"front", "multi"}:
        raise ValueError("protocol must be front or multi")
    samples = []
    for subject_dir in sorted(root.glob("Personne[0-9][0-9]")):
        for image in sorted(subject_dir.glob("*.jpg")):
            match = POINTING_RE.fullmatch(image.name)
            if not match:
                raise ValueError(f"Unexpected Pointing04 filename: {image}")
            values = match.groupdict()
            subject = values["subject"]
            series = values["series"]
            yaw, pitch = int(values["yaw"]), int(values["pitch"])
            role = _pointing_role(subject, series, yaw, pitch, protocol)
            samples.append(
                {
                    "dataset": "pointing04",
                    "subject_id": subject,
                    "series_id": series,
                    "sample_id": f'{subject}-{series}-{values["number"]}',
                    "image_path": str(image.resolve()),
                    "image_sha256": _sha256(image),
                    "yaw_degrees": yaw,
                    "pitch_degrees": pitch,
                    "roll_degrees": None,
                    "role": role,
                    "yaw_band": _band(yaw),
                    "pitch_band": _band(pitch),
                }
            )
    if len(samples) != 2790:
        raise ValueError(f"Expected 2790 Pointing04 samples, found {len(samples)}")
    manifest = {
        "schema_version": 1,
        "dataset": "pointing04",
        "protocol": protocol,
        "source": "https://figshare.com/articles/dataset/Pointing04_DB/5142466/2",
        "license": "CC BY 4.0",
        "seed": 2004,
        "enrolled_subjects": [f"{value:02d}" for value in range(1, 10)],
        "development_unknown_subjects": ["10", "11", "12"],
        "final_unknown_subjects": ["13", "14", "15"],
        "samples": samples,
    }
    validate_manifest(manifest)
    return manifest


def generate_biwi(root: Path, protocol: str) -> dict:
    if protocol not in {"front", "multi"}:
        raise ValueError("protocol must be front or multi")
    hpdb = root / "hpdb" if (root / "hpdb").is_dir() else root
    raw = []
    for sequence, subject in BIWI_SUBJECTS.items():
        sequence_dir = hpdb / sequence
        if not sequence_dir.is_dir():
            raise FileNotFoundError(f"Missing BIWI sequence {sequence_dir}")
        for index, image in enumerate(sorted(sequence_dir.glob("frame_*_rgb.png"))):
            pose_path = image.with_name(image.name.replace("_rgb.png", "_pose.txt"))
            if not pose_path.is_file():
                raise FileNotFoundError(pose_path)
            yaw, pitch, roll = _biwi_euler(pose_path)
            raw.append(
                {
                    "dataset": "biwi",
                    "subject_id": subject,
                    "series_id": sequence,
                    "frame_index": index,
                    "sample_id": f"{sequence}-{image.stem.removesuffix('_rgb')}",
                    "image_path": str(image.resolve()),
                    "image_sha256": _sha256(image),
                    "yaw_degrees": yaw,
                    "pitch_degrees": pitch,
                    "roll_degrees": roll,
                    "yaw_band": _band(yaw),
                    "pitch_band": _band(pitch),
                }
            )
    enrolled = ["F01", "F02", "F03", "F04", "F05", "F06", "M01", "M02", "M03", "M04", "M05", "M06"]
    dev_unknown = ["M07", "M08", "M09", "M10"]
    final_unknown = ["M11", "M12", "M13", "M14"]
    sequences_by_subject = {
        subject: sorted({row["series_id"] for row in raw if row["subject_id"] == subject})
        for subject in enrolled
    }
    enrollment_ids = set()
    targets = [(0.0, 0.0)] if protocol == "front" else [(0.0, 0.0), (-25.0, 0.0), (25.0, 0.0)]
    for subject in enrolled:
        primary = sequences_by_subject[subject][0]
        candidates = [row for row in raw if row["subject_id"] == subject and row["series_id"] == primary]
        remaining = candidates[:]
        for target_yaw, target_pitch in targets:
            selected = min(
                remaining,
                key=lambda row: (row["yaw_degrees"] - target_yaw) ** 2 + (row["pitch_degrees"] - target_pitch) ** 2,
            )
            enrollment_ids.add(selected["sample_id"])
            remaining.remove(selected)
    samples = []
    for row in raw:
        subject = row["subject_id"]
        if subject in dev_unknown:
            role = "development_unknown"
        elif subject in final_unknown:
            role = "final_unknown"
        elif row["sample_id"] in enrollment_ids:
            role = "enrollment"
        else:
            sequences = sequences_by_subject[subject]
            if len(sequences) > 1:
                role = "development_registered" if row["series_id"] == sequences[0] else "final_registered"
            else:
                role = "development_registered" if row["frame_index"] % 2 == 0 else "final_registered"
        samples.append({**{k: v for k, v in row.items() if k != "frame_index"}, "role": role})
    manifest = {
        "schema_version": 1,
        "dataset": "biwi",
        "protocol": protocol,
        "source": "https://data.vision.ee.ethz.ch/cvl/gfanelli/kinect_head_pose_db.tgz",
        "license": "Non-commercial university research and education",
        "official_archive_bytes": 6014398431,
        "official_archive_sha256": "d8fc0fee11b6b865b18b292de7c21dd2181492bd770c4fe13821e8dc630f5549",
        "seed": 2013,
        "enrolled_subjects": enrolled,
        "development_unknown_subjects": dev_unknown,
        "final_unknown_subjects": final_unknown,
        "samples": samples,
    }
    validate_manifest(manifest)
    return manifest


def validate_manifest(manifest: dict) -> None:
    samples = manifest.get("samples", [])
    if not samples:
        raise ValueError("Manifest has no samples")
    keys = [(row["dataset"], row["sample_id"]) for row in samples]
    if len(keys) != len(set(keys)):
        raise ValueError("Duplicate sample IDs")
    paths = [row["image_path"] for row in samples]
    if len(paths) != len(set(paths)):
        raise ValueError("A sample path occurs more than once")
    enrolled = set(manifest["enrolled_subjects"])
    dev_unknown = set(manifest["development_unknown_subjects"])
    final_unknown = set(manifest["final_unknown_subjects"])
    if enrolled & dev_unknown or enrolled & final_unknown or dev_unknown & final_unknown:
        raise ValueError("Subject leakage between enrolled/development-unknown/final-unknown")
    allowed_roles = {
        "enrollment", "development_registered", "development_unknown",
        "final_registered", "final_unknown",
    }
    if any(row["role"] not in allowed_roles for row in samples):
        raise ValueError("Invalid sample role")
    for row in samples:
        subject = row["subject_id"]
        role = row["role"]
        if role.endswith("unknown"):
            expected = dev_unknown if role.startswith("development") else final_unknown
            if subject not in expected:
                raise ValueError(f"Unknown split leakage: {row['sample_id']}")
        elif subject not in enrolled:
            raise ValueError(f"Registered split leakage: {row['sample_id']}")
        if not Path(row["image_path"]).is_file():
            raise FileNotFoundError(row["image_path"])


def write_manifest(manifest: dict, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def _pointing_role(subject: str, series: str, yaw: int, pitch: int, protocol: str) -> str:
    number = int(subject)
    if number <= 9:
        enrollment_angles = {(0, 0)} if protocol == "front" else {(0, 0), (-30, 0), (30, 0)}
        if series == "1" and (yaw, pitch) in enrollment_angles:
            return "enrollment"
        return "development_registered" if series == "1" else "final_registered"
    if number <= 12:
        return "development_unknown"
    return "final_unknown"


def _band(value: float) -> str:
    absolute = abs(value)
    if absolute <= 7.5:
        return "0"
    if absolute <= 22.5:
        return "15"
    if absolute <= 37.5:
        return "30"
    if absolute <= 52.5:
        return "45"
    if absolute <= 67.5:
        return "60"
    if absolute <= 82.5:
        return "75"
    return "90"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _biwi_euler(path: Path) -> tuple[float, float, float]:
    lines = path.read_text(encoding="utf-8").splitlines()
    rotation = np.array([[float(value) for value in line.split()] for line in lines[:3]], dtype=np.float64)
    if rotation.shape != (3, 3) or not np.isfinite(rotation).all():
        raise ValueError(f"Invalid BIWI rotation matrix: {path}")
    sy = math.sqrt(rotation[0, 0] ** 2 + rotation[1, 0] ** 2)
    if sy >= 1e-6:
        pitch = math.atan2(rotation[2, 1], rotation[2, 2])
        yaw = math.atan2(-rotation[2, 0], sy)
        roll = math.atan2(rotation[1, 0], rotation[0, 0])
    else:
        pitch = math.atan2(-rotation[1, 2], rotation[1, 1])
        yaw = math.atan2(-rotation[2, 0], sy)
        roll = 0.0
    return tuple(math.degrees(value) for value in (yaw, pitch, roll))
