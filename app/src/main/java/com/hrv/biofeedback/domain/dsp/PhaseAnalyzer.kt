package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Analyzes phase relationships and spectral characteristics for RF assessment.
 *
 * Implements the scientific criteria from Shaffer et al. (2020):
 * 1. Phase synchrony between HR oscillation and breathing pacer
 * 2. HR waveform smoothness (spectral concentration)
 * 3. Number of LF peaks (spectral simplicity)
 */
class PhaseAnalyzer @Inject constructor() {

    /**
     * Calculate phase synchrony between HR oscillations and the breathing pacer.
     *
     * At resonance, HR and respiration should be phase-locked at ~0 degrees:
     * HR peaks align with inhalation peaks. We measure this by computing the
     * cross-spectral phase at the breathing frequency between the HR signal
     * and a synthetic breathing reference.
     *
     * @param instantaneousHr Evenly-sampled HR time series
     * @param sampleRate Sample rate in Hz
     * @param breathingRateHz Target breathing frequency in Hz
     * @return Phase synchrony score from 0 to 1 (1 = perfect synchrony)
     */
    fun calculatePhaseSynchrony(
        instantaneousHr: DoubleArray,
        sampleRate: Double,
        breathingRateHz: Double
    ): Double {
        if (instantaneousHr.size < 20) return 0.0

        val n = instantaneousHr.size
        val meanHr = instantaneousHr.average()

        // Generate synthetic breathing reference signal (cosine at breathing frequency)
        val breathingRef = DoubleArray(n) { i ->
            cos(2.0 * PI * breathingRateHz * i / sampleRate)
        }

        // Compute cross-correlation at the breathing frequency using DFT
        // This gives us the phase relationship between HR and breathing
        var realHr = 0.0
        var imagHr = 0.0
        var realRef = 0.0
        var imagRef = 0.0

        for (i in 0 until n) {
            val angle = 2.0 * PI * breathingRateHz * i / sampleRate
            val hrCentered = instantaneousHr[i] - meanHr

            realHr += hrCentered * cos(angle)
            imagHr -= hrCentered * sin(angle)
            realRef += breathingRef[i] * cos(angle)
            imagRef -= breathingRef[i] * sin(angle)
        }

        // Cross-spectral phase
        // Phase of HR at breathing frequency
        val hrPhase = atan2(imagHr, realHr)
        val refPhase = atan2(imagRef, realRef)

        // Phase difference (should be near 0 for perfect synchrony)
        var phaseDiff = abs(hrPhase - refPhase)
        if (phaseDiff > PI) phaseDiff = 2.0 * PI - phaseDiff

        // Also check HR amplitude at breathing frequency (should be high)
        val hrAmplitude = sqrt(realHr * realHr + imagHr * imagHr) / n

        // Combine phase alignment (cos of phase diff maps 0->1, PI->-1)
        // and amplitude contribution
        val phaseScore = (cos(phaseDiff) + 1.0) / 2.0 // Maps [-1,1] to [0,1]

        // Weight by how much HR power exists at the breathing frequency
        // relative to total HR variance
        val hrVariance = instantaneousHr.sumOf { (it - meanHr) * (it - meanHr) } / n
        val amplitudeFraction = if (hrVariance > 0) {
            (hrAmplitude * hrAmplitude / hrVariance).coerceIn(0.0, 1.0)
        } else 0.0

        // Final score: phase alignment weighted by signal strength
        return (0.6 * phaseScore + 0.4 * amplitudeFraction).coerceIn(0.0, 1.0)
    }

    /**
     * Calculate HR waveform smoothness.
     *
     * Per Shaffer et al. (2020), a smooth sinusoidal HR waveform indicates
     * better entrainment than a jagged one. We measure this as the ratio of
     * power at the dominant frequency to total power — a pure sine wave
     * concentrates all power at one frequency.
     *
     * @param psd Power spectral density from AR analysis
     * @return Smoothness score from 0 to 1 (1 = perfect sinusoid)
     */
    fun calculateWaveformSmoothness(psd: PowerSpectralDensity): Double {
        if (psd.power.isEmpty()) return 0.0

        // Find peak in the LF band (0.04-0.15 Hz)
        var maxPower = 0.0
        var peakIndex = -1
        for (i in psd.frequencies.indices) {
            if (psd.frequencies[i] in 0.04..0.15 && psd.power[i] > maxPower) {
                maxPower = psd.power[i]
                peakIndex = i
            }
        }
        if (peakIndex < 0) return 0.0

        // Calculate power in a narrow band around the peak (+/- 0.02 Hz)
        val peakFreq = psd.frequencies[peakIndex]
        var peakBandPower = 0.0
        var totalLfPower = 0.0

        for (i in 1 until psd.frequencies.size) {
            val f = (psd.frequencies[i - 1] + psd.frequencies[i]) / 2.0
            val power = (psd.power[i - 1] + psd.power[i]) / 2.0
            val df = psd.frequencies[i] - psd.frequencies[i - 1]

            if (f in 0.04..0.15) {
                totalLfPower += power * df
                if (f in (peakFreq - 0.02)..(peakFreq + 0.02)) {
                    peakBandPower += power * df
                }
            }
        }

        // Smoothness = fraction of LF power concentrated at the peak
        return if (totalLfPower > 0) {
            (peakBandPower / totalLfPower).coerceIn(0.0, 1.0)
        } else 0.0
    }

    /**
     * Count the number of distinct peaks in the LF band.
     *
     * Per Shaffer et al. (2020) criterion #6: fewer LF peaks indicates
     * more consistent breathing at a single frequency. Multiple peaks
     * suggest the user breathed across wider frequency bands.
     *
     * @param psd Power spectral density
     * @return Number of distinct peaks (1 is ideal)
     */
    fun countLfPeaks(psd: PowerSpectralDensity): Int {
        if (psd.power.isEmpty()) return 0

        // Find peak power for threshold
        var maxLfPower = 0.0
        for (i in psd.frequencies.indices) {
            if (psd.frequencies[i] in 0.04..0.15) {
                maxLfPower = maxOf(maxLfPower, psd.power[i])
            }
        }
        if (maxLfPower == 0.0) return 0

        // Count peaks above 30% of maximum (significant peaks only)
        val threshold = maxLfPower * 0.30
        var peakCount = 0
        var inPeak = false

        for (i in psd.frequencies.indices) {
            if (psd.frequencies[i] !in 0.04..0.15) continue

            if (psd.power[i] > threshold) {
                if (!inPeak) {
                    // Check it's a real local max, not just a shoulder
                    val isLocalMax = (i == 0 || psd.power[i] >= psd.power[i - 1]) &&
                            (i == psd.power.lastIndex || psd.power[i] >= psd.power[i + 1])
                    if (isLocalMax) {
                        peakCount++
                    }
                    inPeak = true
                }
            } else {
                inPeak = false
            }
        }

        return maxOf(peakCount, 1) // At least 1 if there's any LF power
    }

    /**
     * Convert LF peak count to a 0-1 score.
     * 1 peak = 1.0, 2 peaks = 0.5, 3+ peaks = 0.0
     */
    fun lfPeakScore(peakCount: Int): Double {
        return when {
            peakCount <= 1 -> 1.0
            peakCount == 2 -> 0.5
            else -> 0.0
        }
    }
}
