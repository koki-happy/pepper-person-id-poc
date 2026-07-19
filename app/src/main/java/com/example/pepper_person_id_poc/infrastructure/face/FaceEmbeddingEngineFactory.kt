package com.example.pepper_person_id_poc.infrastructure.face

import android.content.Context
import com.example.pepper_person_id_poc.domain.config.FaceInferenceBackend
import com.example.pepper_person_id_poc.domain.config.FaceModelOption

object FaceEmbeddingEngineFactory {
    fun create(
        context: Context,
        model: FaceModelOption,
        backend: FaceInferenceBackend,
    ): FaceEmbeddingEngine {
        return when (backend) {
            FaceInferenceBackend.OPEN_CV -> if (model.isSFace) {
                SFaceEmbeddingEngine(context, model)
            } else {
                FaceReidentificationRetail0095EmbeddingEngine(context)
            }
            FaceInferenceBackend.ONNX_RUNTIME -> OnnxRuntimeFaceEmbeddingEngine(context, model)
            FaceInferenceBackend.NCNN -> NativeFaceEmbeddingEngine(context, model, backend)
            FaceInferenceBackend.MNN -> NativeFaceEmbeddingEngine(context, model, backend)
        }
    }
}
