package com.example.pepper_person_id_poc.speakerbenchmark.features

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Row-major log Mel-filterbank features. */
data class FeatureMatrix(
    val numFrames: Int,
    val numBins: Int,
    val values: FloatArray,
) {
    init {
        require(numFrames > 0)
        require(numBins > 0)
        require(values.size == numFrames * numBins)
        require(values.all(Float::isFinite))
    }
}

enum class FeatureNormalization {
    NONE,
    GLOBAL_MEAN,
}

data class LogMelFbankConfig(
    val sampleRate: Int = 16_000,
    val numBins: Int = 80,
    val frameLengthMillis: Int = 25,
    val frameShiftMillis: Int = 10,
    val lowFrequency: Float = 20f,
    val highFrequency: Float = 7_600f,
    val removeDcOffset: Boolean = true,
    val preemphasisCoefficient: Float = 0.97f,
    val normalizeInputSamples: Boolean = false,
    val featureNormalization: FeatureNormalization = FeatureNormalization.GLOBAL_MEAN,
) {
    init {
        require(sampleRate > 0)
        require(numBins >= 3)
        require(frameLengthMillis > 0)
        require(frameShiftMillis > 0)
        require(lowFrequency >= 0f)
        require(highFrequency > lowFrequency && highFrequency <= sampleRate / 2f)
        require(preemphasisCoefficient in 0f..1f)
    }
}

/**
 * Pure-Kotlin port of the sherpa-onnx v1.13.4 speaker feature path.
 *
 * The implementation follows kaldi-native-fbank v1.22.3: reflected edges for
 * `snip_edges=false`, power spectrum, natural-log Mel energies, and the runtime-specific
 * utterance normalization used by the current 3D-Speaker and WeSpeaker models.
 */
class LogMelFbank(
    private val config: LogMelFbankConfig,
) {
    private val frameLength = config.sampleRate * config.frameLengthMillis / 1_000
    private val frameShift = config.sampleRate * config.frameShiftMillis / 1_000
    private val fftSize = nextPowerOfTwo(frameLength)
    private val window = createWindow(frameLength)
    private val melFilters = createMelFilters()

    fun compute(normalizedSamples: FloatArray): FeatureMatrix {
        require(normalizedSamples.isNotEmpty()) { "audio samples must not be empty" }
        require(normalizedSamples.all(Float::isFinite)) { "audio samples must be finite" }

        val samples = if (config.normalizeInputSamples) {
            normalizedSamples.copyOf()
        } else {
            FloatArray(normalizedSamples.size) { index -> normalizedSamples[index] * 32_768f }
        }
        val numFrames = numberOfFrames(samples.size)
        require(numFrames > 0) {
            "audio is too short for a ${config.frameLengthMillis} ms feature frame"
        }

        val features = FloatArray(numFrames * config.numBins)
        repeat(numFrames) { frameIndex ->
            val frame = extractFrame(samples, frameIndex)
            processFrame(frame)
            val power = powerSpectrum(frame)
            val rowOffset = frameIndex * config.numBins
            melFilters.forEachIndexed { bin, filter ->
                var energy = 0f
                filter.indices.forEach { frequencyBin ->
                    energy += filter[frequencyBin] * power[frequencyBin]
                }
                features[rowOffset + bin] = ln(max(energy, FLOAT_EPSILON))
            }
        }

        normalizeFeatures(features, numFrames)
        return FeatureMatrix(numFrames, config.numBins, features)
    }

    private fun numberOfFrames(numSamples: Int): Int = (numSamples + frameShift / 2) / frameShift

    private fun extractFrame(samples: FloatArray, frameIndex: Int): FloatArray {
        val start = frameIndex * frameShift + frameShift / 2 - frameLength / 2
        val frame = FloatArray(fftSize)
        repeat(frameLength) { offset ->
            var sampleIndex = start + offset
            while (sampleIndex < 0 || sampleIndex >= samples.size) {
                sampleIndex = if (sampleIndex < 0) {
                    -sampleIndex - 1
                } else {
                    2 * samples.size - 1 - sampleIndex
                }
            }
            frame[offset] = samples[sampleIndex]
        }
        return frame
    }

    private fun processFrame(frame: FloatArray) {
        if (config.removeDcOffset) {
            var sum = 0f
            repeat(frameLength) { sum += frame[it] }
            val mean = sum / frameLength
            repeat(frameLength) { frame[it] -= mean }
        }
        if (config.preemphasisCoefficient != 0f) {
            for (index in frameLength - 1 downTo 1) {
                frame[index] -= config.preemphasisCoefficient * frame[index - 1]
            }
            frame[0] -= config.preemphasisCoefficient * frame[0]
        }
        repeat(frameLength) { frame[it] *= window[it] }
    }

    private fun powerSpectrum(frame: FloatArray): FloatArray {
        val real = DoubleArray(fftSize) { frame[it].toDouble() }
        val imaginary = DoubleArray(fftSize)
        fft(real, imaginary)
        return FloatArray(fftSize / 2 + 1) { index ->
            (real[index] * real[index] + imaginary[index] * imaginary[index]).toFloat()
        }
    }

    private fun normalizeFeatures(features: FloatArray, numFrames: Int) {
        when (config.featureNormalization) {
            FeatureNormalization.NONE -> Unit
            FeatureNormalization.GLOBAL_MEAN -> {
                repeat(config.numBins) { bin ->
                    var sum = 0.0
                    repeat(numFrames) { frame -> sum += features[frame * config.numBins + bin] }
                    val mean = (sum / numFrames).toFloat()
                    repeat(numFrames) { frame -> features[frame * config.numBins + bin] -= mean }
                }
            }
        }
    }

    private fun createMelFilters(): Array<FloatArray> {
        val frequencyBins = fftSize / 2 + 1
        val lowestMel = frequencyToMel(config.lowFrequency.toDouble())
        val highestMel = frequencyToMel(config.highFrequency.toDouble())
        val melStep = (highestMel - lowestMel) / (config.numBins + 1)
        val boundaries = DoubleArray(config.numBins + 2) { index ->
            melToFrequency(lowestMel + index * melStep)
        }
        val fftBinWidth = config.sampleRate.toDouble() / fftSize
        val lastExclusive = frequencyBins - 1

        return Array(config.numBins) { bin ->
            val left = boundaries[bin]
            val center = boundaries[bin + 1]
            val right = boundaries[bin + 2]
            FloatArray(frequencyBins) { index ->
                if (index >= lastExclusive) {
                    0f
                } else {
                    val frequency = index * fftBinWidth
                    val weight = when {
                        frequency <= left || frequency >= right -> 0.0
                        frequency <= center -> (frequency - left) / (center - left)
                        else -> (right - frequency) / (right - center)
                    }
                    weight.toFloat()
                }
            }
        }
    }

    private fun frequencyToMel(frequency: Double): Double =
        1127.0 * ln(1.0 + frequency / 700.0)

    private fun melToFrequency(mel: Double): Double =
        700.0 * (exp(mel / 1127.0) - 1.0)

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var j = 0
        for (i in 1 until real.size) {
            var bit = real.size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }

        var length = 2
        while (length <= real.size) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            var start = 0
            while (start < real.size) {
                var weightReal = 1.0
                var weightImaginary = 0.0
                repeat(length / 2) { offset ->
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * weightReal - imaginary[odd] * weightImaginary
                    val oddImaginary = real[odd] * weightImaginary + imaginary[odd] * weightReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = weightReal * stepReal - weightImaginary * stepImaginary
                    weightImaginary = weightReal * stepImaginary + weightImaginary * stepReal
                    weightReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }

    private fun createWindow(length: Int): FloatArray = FloatArray(length) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / (length - 1))).pow(0.85).toFloat()
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var result = 1
        while (result < value) result = result shl 1
        return result
    }

    private companion object {
        const val FLOAT_EPSILON = 1.1920929e-7f
    }
}
