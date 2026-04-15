package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Computes magnitude-squared coherence (MSC) between two signals.
 *
 * MSC quantifies the linear relationship between two signals at each frequency.
 * For HRV biofeedback, we compute coherence between:
 * - Heart rate (instantaneous HR from RR intervals)
 * - Respiratory signal (from accelerometer or EDR)
 *
 * At resonance frequency, MSC between HR and breathing should be near 1.0,
 * indicating strong cardiorespiratory coupling.
 *
 * Method: Welch's averaged periodogram with Hanning window.
 * MSC(f) = |Pxy(f)|^2 / (Pxx(f) * Pyy(f))
 * where Pxy is the cross-spectral density, Pxx and Pyy are auto-spectral densities.
 *
 * References:
 * - Faes et al. (2004) "Surrogate data analysis for assessing the significance
 *   of the coherence function"
 * - Shaffer et al. (2020): phase synchrony as criterion #1 for RF assessment
 */
class CrossSpectralAnalyzer @Inject constructor() {

    data class CoherenceSpectrum(
        val frequencies: DoubleArray,
        val coherence: DoubleArray,    // MSC at each frequency (0-1)
        val phase: DoubleArray,        // Cross-spectral phase at each frequency (radians)
        val sampleRate: Double
    )

    data class CardiorespiratoryCoupling(
        val coherenceAtBreathingFreq: Double,  // MSC at the breathing frequency (0-1)
        val phaseAtBreathingFreq: Double,      // Phase difference at breathing frequency (degrees)
        val peakCoherenceFreq: Double,         // Frequency of highest coherence in LF band (Hz)
        val peakCoherence: Double,             // Maximum coherence value in LF band
        val averageLfCoherence: Double         // Average coherence across LF band
    )

    /**
     * Compute magnitude-squared coherence between two signals using Welch's method.
     *
     * @param signalX First signal (e.g., instantaneous HR)
     * @param signalY Second signal (e.g., respiratory signal)
     * @param sampleRate Sample rate of both signals (must be equal)
     * @param segmentLength Length of each Welch segment in samples (default: 256)
     * @param overlap Overlap fraction between segments (default: 0.5)
     * @return CoherenceSpectrum with MSC and phase at each frequency
     */
    fun computeCoherence(
        signalX: DoubleArray,
        signalY: DoubleArray,
        sampleRate: Double,
        segmentLength: Int = 256,
        overlap: Double = 0.5
    ): CoherenceSpectrum {
        require(signalX.size == signalY.size) { "Signals must have equal length" }

        val n = signalX.size
        val step = (segmentLength * (1.0 - overlap)).toInt().coerceAtLeast(1)
        val nfft = segmentLength
        val freqBins = nfft / 2 + 1

        // Hanning window
        val window = DoubleArray(segmentLength) { i ->
            0.5 * (1.0 - cos(2.0 * PI * i / (segmentLength - 1)))
        }

        // Accumulate cross-spectral and auto-spectral estimates
        val pxxReal = DoubleArray(freqBins)
        val pxxImag = DoubleArray(freqBins) // Always 0 for auto-spectrum
        val pyyReal = DoubleArray(freqBins)
        val pxyReal = DoubleArray(freqBins)
        val pxyImag = DoubleArray(freqBins)
        var segmentCount = 0

        // Precompute means once (not per segment — O(N) vs O(N*segments))
        val xMean = signalX.average()
        val yMean = signalY.average()

        var offset = 0
        while (offset + segmentLength <= n) {
            // Apply window and remove mean
            val xWindowed = DoubleArray(segmentLength) { i ->
                (signalX[offset + i] - xMean) * window[i]
            }
            val yWindowed = DoubleArray(segmentLength) { i ->
                (signalY[offset + i] - yMean) * window[i]
            }

            // DFT (not FFT — acceptable for segment sizes up to 256)
            for (k in 0 until freqBins) {
                var xr = 0.0; var xi = 0.0
                var yr = 0.0; var yi = 0.0

                for (j in 0 until segmentLength) {
                    val angle = 2.0 * PI * k * j / nfft
                    val cosA = cos(angle)
                    val sinA = sin(angle)
                    xr += xWindowed[j] * cosA
                    xi -= xWindowed[j] * sinA
                    yr += yWindowed[j] * cosA
                    yi -= yWindowed[j] * sinA
                }

                // Auto-spectral densities: Pxx = |X|^2, Pyy = |Y|^2
                pxxReal[k] += xr * xr + xi * xi
                pyyReal[k] += yr * yr + yi * yi

                // Cross-spectral density: Pxy = X* * Y (conjugate of X times Y)
                pxyReal[k] += xr * yr + xi * yi  // Re(X* * Y)
                pxyImag[k] += xr * yi - xi * yr  // Im(X* * Y) -- note: X* flips sign of xi
            }
            segmentCount++
            offset += step
        }

        if (segmentCount == 0) {
            return CoherenceSpectrum(doubleArrayOf(), doubleArrayOf(), doubleArrayOf(), sampleRate)
        }

        // Compute MSC and phase
        val frequencies = DoubleArray(freqBins) { k -> k * sampleRate / nfft }
        val coherence = DoubleArray(freqBins)
        val phase = DoubleArray(freqBins)

        for (k in 0 until freqBins) {
            val pxx = pxxReal[k]
            val pyy = pyyReal[k]
            val pxyR = pxyReal[k]
            val pxyI = pxyImag[k]

            // MSC = |Pxy|^2 / (Pxx * Pyy)
            val pxyMagSq = pxyR * pxyR + pxyI * pxyI
            val denom = pxx * pyy

            coherence[k] = if (denom > 0) (pxyMagSq / denom).coerceIn(0.0, 1.0) else 0.0

            // Phase = atan2(Im(Pxy), Re(Pxy))
            phase[k] = kotlin.math.atan2(pxyI, pxyR)
        }

        return CoherenceSpectrum(frequencies, coherence, phase, sampleRate)
    }

    /**
     * Extract cardiorespiratory coupling metrics at the breathing frequency.
     *
     * @param spectrum Coherence spectrum from computeCoherence()
     * @param breathingRateHz Target breathing frequency in Hz
     * @return CardiorespiratoryCoupling with coherence and phase at breathing freq
     */
    fun extractCouplingMetrics(
        spectrum: CoherenceSpectrum,
        breathingRateHz: Double
    ): CardiorespiratoryCoupling {
        if (spectrum.frequencies.isEmpty()) {
            return CardiorespiratoryCoupling(0.0, 0.0, 0.0, 0.0, 0.0)
        }

        // Find the frequency bin closest to the breathing frequency
        var closestIdx = 0
        var minDist = Double.MAX_VALUE
        for (i in spectrum.frequencies.indices) {
            val dist = kotlin.math.abs(spectrum.frequencies[i] - breathingRateHz)
            if (dist < minDist) {
                minDist = dist
                closestIdx = i
            }
        }

        val coherenceAtBreathing = spectrum.coherence[closestIdx]
        val phaseAtBreathing = Math.toDegrees(spectrum.phase[closestIdx])

        // Find peak coherence in LF band (0.04-0.15 Hz)
        var peakCoherence = 0.0
        var peakFreq = 0.0
        var lfSum = 0.0
        var lfCount = 0

        for (i in spectrum.frequencies.indices) {
            val f = spectrum.frequencies[i]
            if (f in 0.04..0.15) {
                lfCount++
                lfSum += spectrum.coherence[i]
                if (spectrum.coherence[i] > peakCoherence) {
                    peakCoherence = spectrum.coherence[i]
                    peakFreq = f
                }
            }
        }

        val avgLfCoherence = if (lfCount > 0) lfSum / lfCount else 0.0

        return CardiorespiratoryCoupling(
            coherenceAtBreathingFreq = coherenceAtBreathing,
            phaseAtBreathingFreq = phaseAtBreathing,
            peakCoherenceFreq = peakFreq,
            peakCoherence = peakCoherence,
            averageLfCoherence = avgLfCoherence
        )
    }
}
