from __future__ import annotations

import hashlib
import json
from pathlib import Path
import time

import cv2
import numpy as np


class FaceInference:
    def __init__(self, detector: Path, model: Path, model_kind: str, detection_threshold: float = 0.8):
        self.detector_path = detector
        self.model_path = model
        self.model_kind = model_kind
        self.detector = cv2.FaceDetectorYN.create(str(detector), "", (320, 320), detection_threshold, 0.3, 5000)
        if model_kind == "sface":
            self.recognizer = cv2.FaceRecognizerSF_create(str(model), "")
            self.net = None
        elif model_kind == "0095":
            self.recognizer = None
            self.net = cv2.dnn.readNetFromONNX(str(model))
        else:
            raise ValueError(f"Unsupported model: {model_kind}")

    def extract(self, image_path: Path) -> dict:
        image = cv2.imread(str(image_path), cv2.IMREAD_COLOR)
        if image is None:
            return {"status": "CORRUPT_IMAGE", "embedding": None}
        self.detector.setInputSize((image.shape[1], image.shape[0]))
        started = time.perf_counter_ns()
        _, faces = self.detector.detect(image)
        detection_ms = (time.perf_counter_ns() - started) / 1_000_000.0
        face_count = 0 if faces is None else len(faces)
        if face_count != 1:
            return {
                "status": "NO_FACE" if face_count == 0 else "MULTIPLE_FACES",
                "face_count": face_count,
                "detection_ms": detection_ms,
                "embedding": None,
            }
        started = time.perf_counter_ns()
        if self.model_kind == "sface":
            aligned = self.recognizer.alignCrop(image, faces[0])
            embedding = self.recognizer.feature(aligned).reshape(-1).astype(np.float32)
        else:
            aligned = _align_0095(image, faces[0])
            blob = cv2.dnn.blobFromImage(aligned, 1.0, (128, 128), (0, 0, 0), False, False, cv2.CV_32F)
            self.net.setInput(blob)
            embedding = self.net.forward().reshape(-1).astype(np.float32)
        embedding_ms = (time.perf_counter_ns() - started) / 1_000_000.0
        norm = float(np.linalg.norm(embedding))
        if not np.isfinite(embedding).all() or norm == 0:
            return {"status": "INVALID_EMBEDDING", "embedding": None}
        return {
            "status": "SUCCESS",
            "face_count": 1,
            "detection_ms": detection_ms,
            "embedding_ms": embedding_ms,
            "embedding": embedding / norm,
        }


class EmbeddingCache:
    def __init__(self, root: Path, model_kind: str, model_path: Path):
        digest = hashlib.sha256(model_path.read_bytes()).hexdigest()[:16]
        self.root = root / f"{model_kind}-{digest}"
        self.root.mkdir(parents=True, exist_ok=True)

    def load(self, sample: dict) -> dict | None:
        path = self._path(sample)
        if not path.exists():
            return None
        metadata = json.loads(path.with_suffix(".json").read_text(encoding="utf-8"))
        embedding = np.load(path) if metadata["status"] == "SUCCESS" else None
        return {**metadata, "embedding": embedding}

    def save(self, sample: dict, result: dict) -> None:
        path = self._path(sample)
        metadata = {key: value for key, value in result.items() if key != "embedding"}
        path.with_suffix(".json").write_text(json.dumps(metadata, sort_keys=True), encoding="utf-8")
        if result.get("embedding") is not None:
            np.save(path, result["embedding"])

    def _path(self, sample: dict) -> Path:
        key = hashlib.sha256(
            f'{sample["image_sha256"]}:{sample["image_path"]}'.encode("utf-8")
        ).hexdigest()
        return self.root / f"{key}.npy"


def cosine(left: np.ndarray, right: np.ndarray) -> float:
    return float(np.dot(left, right) / (np.linalg.norm(left) * np.linalg.norm(right)))


def centroid(embeddings: list[np.ndarray]) -> np.ndarray:
    value = np.mean(np.stack(embeddings), axis=0)
    return value / np.linalg.norm(value)


def _align_0095(image: np.ndarray, face: np.ndarray) -> np.ndarray:
    target = np.array(
        [[0.31556875, 0.4615741071], [0.6826229167, 0.4615741071], [0.5002625, 0.6405053571],
         [0.349471875, 0.8246919643], [0.6534364583, 0.8246919643]], dtype=np.float64
    ) * 128.0
    source = face[4:14].reshape(5, 2).astype(np.float64)
    source_mean, target_mean = source.mean(axis=0), target.mean(axis=0)
    source_centered, target_centered = source - source_mean, target - target_mean
    denominator = np.square(source_centered).sum()
    real = np.sum(source_centered[:, 0] * target_centered[:, 0] + source_centered[:, 1] * target_centered[:, 1])
    imaginary = np.sum(source_centered[:, 0] * target_centered[:, 1] - source_centered[:, 1] * target_centered[:, 0])
    a, b = real / denominator, imaginary / denominator
    transform = np.array([[a, -b, target_mean[0] - a * source_mean[0] + b * source_mean[1]],
                          [b, a, target_mean[1] - b * source_mean[0] - a * source_mean[1]]])
    return cv2.warpAffine(image, transform, (128, 128))
