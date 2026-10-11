package net.ericclark.studiare.components.speech

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * RMS-based per-clip loudness normalization for Sherpa-ONNX TTS output. Each language uses a
 * separately trained Piper/VITS checkpoint with no standardized output loudness, so without this,
 * some languages play noticeably louder or quieter than others. RMS (not peak) is used because it
 * tracks perceived loudness, the same reason broadcast/podcast loudness normalization uses an
 * RMS/LUFS-style measure rather than peak amplitude.
 */
internal const val TARGET_RMS = 0.1f
internal const val MIN_GAIN = 0.3f
internal const val MAX_GAIN = 4.0f
internal const val SILENCE_RMS_THRESHOLD = 0.001f
internal const val PEAK_CEILING = 0.98f

/**
 * Scales [samples] in place so its RMS amplitude is close to [targetRms], clamped to
 * [[minGain], [maxGain]] and backed off further if needed so the loudest sample stays at or
 * below [peakCeiling] (float PCM clips/distorts past ±1.0). Near-silent clips (RMS below
 * [SILENCE_RMS_THRESHOLD]) are left untouched — there's nothing meaningful to normalize, and
 * amplifying them would just boost noise floor.
 */
internal fun normalizeLoudness(
    samples: FloatArray,
    targetRms: Float = TARGET_RMS,
    minGain: Float = MIN_GAIN,
    maxGain: Float = MAX_GAIN,
    peakCeiling: Float = PEAK_CEILING
): FloatArray {
    if (samples.isEmpty()) return samples

    var sumSquares = 0.0
    var peak = 0f
    for (sample in samples) {
        sumSquares += sample.toDouble() * sample.toDouble()
        val abs = abs(sample)
        if (abs > peak) peak = abs
    }
    val rms = sqrt(sumSquares / samples.size).toFloat()
    if (rms < SILENCE_RMS_THRESHOLD) return samples

    var gain = (targetRms / rms).coerceIn(minGain, maxGain)
    val projectedPeak = peak * gain
    if (projectedPeak > peakCeiling) {
        gain *= peakCeiling / projectedPeak
    }

    for (i in samples.indices) {
        samples[i] *= gain
    }
    return samples
}
