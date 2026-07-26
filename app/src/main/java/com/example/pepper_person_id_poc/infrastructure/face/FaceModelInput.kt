package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.config.FaceEmbeddingModelOption
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

internal data class FaceModelInput(
    val values: FloatArray,
    val shape: LongArray,
    val width: Int,
    val height: Int,
    val channels: Int = 3,
)

internal fun prepareFaceModelInput(
    model: FaceEmbeddingModelOption,
    imageBgr: Mat,
    detectedFace: Mat,
): FaceModelInput {
    val faceValues = FloatArray(detectedFace.cols())
    detectedFace.get(0, 0, faceValues)
    require(faceValues.size >= 14) { "Face detector did not provide the required five landmarks" }
    val size = Size(model.inputSize.toDouble(), model.inputSize.toDouble())
    val transform = Mat(2, 3, CvType.CV_64F)
    val aligned = Mat()
    val floatImage = Mat()
    try {
        transform.put(0, 0, *FaceLandmarkAlignment.similarityTransform(faceValues))
        Imgproc.warpAffine(imageBgr, aligned, transform, size)
        val scale = if (model.isSFace) 1.0 / 128.0 else 1.0
        val shift = if (model.isSFace) -127.5 / 128.0 else 0.0
        aligned.convertTo(floatImage, CvType.CV_32FC3, scale, shift)
        val interleaved = FloatArray((floatImage.total() * floatImage.channels()).toInt())
        floatImage.get(0, 0, interleaved)
        val width = size.width.toInt()
        val height = size.height.toInt()
        val plane = width * height
        val nchw = FloatArray(interleaved.size)
        repeat(height) { y ->
            repeat(width) { x ->
                val source = (y * width + x) * 3
                val pixel = y * width + x
                if (model.isSFace) {
                    // SFace uses RGB input after the same normalization as FaceRecognizerSF.
                    nchw[pixel] = interleaved[source + 2]
                    nchw[plane + pixel] = interleaved[source + 1]
                    nchw[plane * 2 + pixel] = interleaved[source]
                } else {
                    // Converted 0095 keeps its BGR-to-RGB and /255 preprocessing in the graph.
                    nchw[pixel] = interleaved[source]
                    nchw[plane + pixel] = interleaved[source + 1]
                    nchw[plane * 2 + pixel] = interleaved[source + 2]
                }
            }
        }
        return FaceModelInput(nchw, longArrayOf(1, 3, height.toLong(), width.toLong()), width, height)
    } finally {
        floatImage.release()
        aligned.release()
        transform.release()
    }
}
