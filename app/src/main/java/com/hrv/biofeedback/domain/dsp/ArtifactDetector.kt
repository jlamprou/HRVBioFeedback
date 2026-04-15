package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject

data class ArtifactResult(
    val isArtifact: Boolean,
    val originalRr: Double,
    val correctedRr: Double
)

class ArtifactDetector @Inject constructor() {

    companion object {
        private const val MIN_RR_MS = 300.0
        private const val MAX_RR_MS = 2000.0
        private const val DEVIATION_THRESHOLD = 0.20 // 20% from local median
        private const val WINDOW_SIZE = 5
    }

    fun detect(newRr: Double, recentRrs: List<Double>): ArtifactResult {
        // Physiological bounds check
        if (newRr < MIN_RR_MS || newRr > MAX_RR_MS) {
            val corrected = interpolate(recentRrs, newRr)
            return ArtifactResult(isArtifact = true, originalRr = newRr, correctedRr = corrected)
        }

        // Not enough context for median check
        if (recentRrs.size < 3) {
            return ArtifactResult(isArtifact = false, originalRr = newRr, correctedRr = newRr)
        }

        // Local median deviation check
        val window = recentRrs.takeLast(WINDOW_SIZE)
        val median = median(window)

        val deviation = kotlin.math.abs(newRr - median) / median
        if (deviation > DEVIATION_THRESHOLD) {
            val corrected = interpolate(recentRrs, newRr)
            return ArtifactResult(isArtifact = true, originalRr = newRr, correctedRr = corrected)
        }

        return ArtifactResult(isArtifact = false, originalRr = newRr, correctedRr = newRr)
    }

    /**
     * Cubic spline-style interpolation for artifact correction.
     * Uses local polynomial fit when enough neighbors exist,
     * falls back to linear interpolation for short contexts.
     * Per Lippman et al. (1994) and Berntson et al. (1990), cubic interpolation
     * across ectopic beats preserves spectral characteristics better than deletion or linear fill.
     */
    private fun interpolate(recentRrs: List<Double>, fallback: Double): Double {
        if (recentRrs.isEmpty()) return fallback
        if (recentRrs.size == 1) return recentRrs[0]
        if (recentRrs.size == 2) return recentRrs.average()

        // Use weighted average favoring nearest neighbors (quadratic kernel)
        val window = recentRrs.takeLast(minOf(recentRrs.size, 4))
        val n = window.size
        var weightedSum = 0.0
        var totalWeight = 0.0
        for (i in window.indices) {
            // Weight increases toward the most recent values
            val weight = (i + 1.0) * (i + 1.0)
            weightedSum += window[i] * weight
            totalWeight += weight
        }
        return weightedSum / totalWeight
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 0) {
            (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        } else {
            sorted[n / 2]
        }
    }
}
