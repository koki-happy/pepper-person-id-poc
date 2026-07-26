package com.example.pepper_person_id_poc.domain.face

class FaceTracker(
    private val minimumIntersectionOverUnion: Float = 0.30f,
    private val maximumMissedFrames: Int = 4,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var nextTrackNumber = 1
    private var tracks = emptyList<Track>()

    fun update(
        detections: List<NormalizedBoundingBox>,
        landmarkCounts: List<Int> = List(detections.size) { 0 },
    ): List<TrackedBoundingBox> {
        require(landmarkCounts.size == detections.size)
        require(landmarkCounts.all { it >= 0 })
        val now = elapsedRealtimeMillis()
        val availableTracks = tracks.toMutableList()
        val assignedTrackIds = mutableSetOf<String>()
        val results = detections.mapIndexed { index, detection ->
            val match = availableTracks
                .filterNot { it.trackId in assignedTrackIds }
                .map { it to it.boundingBox.intersectionOverUnion(detection) }
                .filter { (_, score) -> score >= minimumIntersectionOverUnion }
                .maxByOrNull { (_, score) -> score }
                ?.first
            val trackId = match?.trackId ?: nextTrackId()
            val firstSeen = match?.firstSeenElapsedRealtimeMillis ?: now
            assignedTrackIds += trackId
            TrackedBoundingBox(
                trackId = trackId,
                boundingBox = detection,
                firstSeenElapsedRealtimeMillis = firstSeen,
                trackDurationMillis = (now - firstSeen).coerceAtLeast(0L),
                landmarkCount = landmarkCounts[index],
            )
        }

        val updatedTracks = results.map {
            Track(
                trackId = it.trackId,
                boundingBox = it.boundingBox,
                missedFrames = 0,
                firstSeenElapsedRealtimeMillis = it.firstSeenElapsedRealtimeMillis,
            )
        }
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
        val firstSeenElapsedRealtimeMillis: Long,
    )
}

data class TrackedBoundingBox(
    val trackId: String,
    val boundingBox: NormalizedBoundingBox,
    val firstSeenElapsedRealtimeMillis: Long = 0L,
    val trackDurationMillis: Long = 0L,
    val landmarkCount: Int = 0,
)
