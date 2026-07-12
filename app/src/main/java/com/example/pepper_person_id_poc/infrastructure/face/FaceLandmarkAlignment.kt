package com.example.pepper_person_id_poc.infrastructure.face

internal object FaceLandmarkAlignment {
    private const val LANDMARK_OFFSET = 4
    private const val LANDMARK_COUNT = 5
    private const val INPUT_SIZE = 128f
    private val target = floatArrayOf(
        0.31556875f, 0.4615741f,
        0.6826229f, 0.4615741f,
        0.5002625f, 0.6405054f,
        0.34947187f, 0.824692f,
        0.6534365f, 0.824692f,
    ).map { it * INPUT_SIZE }

    /** Returns a row-major 2x3 least-squares similarity transform. */
    fun similarityTransform(detectedFace: FloatArray): DoubleArray {
        require(detectedFace.size >= LANDMARK_OFFSET + LANDMARK_COUNT * 2)
        val sourceX = DoubleArray(LANDMARK_COUNT) { detectedFace[LANDMARK_OFFSET + it * 2].toDouble() }
        val sourceY = DoubleArray(LANDMARK_COUNT) { detectedFace[LANDMARK_OFFSET + it * 2 + 1].toDouble() }
        val targetX = DoubleArray(LANDMARK_COUNT) { target[it * 2].toDouble() }
        val targetY = DoubleArray(LANDMARK_COUNT) { target[it * 2 + 1].toDouble() }
        val sourceMeanX = sourceX.average()
        val sourceMeanY = sourceY.average()
        val targetMeanX = targetX.average()
        val targetMeanY = targetY.average()

        var denominator = 0.0
        var real = 0.0
        var imaginary = 0.0
        repeat(LANDMARK_COUNT) { index ->
            val sx = sourceX[index] - sourceMeanX
            val sy = sourceY[index] - sourceMeanY
            val tx = targetX[index] - targetMeanX
            val ty = targetY[index] - targetMeanY
            denominator += sx * sx + sy * sy
            real += sx * tx + sy * ty
            imaginary += sx * ty - sy * tx
        }
        require(denominator > 0.0) { "Face landmarks are degenerate" }
        val a = real / denominator
        val b = imaginary / denominator
        return doubleArrayOf(
            a, -b, targetMeanX - a * sourceMeanX + b * sourceMeanY,
            b, a, targetMeanY - b * sourceMeanX - a * sourceMeanY,
        )
    }
}
