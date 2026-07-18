package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.face.HeadPose
import kotlin.math.atan2
import kotlin.math.sqrt
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import org.opencv.geometry.Geometry

class HeadPoseEstimator {
    fun estimate(landmarks: FloatArray, imageWidth: Int, imageHeight: Int): HeadPose? {
        if (landmarks.size != LANDMARK_VALUE_COUNT || imageWidth <= 0 || imageHeight <= 0) return null
        val imagePoints = MatOfPoint2f(
            Point(landmarks[0].toDouble(), landmarks[1].toDouble()),
            Point(landmarks[2].toDouble(), landmarks[3].toDouble()),
            Point(landmarks[4].toDouble(), landmarks[5].toDouble()),
            Point(landmarks[6].toDouble(), landmarks[7].toDouble()),
            Point(landmarks[8].toDouble(), landmarks[9].toDouble()),
        )
        val objectPoints = MatOfPoint3f(
            Point3(-30.0, 30.0, -30.0),
            Point3(30.0, 30.0, -30.0),
            Point3(0.0, 0.0, 0.0),
            Point3(-25.0, -30.0, -20.0),
            Point3(25.0, -30.0, -20.0),
        )
        val focalLength = imageWidth.toDouble()
        val cameraMatrix = Mat.eye(3, 3, CvType.CV_64F)
        cameraMatrix.put(0, 0, focalLength)
        cameraMatrix.put(1, 1, focalLength)
        cameraMatrix.put(0, 2, imageWidth / 2.0)
        cameraMatrix.put(1, 2, imageHeight / 2.0)
        val distortion = MatOfDouble(0.0, 0.0, 0.0, 0.0)
        val rotationVector = Mat()
        val translationVector = Mat()
        val rotationMatrix = Mat()
        return try {
            val solved = Geometry.solvePnP(
                objectPoints,
                imagePoints,
                cameraMatrix,
                distortion,
                rotationVector,
                translationVector,
                false,
                Geometry.SOLVEPNP_EPNP,
            )
            if (!solved) return null
            Geometry.Rodrigues(rotationVector, rotationMatrix)
            val r00 = rotationMatrix.get(0, 0)[0]
            val r10 = rotationMatrix.get(1, 0)[0]
            val r20 = rotationMatrix.get(2, 0)[0]
            val r21 = rotationMatrix.get(2, 1)[0]
            val r22 = rotationMatrix.get(2, 2)[0]
            val r11 = rotationMatrix.get(1, 1)[0]
            val r12 = rotationMatrix.get(1, 2)[0]
            val sy = sqrt(r00 * r00 + r10 * r10)
            val pitchRadians: Double
            val yawRadians: Double
            val rollRadians: Double
            if (sy > 1e-6) {
                pitchRadians = atan2(r21, r22)
                yawRadians = atan2(-r20, sy)
                rollRadians = atan2(r10, r00)
            } else {
                pitchRadians = atan2(-r12, r11)
                yawRadians = atan2(-r20, sy)
                rollRadians = 0.0
            }
            HeadPose(
                yawDegrees = Math.toDegrees(yawRadians).toFloat(),
                pitchDegrees = Math.toDegrees(pitchRadians).toFloat().foldToFrontalRange(),
                rollDegrees = Math.toDegrees(rollRadians).toFloat(),
            )
        } finally {
            rotationMatrix.release()
            translationVector.release()
            rotationVector.release()
            distortion.release()
            cameraMatrix.release()
            objectPoints.release()
            imagePoints.release()
        }
    }

    private companion object {
        const val LANDMARK_VALUE_COUNT = 10
    }
}

private fun Float.foldToFrontalRange(): Float = when {
    this > 90f -> this - 180f
    this < -90f -> this + 180f
    else -> this
}
