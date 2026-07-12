package com.example.pepper_person_id_poc.infrastructure.camera

import androidx.camera.core.ImageProxy

fun interface CameraFrameProcessor {
    /** The controller owns and closes [image]; implementations must not close it. */
    fun process(image: ImageProxy)

    companion object {
        val NoOp = CameraFrameProcessor { }
    }
}
