package com.example.pepper_person_id_poc.infrastructure.face

import com.example.pepper_person_id_poc.domain.face.FaceDetectionSnapshot
import com.example.pepper_person_id_poc.infrastructure.camera.CameraFrameProcessor
import java.io.Closeable
import kotlinx.coroutines.flow.StateFlow

interface FaceDetectorPipeline : CameraFrameProcessor, Closeable {
    val snapshot: StateFlow<FaceDetectionSnapshot>
}
