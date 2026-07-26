package com.example.pepper_person_id_poc.domain.config

/**
 * Keeps the user-facing face model independent from the runtime-specific artifact.
 *
 * Existing runtime-specific enum values remain readable for settings migration, but
 * new selections should use [logicalModels] and resolve the exact packaged artifact
 * through [resolve].
 */
object FaceEmbeddingArtifactResolver {
    val logicalModels: List<FaceEmbeddingModelOption> = listOf(
        FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
        FaceEmbeddingModelOption.SFACE_2021DEC_INT8,
        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32,
    )

    fun logicalModel(model: FaceEmbeddingModelOption): FaceEmbeddingModelOption = when (model) {
        FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32,
        FaceEmbeddingModelOption.SFACE_2021DEC_MNN_FP32,
        FaceEmbeddingModelOption.SFACE_2021DEC_LITERT_FP32,
        -> FaceEmbeddingModelOption.SFACE_2021DEC_FP32
        FaceEmbeddingModelOption.FACE_0095_NCNN_FP32,
        FaceEmbeddingModelOption.FACE_0095_MNN_FP32,
        FaceEmbeddingModelOption.FACE_0095_LITERT_FP32,
        -> FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32
        else -> model
    }

    fun resolve(
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
    ): FaceEmbeddingModelOption? = when (logicalModel(model) to runtime) {
        FaceEmbeddingModelOption.SFACE_2021DEC_FP32 to FaceEmbeddingRuntime.OPEN_CV,
        FaceEmbeddingModelOption.SFACE_2021DEC_FP32 to FaceEmbeddingRuntime.ONNX_RUNTIME ->
            FaceEmbeddingModelOption.SFACE_2021DEC_FP32
        FaceEmbeddingModelOption.SFACE_2021DEC_FP32 to FaceEmbeddingRuntime.NCNN ->
            FaceEmbeddingModelOption.SFACE_2021DEC_NCNN_FP32
        FaceEmbeddingModelOption.SFACE_2021DEC_FP32 to FaceEmbeddingRuntime.MNN ->
            FaceEmbeddingModelOption.SFACE_2021DEC_MNN_FP32
        FaceEmbeddingModelOption.SFACE_2021DEC_FP32 to FaceEmbeddingRuntime.LITERT ->
            FaceEmbeddingModelOption.SFACE_2021DEC_LITERT_FP32
        FaceEmbeddingModelOption.SFACE_2021DEC_INT8 to FaceEmbeddingRuntime.OPEN_CV ->
            FaceEmbeddingModelOption.SFACE_2021DEC_INT8
        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32 to FaceEmbeddingRuntime.OPEN_CV,
        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32 to FaceEmbeddingRuntime.ONNX_RUNTIME ->
            FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32
        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32 to FaceEmbeddingRuntime.NCNN ->
            FaceEmbeddingModelOption.FACE_0095_NCNN_FP32
        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32 to FaceEmbeddingRuntime.MNN ->
            FaceEmbeddingModelOption.FACE_0095_MNN_FP32
        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32 to FaceEmbeddingRuntime.LITERT ->
            FaceEmbeddingModelOption.FACE_0095_LITERT_FP32
        else -> null
    }
}
