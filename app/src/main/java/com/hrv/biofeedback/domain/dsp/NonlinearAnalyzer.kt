package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Nonlinear HRV analysis metrics that capture the complexity and
 * self-similarity of heart rate dynamics.
 *
 * These metrics complement traditional time-domain and frequency-domain measures
 * by quantifying aspects of autonomic regulation that linear methods miss.
 *
 * References:
 * - Shaffer & Ginsberg (2017) "An Overview of Heart Rate Variability Metrics and Norms"
 * - Peng et al. (1995) "Quantification of Scaling Exponents and Crossover Phenomena
 *   in Nonstationary Heartbeat Time Series"
 * - Richman & Moorman (2000) "Physiological Time-Series Analysis Using
 *   Approximate Entropy and Sample Entropy"
 */
class NonlinearAnalyzer @Inject constructor() {

    data class NonlinearMetrics(
        val sd1: Double,      // Poincaré SD1 (ms) — short-term variability (≈ RMSSD / √2)
        val sd2: Double,      // Poincaré SD2 (ms) — long-term variability
        val sd1sd2Ratio: Double, // SD1/SD2 — autonomic balance index
        val dfaAlpha1: Double,   // DFA short-term scaling exponent (4-16 beats)
        val sampleEntropy: Double // SampEn(m=2, r=0.2*SDNN) — regularity/complexity
    )

    // --- Poincaré Plot Analysis ---

    /**
     * Compute Poincaré plot descriptors SD1 and SD2.
     *
     * The Poincaré plot graphs each RR interval against the previous one.
     * - SD1: standard deviation perpendicular to the line of identity
     *   (short-term beat-to-beat variability, mathematically = RMSSD / √2)
     * - SD2: standard deviation along the line of identity
     *   (combined short- and long-term variability)
     * - SD1/SD2: reflects the ratio of short-term to total variability
     *
     * @param rrIntervals RR intervals in ms (minimum 10)
     * @return Pair of (SD1, SD2) in ms
     */
    fun poincare(rrIntervals: List<Double>): Pair<Double, Double> {
        if (rrIntervals.size < 10) return Pair(0.0, 0.0)

        val n = rrIntervals.size - 1

        // Successive differences and successive sums
        var sumDiffSq = 0.0
        var sumSumSq = 0.0
        val meanRr = rrIntervals.average()

        for (i in 0 until n) {
            val diff = rrIntervals[i + 1] - rrIntervals[i]
            val sum = rrIntervals[i + 1] + rrIntervals[i] - 2 * meanRr

            sumDiffSq += diff * diff
            sumSumSq += sum * sum
        }

        // SD1 = SD of points projected onto the line perpendicular to identity
        val sd1 = sqrt(sumDiffSq / (2.0 * n))

        // SD2 = SD of points projected onto the line of identity
        val sd2 = sqrt(sumSumSq / (2.0 * n))

        return Pair(sd1, sd2)
    }

    // --- Detrended Fluctuation Analysis (DFA) ---

    /**
     * Compute DFA alpha1 (short-term scaling exponent).
     *
     * DFA quantifies the fractal-like scaling properties of the RR time series.
     * - alpha1 ≈ 1.0: healthy fractal scaling (complex, adaptive)
     * - alpha1 > 1.5: strong correlations (less complex, potentially pathological)
     * - alpha1 < 0.5: anti-correlated (random-like)
     *
     * During resonance frequency breathing, alpha1 typically decreases
     * (the strong periodic breathing overrides natural fractal dynamics).
     *
     * @param rrIntervals RR intervals in ms (minimum 50 for reliable estimate)
     * @param minBox Minimum box size (default 4 beats)
     * @param maxBox Maximum box size (default 16 beats for short-term alpha1)
     * @return DFA alpha1 scaling exponent
     */
    fun dfaAlpha1(
        rrIntervals: List<Double>,
        minBox: Int = 4,
        maxBox: Int = 16
    ): Double {
        if (rrIntervals.size < maxBox * 4) return 0.0

        val n = rrIntervals.size
        val mean = rrIntervals.average()

        // Step 1: Integrate the time series (cumulative sum of deviations from mean)
        val integrated = DoubleArray(n)
        integrated[0] = rrIntervals[0] - mean
        for (i in 1 until n) {
            integrated[i] = integrated[i - 1] + (rrIntervals[i] - mean)
        }

        // Step 2: Divide into boxes of logarithmically spaced sizes.
        // Log-spacing ensures even distribution in log-log regression space,
        // which is critical for an unbiased scaling exponent estimate.
        // Standard DFA implementations (PhysioNet, Kubios) use log-spaced scales.
        val boxSizes = mutableListOf<Int>()
        val logMin = ln(minBox.toDouble())
        val logMax = ln(maxBox.toDouble())
        val numScales = maxOf(8, maxBox - minBox + 1) // At least 8 scales
        for (i in 0 until numScales) {
            val logS = logMin + (logMax - logMin) * i / (numScales - 1)
            val s = kotlin.math.exp(logS).toInt()
            if (s >= minBox && s <= maxBox && (boxSizes.isEmpty() || s != boxSizes.last())) {
                boxSizes.add(s)
            }
        }

        if (boxSizes.size < 3) return 0.0

        val logN = mutableListOf<Double>()
        val logF = mutableListOf<Double>()

        for (boxSize in boxSizes) {
            val numBoxes = n / boxSize
            if (numBoxes < 1) continue

            var totalRms = 0.0
            var boxCount = 0

            for (b in 0 until numBoxes) {
                val start = b * boxSize
                val end = start + boxSize

                // Linear detrend within the box (least-squares fit)
                var sumX = 0.0; var sumY = 0.0; var sumXY = 0.0; var sumXX = 0.0
                for (i in start until end) {
                    val x = (i - start).toDouble()
                    sumX += x
                    sumY += integrated[i]
                    sumXY += x * integrated[i]
                    sumXX += x * x
                }

                val bsD = boxSize.toDouble()
                val denom = bsD * sumXX - sumX * sumX
                if (denom == 0.0) continue

                val slope = (bsD * sumXY - sumX * sumY) / denom
                val intercept = (sumY - slope * sumX) / bsD

                // RMS of detrended values
                var sumSqDev = 0.0
                for (i in start until end) {
                    val trend = intercept + slope * (i - start).toDouble()
                    val dev = integrated[i] - trend
                    sumSqDev += dev * dev
                }

                totalRms += sumSqDev / boxSize
                boxCount++
            }

            if (boxCount > 0) {
                val rms = sqrt(totalRms / boxCount)
                if (rms <= 0 || !rms.isFinite()) continue
                logN.add(ln(boxSize.toDouble()))
                logF.add(ln(rms.coerceAtLeast(1e-10)))
            }
        }

        // Step 3: Linear regression in log-log space to get scaling exponent
        if (logN.size < 3) return 0.0
        return linearRegressionSlope(logN, logF)
    }

    // --- Sample Entropy ---

    /**
     * Compute Sample Entropy (SampEn).
     *
     * SampEn measures the regularity/predictability of the RR time series.
     * Lower values = more regular/predictable; higher values = more complex.
     *
     * Parameters follow Richman & Moorman (2000):
     * - m = 2 (template length)
     * - r = 0.2 * SDNN (tolerance)
     *
     * SampEn is more robust than Approximate Entropy (ApEn) for short time series
     * and doesn't count self-matches, eliminating the bias present in ApEn.
     *
     * @param rrIntervals RR intervals in ms (minimum 50)
     * @param m Template length (default 2)
     * @param rFraction Tolerance as fraction of SDNN (default 0.2)
     * @return Sample Entropy value
     */
    fun sampleEntropy(
        rrIntervals: List<Double>,
        m: Int = 2,
        rFraction: Double = 0.2
    ): Double {
        val n = rrIntervals.size
        if (n < 50) return 0.0

        // Calculate tolerance r
        val mean = rrIntervals.average()
        val sdnn = sqrt(rrIntervals.sumOf { (it - mean) * (it - mean) } / (n - 1))
        val r = rFraction * sdnn

        if (r == 0.0) return 0.0

        val data = rrIntervals.toDoubleArray()

        // Count template matches for length m and m+1
        val countM = countMatches(data, m, r)
        val countM1 = countMatches(data, m + 1, r)

        return if (countM > 0 && countM1 > 0) {
            -ln(countM1.toDouble() / countM.toDouble())
        } else {
            0.0 // Undefined: insufficient matches
        }
    }

    /**
     * Count the number of template matches of length m within tolerance r.
     * Does NOT count self-matches (SampEn, not ApEn).
     */
    private fun countMatches(data: DoubleArray, m: Int, r: Double): Int {
        val n = data.size
        var count = 0

        for (i in 0..n - m) {
            for (j in i + 1..n - m) {
                // Check if templates starting at i and j match within tolerance
                var match = true
                for (k in 0 until m) {
                    if (abs(data[i + k] - data[j + k]) > r) {
                        match = false
                        break
                    }
                }
                if (match) count++
            }
        }
        return count
    }

    // --- Compute All Nonlinear Metrics ---

    fun computeAll(rrIntervals: List<Double>): NonlinearMetrics {
        val (sd1, sd2) = poincare(rrIntervals)
        val dfa = dfaAlpha1(rrIntervals)
        val sampEn = sampleEntropy(rrIntervals)

        return NonlinearMetrics(
            sd1 = sd1,
            sd2 = sd2,
            sd1sd2Ratio = if (sd2 > 0) sd1 / sd2 else 0.0,
            dfaAlpha1 = dfa,
            sampleEntropy = sampEn
        )
    }

    // --- Utility ---

    private fun linearRegressionSlope(x: List<Double>, y: List<Double>): Double {
        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            num += (x[i] - meanX) * (y[i] - meanY)
            den += (x[i] - meanX) * (x[i] - meanX)
        }

        return if (den > 0) num / den else 0.0
    }
}
