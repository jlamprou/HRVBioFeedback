package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject

/**
 * Calculates the cardiac coherence score using the HeartMath algorithm.
 *
 * Per McCraty (2022) and the HeartMath Institute specification:
 * 1. Identify the maximum peak in the 0.04-0.26 Hz range of the HRV power spectrum
 * 2. Calculate the integral in a window 0.030 Hz wide centered on the highest peak
 * 3. Coherence Score = Peak Power / (Total Power - Peak Power)
 *
 * The denominator uses total power across the ENTIRE spectrum (0 to Nyquist),
 * not just the coherence band. This makes the score unbounded above
 * (typical range 0-16, where higher = more coherent).
 *
 * References:
 * - McCraty R (2022) "Following the Rhythm of the Heart: HeartMath Institute's
 *   Path to HRV Biofeedback" Applied Psychophysiology and Biofeedback 47:63-74
 * - HeartMath Institute: "How is Coherence Determined by HeartMath Programs?"
 *   https://help.heartmath.com/emwave-pro/how-is-coherence-determined-by-heartmath-programs/
 */
class CoherenceCalculator @Inject constructor() {

    companion object {
        const val COHERENCE_BAND_LOW = 0.04  // Hz - lower bound for peak search
        const val COHERENCE_BAND_HIGH = 0.26 // Hz - upper bound for peak search
        const val PEAK_WINDOW_HZ = 0.030     // Hz - width of integration window around peak

        // HeartMath coherence thresholds (Inner Balance, Challenge Level 1)
        // Per HeartMath Inner Balance Coherence Scoring System document:
        // Level 1: Low/Medium = 0.5, Medium/High = 1.8
        // Level 2: Low/Medium = 0.6, Medium/High = 4.0
        // Level 3: Low/Medium = 0.6, Medium/High = 4.0
        // Level 4: Low/Medium = 2.1, Medium/High = 6.0
        const val THRESHOLD_LOW_MEDIUM = 0.5
        const val THRESHOLD_MEDIUM_HIGH = 1.8
    }

    data class CoherenceResult(
        val coherenceScore: Double,    // Unbounded (typically 0-16), per HeartMath formula
        val peakFrequency: Double,     // Hz - frequency of the dominant peak
        val peakPower: Double          // Power at the dominant peak
    )

    /**
     * Calculate the HeartMath coherence score from a power spectral density.
     *
     * @param psd Power spectral density from AR analysis
     * @return CoherenceResult with the unbounded coherence score
     */
    fun calculate(psd: PowerSpectralDensity): CoherenceResult {
        // Step 1: Find the maximum peak in the 0.04-0.26 Hz coherence band
        var maxPower = 0.0
        var peakIndex = -1

        for (i in psd.frequencies.indices) {
            val f = psd.frequencies[i]
            if (f in COHERENCE_BAND_LOW..COHERENCE_BAND_HIGH && psd.power[i] > maxPower) {
                maxPower = psd.power[i]
                peakIndex = i
            }
        }

        if (peakIndex < 0) {
            return CoherenceResult(0.0, 0.0, 0.0)
        }

        val peakFreq = psd.frequencies[peakIndex]
        val halfWindow = PEAK_WINDOW_HZ / 2.0

        // Step 2: Integrate power in the peak window (0.030 Hz centered on peak)
        var peakWindowPower = 0.0
        // Step 3: Integrate total power across the ENTIRE spectrum (0 to Nyquist)
        var totalPower = 0.0

        for (i in 1 until psd.frequencies.size) {
            val f0 = psd.frequencies[i - 1]
            val f1 = psd.frequencies[i]
            val avgPower = (psd.power[i - 1] + psd.power[i]) / 2.0
            val df = f1 - f0

            // Total power: integrate over entire spectrum
            totalPower += avgPower * df

            // Peak window power: integrate only within +/- 0.015 Hz of peak
            if (f0 >= peakFreq - halfWindow && f1 <= peakFreq + halfWindow) {
                peakWindowPower += avgPower * df
            }
        }

        // Step 4: Coherence Score = Peak Power / (Total Power - Peak Power)
        // Per HeartMath specification. Score is unbounded above (0 to ~16 typical).
        val denominator = totalPower - peakWindowPower
        val coherenceScore = if (denominator > 0) {
            peakWindowPower / denominator
        } else {
            0.0
        }

        return CoherenceResult(
            coherenceScore = coherenceScore.coerceAtLeast(0.0),
            peakFrequency = peakFreq,
            peakPower = maxPower
        )
    }
}
