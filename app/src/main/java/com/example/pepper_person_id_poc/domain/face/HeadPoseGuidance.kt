package com.example.pepper_person_id_poc.domain.face

data class HeadPose(
    val yawDegrees: Float,
    val pitchDegrees: Float,
    val rollDegrees: Float,
) {
    init {
        require(yawDegrees.isFinite() && pitchDegrees.isFinite() && rollDegrees.isFinite())
    }
}

data class FacePoseObservation(
    val trackId: String,
    val headPose: HeadPose?,
)
