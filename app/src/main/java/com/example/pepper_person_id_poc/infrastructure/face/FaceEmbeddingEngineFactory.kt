package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingRuntime
import com.example.pepper_person_id_poc.domain.config.FaceModelFormat

class UnsupportedModelRuntimePairException(message: String) : IllegalArgumentException(message)

object FaceEmbeddingEngineFactory {
    fun create(
        context: Context,
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
    ): FaceEmbeddingEngine {
        requireExactPair(model, runtime)
        return when (runtime) {
            FaceEmbeddingRuntime.OPEN_CV -> if (model.isSFace) {
                SFaceEmbeddingEngine(context, model)
            } else {
                FaceReidentificationRetail0095EmbeddingEngine(context)
            }
            FaceEmbeddingRuntime.ONNX_RUNTIME -> OnnxRuntimeFaceEmbeddingEngine(context, model)
            FaceEmbeddingRuntime.NCNN,
            FaceEmbeddingRuntime.MNN,
            -> NativeFaceEmbeddingEngine(context, model, runtime)
        }
    }

    fun requireExactPair(
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
    ) {
        val supported = when (runtime) {
            FaceEmbeddingRuntime.OPEN_CV -> model.format == FaceModelFormat.ONNX
            FaceEmbeddingRuntime.ONNX_RUNTIME -> false
            FaceEmbeddingRuntime.NCNN -> model.format == FaceModelFormat.NCNN
            FaceEmbeddingRuntime.MNN -> model.format == FaceModelFormat.MNN
        }
        if (!supported) {
            throw UnsupportedModelRuntimePairException(
                "Unsupported face embedding pair artifact=${model.artifactId} runtime=${runtime.runtimeId}; no fallback",
            )
        }
    }
}
