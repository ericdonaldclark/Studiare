package net.ericclark.studiare.components.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class LoudnessNormalizerTest {

    private fun rmsOf(samples: FloatArray): Float =
        sqrt(samples.sumOf { it.toDouble() * it.toDouble() } / samples.size).toFloat()

    private fun peakOf(samples: FloatArray): Float = samples.maxOf { abs(it) }

    @Test
    fun quietClip_isBoostedToTargetRms() {
        val result = normalizeLoudness(FloatArray(1000) { 0.03f })
        assertEquals(TARGET_RMS, rmsOf(result), 0.001f)
    }

    @Test
    fun moderatelyLoudClip_isAttenuatedToTargetRms() {
        val result = normalizeLoudness(FloatArray(1000) { 0.2f })
        assertEquals(TARGET_RMS, rmsOf(result), 0.001f)
    }

    @Test
    fun veryLoudClip_gainIsClampedAtMinGain() {
        val result = normalizeLoudness(FloatArray(1000) { 0.5f })
        // Raw ratio (0.1 / 0.5 = 0.2x) is below MIN_GAIN, so gain should clamp at MIN_GAIN
        // rather than driving the clip all the way down to TARGET_RMS.
        assertEquals(0.5f * MIN_GAIN, result[0], 0.001f)
    }

    @Test
    fun nearSilentClip_gainIsClampedAtMaxGain() {
        val result = normalizeLoudness(FloatArray(1000) { 0.002f })
        // Raw ratio (0.1 / 0.002 = 50x) is above MAX_GAIN, so gain should clamp at MAX_GAIN.
        assertEquals(0.002f * MAX_GAIN, result[0], 0.0005f)
    }

    @Test
    fun peakyClip_gainIsBackedOffToAvoidClipping() {
        val samples = FloatArray(1000) { 0.01f }
        samples[0] = 0.9f
        val result = normalizeLoudness(samples)
        assertTrue(peakOf(result) <= PEAK_CEILING + 0.0001f)
    }

    @Test
    fun silentClip_isLeftUnchanged() {
        val result = normalizeLoudness(FloatArray(500) { 0f })
        assertTrue(result.all { it == 0f })
    }

    @Test
    fun clipBelowSilenceThreshold_isLeftUnchanged() {
        val result = normalizeLoudness(FloatArray(500) { 0.0001f })
        assertEquals(0.0001f, result[0], 0.0000001f)
    }

    @Test
    fun emptyArray_returnsEmptyWithoutCrashing() {
        val result = normalizeLoudness(FloatArray(0))
        assertEquals(0, result.size)
    }
}
