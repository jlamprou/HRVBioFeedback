package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Autoregressive (AR) spectral analysis using Burg's method.
 * Standard in HRV analysis per the 1996 Task Force guidelines.
 * Order 16 at 4 Hz sampling provides good frequency resolution in the LF band.
 */
data class PowerSpectralDensity(
    val frequencies: DoubleArray,
    val power: DoubleArray,
    val sampleRate: Double
)

class ArSpectralAnalyzer @Inject constructor() {

    companion object {
        const val DEFAULT_ORDER = 16
        const val DEFAULT_NFFT = 512
    }

    /**
     * Estimate AR coefficients using Burg's method.
     * Burg's method guarantees a stable model and works well with short segments.
     *
     * @param data Input time series (mean-removed)
     * @param order AR model order
     * @return Pair of (AR coefficients, prediction error power)
     */
    fun burgMethod(data: DoubleArray, order: Int = DEFAULT_ORDER): Pair<DoubleArray, Double> {
        val n = data.size
        require(n > order) { "Data length must exceed model order" }

        val arCoeffs = DoubleArray(order)
        var errorPower = 0.0
        for (v in data) errorPower += v * v
        errorPower /= n

        // Forward and backward prediction errors
        var ef = data.copyOf()
        var eb = data.copyOf()

        for (m in 0 until order) {
            // Compute reflection coefficient
            var num = 0.0
            var den = 0.0
            for (j in m + 1 until n) {
                num += ef[j] * eb[j - 1]
                den += ef[j] * ef[j] + eb[j - 1] * eb[j - 1]
            }

            if (den == 0.0) break

            val k = -2.0 * num / den

            // Check stability (reflection coefficient magnitude must be < 1)
            if (kotlin.math.abs(k) >= 1.0) break

            // Update AR coefficients
            val newCoeffs = DoubleArray(m + 1)
            newCoeffs[m] = k
            for (i in 0 until m) {
                newCoeffs[i] = arCoeffs[i] + k * arCoeffs[m - 1 - i]
            }
            for (i in 0..m) {
                arCoeffs[i] = newCoeffs[i]
            }

            // Update error power
            errorPower *= (1.0 - k * k)

            // Update forward and backward prediction errors
            val efNew = DoubleArray(n)
            val ebNew = DoubleArray(n)
            for (j in m + 1 until n) {
                efNew[j] = ef[j] + k * eb[j - 1]
                ebNew[j] = eb[j - 1] + k * ef[j]
            }
            ef = efNew
            eb = ebNew
        }

        return Pair(arCoeffs, errorPower)
    }

    /**
     * Compute power spectral density from AR coefficients.
     *
     * @param arCoeffs AR model coefficients
     * @param errorPower Prediction error power
     * @param sampleRate Sampling rate in Hz
     * @param nfft Number of frequency bins (resolution)
     * @return PowerSpectralDensity with frequencies and power values
     */
    fun computePsd(
        arCoeffs: DoubleArray,
        errorPower: Double,
        sampleRate: Double,
        nfft: Int = DEFAULT_NFFT
    ): PowerSpectralDensity {
        val frequencies = DoubleArray(nfft / 2 + 1) { i ->
            i.toDouble() * sampleRate / nfft
        }
        val power = DoubleArray(frequencies.size)

        for (i in frequencies.indices) {
            val freq = frequencies[i]
            val omega = 2.0 * PI * freq / sampleRate

            // H(f) = 1 / (1 + sum(a[k] * e^(-j*k*omega)))
            var realDenom = 1.0
            var imagDenom = 0.0
            for (k in arCoeffs.indices) {
                realDenom += arCoeffs[k] * cos((k + 1) * omega)
                imagDenom += arCoeffs[k] * (-sin((k + 1) * omega))
            }

            val denomMagSquared = realDenom * realDenom + imagDenom * imagDenom
            // PSD = errorPower / (sampleRate * |H(f)|^2)
            val p = if (denomMagSquared > 1e-15) {
                errorPower / (sampleRate * denomMagSquared)
            } else {
                0.0
            }
            // Guard against NaN/Infinity from numerical instability
            power[i] = if (p.isFinite()) p else 0.0
        }

        return PowerSpectralDensity(frequencies, power, sampleRate)
    }

    /**
     * Full pipeline: data -> AR model -> PSD
     */
    fun analyze(
        data: DoubleArray,
        sampleRate: Double,
        order: Int = DEFAULT_ORDER,
        nfft: Int = DEFAULT_NFFT
    ): PowerSpectralDensity {
        require(data.size > order) { "Data length (${data.size}) must exceed model order ($order)" }

        // Remove mean
        val mean = data.average()
        if (!mean.isFinite()) {
            return PowerSpectralDensity(doubleArrayOf(), doubleArrayOf(), sampleRate)
        }
        val centered = DoubleArray(data.size) { data[it] - mean }

        val (coeffs, errorPower) = burgMethod(centered, order)
        if (!errorPower.isFinite() || errorPower <= 0) {
            return PowerSpectralDensity(doubleArrayOf(), doubleArrayOf(), sampleRate)
        }
        return computePsd(coeffs, errorPower, sampleRate, nfft)
    }
}
