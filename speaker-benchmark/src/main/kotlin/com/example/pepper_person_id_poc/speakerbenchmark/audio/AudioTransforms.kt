package com.example.pepper_person_id_poc.speakerbenchmark.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object AudioTransforms {
    fun centerCrop(samples: FloatArray, sampleCount: Int): FloatArray {
        require(sampleCount > 0) { "Center-crop sample count must be greater than zero" }
        require(samples.size >= sampleCount) {
            "Audio has ${samples.size} samples but the center crop requires $sampleCount"
        }
        val start = (samples.size - sampleCount) / 2
        return samples.copyOfRange(start, start + sampleCount)
    }
}

/**
 * Deterministic band-limited resampling with a Blackman-windowed sinc low-pass filter.
 *
 * The JVS path uses the rational 24 kHz -> 16 kHz case, so only two fractional-phase
 * kernels are needed. Kernels are cached per call and reused for every output sample.
 */
object WindowedSincResampler {
    fun resample(samples: FloatArray, sourceSampleRate: Int, targetSampleRate: Int): FloatArray {
        require(samples.isNotEmpty()) { "Cannot resample empty audio" }
        require(sourceSampleRate > 0) { "Source sample rate must be greater than zero" }
        require(targetSampleRate > 0) { "Target sample rate must be greater than zero" }
        require(samples.all(Float::isFinite)) { "Audio samples must all be finite" }
        if (sourceSampleRate == targetSampleRate) return samples.copyOf()

        val outputSizeLong = samples.size.toLong() * targetSampleRate / sourceSampleRate
        require(outputSizeLong in 1..Int.MAX_VALUE.toLong()) {
            "Resampled audio size is outside the supported range: $outputSizeLong"
        }

        val commonDivisor = greatestCommonDivisor(sourceSampleRate, targetSampleRate)
        val phaseCount = targetSampleRate / commonDivisor
        val offsets = IntArray(TAP_COUNT) { it - HALF_WIDTH }
        val cutoff = 0.5 * minOf(1.0, targetSampleRate.toDouble() / sourceSampleRate) * ROLLOFF
        val kernels = Array(phaseCount) { phase ->
            val fraction = phase.toDouble() / phaseCount
            val weights = DoubleArray(TAP_COUNT) { index ->
                val distance = offsets[index] - fraction
                lowPassKernel(distance, cutoff)
            }
            val sum = weights.sum()
            require(abs(sum) > MIN_WEIGHT_SUM) { "Resampling filter has a zero-valued phase" }
            DoubleArray(TAP_COUNT) { index -> weights[index] / sum }
        }

        return FloatArray(outputSizeLong.toInt()) { outputIndex ->
            val sourcePositionNumerator = outputIndex.toLong() * sourceSampleRate
            val sourceIndex = (sourcePositionNumerator / targetSampleRate).toInt()
            val phase = ((sourcePositionNumerator / commonDivisor) % phaseCount).toInt()
            val weights = kernels[phase]
            var weightedSample = 0.0
            var availableWeight = 0.0
            for (tap in offsets.indices) {
                val inputIndex = sourceIndex + offsets[tap]
                if (inputIndex in samples.indices) {
                    weightedSample += samples[inputIndex] * weights[tap]
                    availableWeight += weights[tap]
                }
            }
            if (abs(availableWeight) <= MIN_WEIGHT_SUM) {
                samples[sourceIndex.coerceIn(samples.indices)]
            } else {
                (weightedSample / availableWeight).toFloat()
            }
        }
    }

    private fun lowPassKernel(distance: Double, cutoff: Double): Double {
        if (abs(distance) >= HALF_WIDTH) return 0.0
        val scaled = 2.0 * cutoff * distance
        val sinc = if (abs(scaled) < 1e-12) 1.0 else sin(PI * scaled) / (PI * scaled)
        val normalizedDistance = distance / HALF_WIDTH
        val window = 0.42 +
            0.5 * cos(PI * normalizedDistance) +
            0.08 * cos(2.0 * PI * normalizedDistance)
        return 2.0 * cutoff * sinc * window
    }

    private tailrec fun greatestCommonDivisor(a: Int, b: Int): Int =
        if (b == 0) a else greatestCommonDivisor(b, a % b)

    private const val HALF_WIDTH = 24
    private const val TAP_COUNT = HALF_WIDTH * 2 + 1
    private const val ROLLOFF = 0.95
    private const val MIN_WEIGHT_SUM = 1e-12
}
