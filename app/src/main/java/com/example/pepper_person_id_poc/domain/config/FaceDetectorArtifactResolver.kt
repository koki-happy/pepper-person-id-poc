package com.example.pepper_person_id_poc.domain.config

/** Resolves one user-facing detector model to its exact runtime artifact. */
object FaceDetectorArtifactResolver {
    val logicalModels: List<FaceDetectorModelOption> = listOf(
        FaceDetectorModelOption.ML_KIT_BUNDLED,
        FaceDetectorModelOption.YUNET_2026MAY_FP32,
    )

    fun logicalModel(model: FaceDetectorModelOption): FaceDetectorModelOption = when (model) {
        FaceDetectorModelOption.YUNET_2026MAY_LITERT_FP32,
        FaceDetectorModelOption.YUNET_2026MAY_NCNN_FP32,
        FaceDetectorModelOption.YUNET_2026MAY_MNN_FP32,
        -> FaceDetectorModelOption.YUNET_2026MAY_FP32
        else -> model
    }

    fun resolve(
        model: FaceDetectorModelOption,
        runtime: FaceDetectorRuntime,
    ): FaceDetectorModelOption? = when (logicalModel(model) to runtime) {
        FaceDetectorModelOption.ML_KIT_BUNDLED to FaceDetectorRuntime.ML_KIT ->
            FaceDetectorModelOption.ML_KIT_BUNDLED
        FaceDetectorModelOption.YUNET_2026MAY_FP32 to FaceDetectorRuntime.OPEN_CV,
        FaceDetectorModelOption.YUNET_2026MAY_FP32 to FaceDetectorRuntime.ONNX_RUNTIME ->
            FaceDetectorModelOption.YUNET_2026MAY_FP32
        FaceDetectorModelOption.YUNET_2026MAY_FP32 to FaceDetectorRuntime.NCNN ->
            FaceDetectorModelOption.YUNET_2026MAY_NCNN_FP32
        FaceDetectorModelOption.YUNET_2026MAY_FP32 to FaceDetectorRuntime.MNN ->
            FaceDetectorModelOption.YUNET_2026MAY_MNN_FP32
        FaceDetectorModelOption.YUNET_2026MAY_FP32 to FaceDetectorRuntime.LITERT ->
            FaceDetectorModelOption.YUNET_2026MAY_LITERT_FP32
        else -> null
    }
}
