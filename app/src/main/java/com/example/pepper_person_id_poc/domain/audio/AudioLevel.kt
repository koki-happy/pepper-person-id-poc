package com.example.pepper_person_id_poc.domain.audio

import kotlin.math.log10
import kotlin.math.sqrt

object AudioLevel {
    fun dbFs(samples: ShortArray): Float {
        if (samples.isEmpty()) return MIN_DB_FS
        val meanSquare = samples.sumOf { sample ->
            val normalized = sample.toDouble() / Short.MAX_VALUE
            normalized * normalized
        } / samples.size
        if (meanSquare <= 0.0) return MIN_DB_FS
        return (20.0 * log10(sqrt(meanSquare))).toFloat().coerceIn(MIN_DB_FS, 0f)
    }

    const val MIN_DB_FS = -90f
}
