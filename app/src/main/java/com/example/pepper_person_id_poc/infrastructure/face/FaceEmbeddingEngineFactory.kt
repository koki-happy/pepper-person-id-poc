package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import android.os.Build
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingArtifactResolver
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntimeCompatibility

class UnsupportedModelRuntimePairException(message: String) : IllegalArgumentException(message)

object FaceEmbeddingEngineFactory {
    fun create(
        context: Context,
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
    ): FaceEmbeddingEngine {
        requireExactPair(model, runtime)
        val artifact = requireNotNull(FaceEmbeddingArtifactResolver.resolve(model, runtime))
        return when (runtime) {
            FaceEmbeddingRuntime.OPEN_CV -> if (artifact.isSFace) {
                SFaceEmbeddingEngine(context, artifact)
            } else {
                FaceReidentificationRetail0095EmbeddingEngine(context)
            }
            FaceEmbeddingRuntime.ONNX_RUNTIME -> OnnxRuntimeFaceEmbeddingEngine(context, artifact)
            FaceEmbeddingRuntime.NCNN,
            FaceEmbeddingRuntime.MNN,
            -> NativeFaceEmbeddingEngine(context, artifact, runtime)
            FaceEmbeddingRuntime.LITERT -> when (artifact) {
                FaceEmbeddingModelOption.SFACE_2021DEC_LITERT_FP32 ->
                    LiteRtSFaceEmbeddingEngine(
                        context = context,
                        contract = LiteRtSFaceEmbeddingEngine.CONTRACT,
                    )
                FaceEmbeddingModelOption.FACE_0095_LITERT_FP32 ->
                    LiteRt0095EmbeddingEngine(
                        context = context,
                        contract = LiteRt0095EmbeddingEngine.CONTRACT,
                    )
                else -> error(
                    "Unsupported LiteRT face artifact=${artifact.artifactId}; no fallback",
                )
            }
        }
    }

    fun requireExactPair(
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
    ) = requireExactPair(model, runtime, currentAbi())

    fun requireExactPair(
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
        abi: String,
    ) {
        val supported = FaceEmbeddingRuntimeCompatibility.supportsExactPair(model, runtime, abi)
        if (!supported) {
            throw UnsupportedModelRuntimePairException(
                "Unsupported face embedding pair artifact=${model.artifactId} " +
                    "runtime=${runtime.runtimeId} abi=$abi; no fallback",
            )
        }
    }

    private fun currentAbi(): String =
        Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" }
}
