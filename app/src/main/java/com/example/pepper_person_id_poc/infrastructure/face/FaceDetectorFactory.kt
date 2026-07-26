package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.config.FaceDetectorModelOption
import com.example.pepper_person_id_poc.domain.config.FaceDetectorRuntime
import com.example.pepper_person_id_poc.domain.face.FacePoseObservation

object FaceDetectorFactory {
    fun create(
        context: Context,
        model: FaceDetectorModelOption,
        runtime: FaceDetectorRuntime,
        embeddingEngine: FaceEmbeddingEngine,
        analysisIntervalMillis: Long,
        estimateHeadPose: Boolean,
        onPoseObservations: (Int, List<FacePoseObservation>) -> Set<String>,
        onFeatureObservations: (List<FaceFeatureObservation>) -> Unit,
        onEmbeddingReady: () -> Unit,
        onEmbeddingError: (Throwable) -> Unit,
        onBenchmarkEvent: (BenchmarkEvent) -> Unit,
    ): FaceDetectorPipeline {
        requireExactPair(model, runtime)
        return when (runtime) {
            FaceDetectorRuntime.ML_KIT -> MlKitFaceDetector(
                context = context,
                embeddingEngine = embeddingEngine,
                analysisIntervalMillis = analysisIntervalMillis,
                estimateHeadPose = estimateHeadPose,
                onPoseObservations = onPoseObservations,
                onFeatureObservations = onFeatureObservations,
                onEmbeddingReady = onEmbeddingReady,
                onEmbeddingError = onEmbeddingError,
                onBenchmarkEvent = onBenchmarkEvent,
            )
            FaceDetectorRuntime.OPEN_CV -> YuNetFaceDetector(
                context = context,
                detectorOption = model,
                embeddingEngine = embeddingEngine,
                analysisIntervalMillis = analysisIntervalMillis,
                estimateHeadPose = estimateHeadPose,
                onPoseObservations = onPoseObservations,
                onFeatureObservations = onFeatureObservations,
                onEmbeddingReady = onEmbeddingReady,
                onEmbeddingError = onEmbeddingError,
                onBenchmarkEvent = onBenchmarkEvent,
            )
        }
    }

    fun requireExactPair(
        model: FaceDetectorModelOption,
        runtime: FaceDetectorRuntime,
    ) {
        val supported = when (runtime) {
            FaceDetectorRuntime.ML_KIT -> model == FaceDetectorModelOption.ML_KIT_BUNDLED
            FaceDetectorRuntime.OPEN_CV -> model != FaceDetectorModelOption.ML_KIT_BUNDLED
        }
        if (!supported) {
            throw UnsupportedModelRuntimePairException(
                "Unsupported face detector pair artifact=${model.artifactId} runtime=${runtime.runtimeId}; no fallback",
            )
        }
    }
}
