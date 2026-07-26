package com.example.pepper_person_id_poc.infrastructure.face

import kotlin.math.max
import kotlin.math.min
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

data class FacePixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class FaceImageQualityMetrics(
    val blurScore: Float,
    val brightnessMean: Float,
    val clippedRatio: Float,
    val edgeTruncationRatio: Float,
)

class FaceImageQualityAnalyzer(
    private val darkClippingValue: Int = 5,
    private val brightClippingValue: Int = 250,
) {
    init {
        require(darkClippingValue in 0..255)
        require(brightClippingValue in 0..255)
        require(darkClippingValue < brightClippingValue)
    }

    fun analyzeBgr(
        bgr: Mat,
        faceBounds: FacePixelBounds,
    ): FaceImageQualityMetrics {
        require(!bgr.empty())
        val gray = Mat()
        return try {
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            val luma = ByteArray(gray.rows() * gray.cols())
            gray.get(0, 0, luma)
            analyze(
                luma = luma,
                imageWidth = gray.cols(),
                imageHeight = gray.rows(),
                rowStride = gray.cols(),
                faceBounds = faceBounds,
            )
        } finally {
            gray.release()
        }
    }

    fun analyze(
        luma: ByteArray,
        imageWidth: Int,
        imageHeight: Int,
        rowStride: Int,
        faceBounds: FacePixelBounds,
    ): FaceImageQualityMetrics {
        require(imageWidth > 0 && imageHeight > 0)
        require(rowStride >= imageWidth)
        require(luma.size >= rowStride * imageHeight)
        require(faceBounds.width > 0 && faceBounds.height > 0)

        val visibleLeft = max(0, faceBounds.left)
        val visibleTop = max(0, faceBounds.top)
        val visibleRight = min(imageWidth, faceBounds.right)
        val visibleBottom = min(imageHeight, faceBounds.bottom)
        val requestedArea = faceBounds.width.toLong() * faceBounds.height.toLong()
        val visibleWidth = max(0, visibleRight - visibleLeft)
        val visibleHeight = max(0, visibleBottom - visibleTop)
        val visibleArea = visibleWidth.toLong() * visibleHeight.toLong()
        val edgeTruncationRatio = (1.0 - visibleArea.toDouble() / requestedArea.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
        if (visibleArea == 0L) {
            return FaceImageQualityMetrics(
                blurScore = 0f,
                brightnessMean = 0f,
                clippedRatio = 1f,
                edgeTruncationRatio = edgeTruncationRatio,
            )
        }

        var brightnessSum = 0L
        var clippedCount = 0L
        for (y in visibleTop until visibleBottom) {
            val rowOffset = y * rowStride
            for (x in visibleLeft until visibleRight) {
                val value = luma[rowOffset + x].toInt() and 0xff
                brightnessSum += value
                if (value <= darkClippingValue || value >= brightClippingValue) {
                    clippedCount += 1
                }
            }
        }

        var laplacianCount = 0L
        var laplacianSum = 0.0
        var laplacianSquaredSum = 0.0
        for (y in max(visibleTop + 1, 1) until min(visibleBottom - 1, imageHeight - 1)) {
            val rowOffset = y * rowStride
            for (x in max(visibleLeft + 1, 1) until min(visibleRight - 1, imageWidth - 1)) {
                val center = luma[rowOffset + x].toInt() and 0xff
                val laplacian =
                    (luma[rowOffset + x - 1].toInt() and 0xff) +
                        (luma[rowOffset + x + 1].toInt() and 0xff) +
                        (luma[rowOffset - rowStride + x].toInt() and 0xff) +
                        (luma[rowOffset + rowStride + x].toInt() and 0xff) -
                        4 * center
                laplacianCount += 1
                laplacianSum += laplacian
                laplacianSquaredSum += laplacian.toDouble() * laplacian.toDouble()
            }
        }
        val blurScore = if (laplacianCount == 0L) {
            0f
        } else {
            val mean = laplacianSum / laplacianCount
            (laplacianSquaredSum / laplacianCount - mean * mean)
                .coerceAtLeast(0.0)
                .toFloat()
        }
        return FaceImageQualityMetrics(
            blurScore = blurScore,
            brightnessMean = (brightnessSum.toDouble() / visibleArea).toFloat(),
            clippedRatio = (clippedCount.toDouble() / visibleArea).toFloat(),
            edgeTruncationRatio = edgeTruncationRatio,
        )
    }
}
