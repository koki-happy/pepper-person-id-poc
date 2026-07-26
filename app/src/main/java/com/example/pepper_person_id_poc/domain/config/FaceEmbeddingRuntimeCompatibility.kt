package com.example.pepper_person_id_poc.domain.config

object FaceEmbeddingRuntimeCompatibility {
    const val ARM64_ABI = "arm64-v8a"

    fun supportsExactPair(
        model: FaceEmbeddingModelOption,
        runtime: FaceEmbeddingRuntime,
        abi: String,
    ): Boolean {
        val artifact = FaceEmbeddingArtifactResolver.resolve(model, runtime) ?: return false
        return when (runtime) {
            FaceEmbeddingRuntime.OPEN_CV -> artifact.format == FaceModelFormat.ONNX
            FaceEmbeddingRuntime.ONNX_RUNTIME ->
                abi == ARM64_ABI &&
                    artifact in setOf(
                        FaceEmbeddingModelOption.SFACE_2021DEC_FP32,
                        FaceEmbeddingModelOption.FACE_REIDENTIFICATION_RETAIL_0095_ONNX_FP32,
                    )
            FaceEmbeddingRuntime.NCNN -> artifact.format == FaceModelFormat.NCNN
            FaceEmbeddingRuntime.MNN -> artifact.format == FaceModelFormat.MNN
            FaceEmbeddingRuntime.LITERT -> artifact.format == FaceModelFormat.TFLITE
        }
    }
}
