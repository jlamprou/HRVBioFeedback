package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Calculates time-domain and frequency-domain HRV metrics.
 * Based on the 1996 Task Force standards and Shaffer & Ginsberg 2017.
 */
class HrvMetricsCalculator @Inject constructor() {

    companion object {
        // Frequency band boundaries in Hz
        const val LF_LOW = 0.04
        const val LF_HIGH = 0.15
        const val HF_LOW = 0.15
        const val HF_HIGH = 0.40
    }

    // --- Time-Domain Metrics ---

    fun calculateRmssd(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0
        var sumSquaredDiffs = 0.0
        var count = 0
        for (i in 1 until rrIntervals.size) {
            val diff = rrIntervals[i] - rrIntervals[i - 1]
            sumSquaredDiffs += diff * diff
            count++
        }
        return if (count > 0) sqrt(sumSquaredDiffs / count) else 0.0
    }

    fun calculateSdnn(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0
        val mean = rrIntervals.average()
        val variance = rrIntervals.sumOf { (it - mean) * (it - mean) } / (rrIntervals.size - 1)
        return sqrt(variance)
    }

    fun calculatePnn50(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0
        var count50 = 0
        for (i in 1 until rrIntervals.size) {
            if (abs(rrIntervals[i] - rrIntervals[i - 1]) > 50.0) {
                count50++
            }
        }
        return (count50.toDouble() / (rrIntervals.size - 1)) * 100.0
    }

    fun calculateMeanHr(rrIntervals: List<Double>): Double {
        if (rrIntervals.isEmpty()) return 0.0
        val meanRr = rrIntervals.average()
        return if (meanRr > 0) 60000.0 / meanRr else 0.0
    }

    // --- Frequency-Domain Metrics ---

    /**
     * Integrate PSD over a frequency band using the trapezoidal rule.
     */
    fun bandPower(psd: PowerSpectralDensity, lowFreq: Double, highFreq: Double): Double {
        var power = 0.0
        for (i in 1 until psd.frequencies.size) {
            val f0 = psd.frequencies[i - 1]
            val f1 = psd.frequencies[i]
            if (f1 >= lowFreq && f0 <= highFreq) {
                val fLow = maxOf(f0, lowFreq)
                val fHigh = minOf(f1, highFreq)
                // Linear interpolation for boundary bins
                val p0 = psd.power[i - 1]
                val p1 = psd.power[i]
                val avgPower = (p0 + p1) / 2.0
                power += avgPower * (fHigh - fLow)
            }
        }
        return power
    }

    fun calculateLfPower(psd: PowerSpectralDensity): Double = bandPower(psd, LF_LOW, LF_HIGH)

    fun calculateHfPower(psd: PowerSpectralDensity): Double = bandPower(psd, HF_LOW, HF_HIGH)

    fun calculateTotalPower(psd: PowerSpectralDensity): Double = bandPower(psd, LF_LOW, HF_HIGH)

    /**
     * Find the frequency with the highest power in the LF band.
     * This indicates the dominant oscillation frequency (ideally near the breathing rate during RF training).
     */
    fun peakLfFrequency(psd: PowerSpectralDensity): Double {
        var maxPower = 0.0
        var peakFreq = 0.0
        for (i in psd.frequencies.indices) {
            val f = psd.frequencies[i]
            if (f in LF_LOW..LF_HIGH && psd.power[i] > maxPower) {
                maxPower = psd.power[i]
                peakFreq = f
            }
        }
        return peakFreq
    }
}
