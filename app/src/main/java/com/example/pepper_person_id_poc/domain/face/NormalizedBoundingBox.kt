package com.example.pepper_person_id_poc.domain.face

data class NormalizedBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height

    fun intersectionOverUnion(other: NormalizedBoundingBox): Float {
        val intersectionLeft = maxOf(left, other.left)
        val intersectionTop = maxOf(top, other.top)
        val intersectionRight = minOf(right, other.right)
        val intersectionBottom = minOf(bottom, other.bottom)
        val intersectionWidth = (intersectionRight - intersectionLeft).coerceAtLeast(0f)
        val intersectionHeight = (intersectionBottom - intersectionTop).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val union = area + other.area - intersection
        return if (union > 0f) intersection / union else 0f
    }

    fun rotated(rotationDegrees: Int): NormalizedBoundingBox = when (rotationDegrees.mod(360)) {
        90 -> NormalizedBoundingBox(1f - bottom, left, 1f - top, right)
        180 -> NormalizedBoundingBox(1f - right, 1f - bottom, 1f - left, 1f - top)
        270 -> NormalizedBoundingBox(top, 1f - right, bottom, 1f - left)
        else -> this
    }
}
