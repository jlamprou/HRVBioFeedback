package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Analyzes peak-to-trough HR amplitude per breathing cycle.
 * HR Max - HR Min is Shaffer et al. (2020) criterion #2 for identifying RF.
 *
 * Uses actual local peak/trough detection rather than fixed-window segmentation,
 * which avoids systematic underestimation when breathing rate drifts or
 * window boundaries split actual peaks.
 */
class PeakTroughAnalyzer @Inject constructor() {

    /**
     * Calculate the average peak-to-trough HR amplitude using local extrema detection.
     *
     * Algorithm:
     * 1. Low-pass filter the HR signal to remove beat-to-beat noise while preserving
     *    the breathing-frequency oscillation
     * 2. Detect local maxima and minima with minimum distance constraint based on
     *    expected breathing cycle length
     * 3. Pair consecutive max-min pairs and compute amplitude
     *
     * @param instantaneousHr Array of instantaneous HR values (bpm), evenly sampled
     * @param sampleRate Sample rate of the HR array in Hz
     * @param breathingRateBpm Current breathing rate in breaths per minute
     * @return Average peak-to-trough amplitude in bpm
     */
    fun analyze(
        instantaneousHr: DoubleArray,
        sampleRate: Double,
        breathingRateBpm: Double
    ): Double {
        if (instantaneousHr.size < 10 || breathingRateBpm <= 0) return 0.0

        // Minimum distance between peaks: half a breathing cycle
        // This prevents detecting noise as separate peaks
        val breathingFreqHz = breathingRateBpm / 60.0
        val minPeakDistance = (sampleRate / breathingFreqHz * 0.5).roundToInt().coerceAtLeast(2)

        // Smooth the signal with a moving average to reduce beat-to-beat noise
        // Window size = ~1/4 of a breathing cycle (preserves the oscillation shape)
        val smoothWindow = (sampleRate / breathingFreqHz * 0.25).roundToInt().coerceIn(1, instantaneousHr.size / 2)
        val smoothed = movingAverage(instantaneousHr, smoothWindow)

        // Detect local maxima
        val peaks = findLocalExtrema(smoothed, minPeakDistance, findMaxima = true)
        // Detect local minima
        val troughs = findLocalExtrema(smoothed, minPeakDistance, findMaxima = false)

        if (peaks.isEmpty() || troughs.isEmpty()) return 0.0

        // Pair peaks and troughs: for each peak, find the nearest trough
        // and compute the amplitude
        val amplitudes = mutableListOf<Double>()

        for (peakIdx in peaks) {
            // Find closest trough before and after this peak
            val troughBefore = troughs.lastOrNull { it < peakIdx }
            val troughAfter = troughs.firstOrNull { it > peakIdx }

            // Use the trough that gives the larger amplitude (more conservative)
            val peakValue = smoothed[peakIdx]
            val amplitudeBefore = if (troughBefore != null) peakValue - smoothed[troughBefore] else 0.0
            val amplitudeAfter = if (troughAfter != null) peakValue - smoothed[troughAfter] else 0.0

            val amplitude = maxOf(amplitudeBefore, amplitudeAfter)
            if (amplitude > 0) {
                amplitudes.add(amplitude)
            }
        }

        return if (amplitudes.isNotEmpty()) amplitudes.average() else 0.0
    }

    /**
     * Find local extrema (maxima or minima) with minimum distance constraint.
     *
     * @param data Input signal
     * @param minDistance Minimum number of samples between detected extrema
     * @param findMaxima If true, find maxima; if false, find minima
     * @return List of indices where extrema occur
     */
    private fun findLocalExtrema(
        data: DoubleArray,
        minDistance: Int,
        findMaxima: Boolean
    ): List<Int> {
        if (data.size < 3) return emptyList()

        // Find all candidate extrema
        val candidates = mutableListOf<Int>()
        for (i in 1 until data.size - 1) {
            val isExtremum = if (findMaxima) {
                data[i] > data[i - 1] && data[i] >= data[i + 1]
            } else {
                data[i] < data[i - 1] && data[i] <= data[i + 1]
            }
            if (isExtremum) {
                candidates.add(i)
            }
        }

        if (candidates.isEmpty()) return emptyList()

        // Enforce minimum distance: keep the most extreme value when peaks are too close
        val filtered = mutableListOf<Int>()
        filtered.add(candidates[0])

        for (i in 1 until candidates.size) {
            val lastKept = filtered.last()
            if (candidates[i] - lastKept >= minDistance) {
                filtered.add(candidates[i])
            } else {
                // Keep the more extreme one
                val keepNew = if (findMaxima) {
                    data[candidates[i]] > data[lastKept]
                } else {
                    data[candidates[i]] < data[lastKept]
                }
                if (keepNew) {
                    filtered[filtered.lastIndex] = candidates[i]
                }
            }
        }

        return filtered
    }

    /**
     * Simple centered moving average filter.
     */
    private fun movingAverage(data: DoubleArray, windowSize: Int): DoubleArray {
        if (windowSize <= 1) return data.copyOf()
        val result = DoubleArray(data.size)
        val halfWindow = windowSize / 2

        for (i in data.indices) {
            val start = maxOf(0, i - halfWindow)
            val end = minOf(data.size - 1, i + halfWindow)
            var sum = 0.0
            for (j in start..end) {
                sum += data[j]
            }
            result[i] = sum / (end - start + 1)
        }
        return result
    }
}
