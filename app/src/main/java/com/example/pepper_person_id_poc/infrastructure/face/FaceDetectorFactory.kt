package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.benchmark.BenchmarkEvent
import com.example.pepper_person_id_poc.domain.config.FaceDetectorArtifactResolver
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
        val artifact = requireNotNull(FaceDetectorArtifactResolver.resolve(model, runtime))
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
                detectorOption = artifact,
                embeddingEngine = embeddingEngine,
                analysisIntervalMillis = analysisIntervalMillis,
                estimateHeadPose = estimateHeadPose,
                onPoseObservations = onPoseObservations,
                onFeatureObservations = onFeatureObservations,
                onEmbeddingReady = onEmbeddingReady,
                onEmbeddingError = onEmbeddingError,
                onBenchmarkEvent = onBenchmarkEvent,
            )
            FaceDetectorRuntime.ONNX_RUNTIME -> YuNetFaceDetector(
                context = context,
                detectorOption = artifact,
                embeddingEngine = embeddingEngine,
                analysisIntervalMillis = analysisIntervalMillis,
                estimateHeadPose = estimateHeadPose,
                onPoseObservations = onPoseObservations,
                onFeatureObservations = onFeatureObservations,
                onEmbeddingReady = onEmbeddingReady,
                onEmbeddingError = onEmbeddingError,
                onBenchmarkEvent = onBenchmarkEvent,
                detectionBackendProvider = { _, _ ->
                    OnnxRuntimeYuNetFaceDetector(context, artifact)
                },
            )
            FaceDetectorRuntime.NCNN,
            FaceDetectorRuntime.MNN,
            -> YuNetFaceDetector(
                context = context,
                detectorOption = FaceDetectorArtifactResolver.logicalModel(artifact),
                embeddingEngine = embeddingEngine,
                analysisIntervalMillis = analysisIntervalMillis,
                estimateHeadPose = estimateHeadPose,
                onPoseObservations = onPoseObservations,
                onFeatureObservations = onFeatureObservations,
                onEmbeddingReady = onEmbeddingReady,
                onEmbeddingError = onEmbeddingError,
                onBenchmarkEvent = onBenchmarkEvent,
                detectionBackendProvider = { _, _ ->
                    YuNetNativeFaceDetector(
                        context,
                        if (runtime == FaceDetectorRuntime.NCNN) {
                            YuNetNativeRuntime.NCNN
                        } else {
                            YuNetNativeRuntime.MNN
                        },
                    )
                },
            )
            FaceDetectorRuntime.LITERT -> YuNetFaceDetector(
                context = context,
                detectorOption = FaceDetectorArtifactResolver.logicalModel(artifact),
                embeddingEngine = embeddingEngine,
                analysisIntervalMillis = analysisIntervalMillis,
                estimateHeadPose = estimateHeadPose,
                onPoseObservations = onPoseObservations,
                onFeatureObservations = onFeatureObservations,
                onEmbeddingReady = onEmbeddingReady,
                onEmbeddingError = onEmbeddingError,
                onBenchmarkEvent = onBenchmarkEvent,
                detectionBackendProvider = { _, _ -> LiteRtYuNetFaceDetector(context) },
            )
        }
    }

    fun requireExactPair(
        model: FaceDetectorModelOption,
        runtime: FaceDetectorRuntime,
    ) {
        val supported = FaceDetectorArtifactResolver.resolve(model, runtime) != null
        if (!supported) {
            throw UnsupportedModelRuntimePairException(
                "Unsupported face detector pair artifact=${model.artifactId} runtime=${runtime.runtimeId}; no fallback",
            )
        }
    }
}
