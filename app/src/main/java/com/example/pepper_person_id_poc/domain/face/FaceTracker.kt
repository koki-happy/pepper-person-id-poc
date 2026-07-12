package com.example.pepper_person_id_poc.domain.face

class FaceTracker(
    private val minimumIntersectionOverUnion: Float = 0.30f,
    private val maximumMissedFrames: Int = 4,
) {
    private var nextTrackNumber = 1
    private var tracks = emptyList<Track>()

    fun update(detections: List<NormalizedBoundingBox>): List<TrackedBoundingBox> {
        val availableTracks = tracks.toMutableList()
        val assignedTrackIds = mutableSetOf<String>()
        val results = detections.map { detection ->
            val match = availableTracks
                .filterNot { it.trackId in assignedTrackIds }
                .map { it to it.boundingBox.intersectionOverUnion(detection) }
                .filter { (_, score) -> score >= minimumIntersectionOverUnion }
                .maxByOrNull { (_, score) -> score }
                ?.first
            val trackId = match?.trackId ?: nextTrackId()
            assignedTrackIds += trackId
            TrackedBoundingBox(trackId, detection)
        }

        val updatedTracks = results.map { Track(it.trackId, it.boundingBox, missedFrames = 0) }
        val missedTracks = tracks
            .filterNot { it.trackId in assignedTrackIds }
            .map { it.copy(missedFrames = it.missedFrames + 1) }
            .filter { it.missedFrames <= maximumMissedFrames }
        tracks = updatedTracks + missedTracks
        return results
    }

    private fun nextTrackId(): String = "face-${nextTrackNumber++.toString().padStart(3, '0')}"

    private data class Track(
        val trackId: String,
        val boundingBox: NormalizedBoundingBox,
        val missedFrames: Int,
    )
}

data class TrackedBoundingBox(
    val trackId: String,
    val boundingBox: NormalizedBoundingBox,
)
