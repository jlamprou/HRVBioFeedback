package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Extracts a respiratory signal from Polar H10 sensor data.
 *
 * Two independent methods:
 * 1. Accelerometer-derived respiration (ADR): Z-axis of chest-mounted ACC
 *    directly measures anterior-posterior chest wall displacement during breathing.
 * 2. ECG-derived respiration (EDR): R-wave amplitude modulation tracks
 *    thoracic impedance changes caused by lung volume changes.
 *
 * Both signals are bandpass filtered to the respiratory band (0.05-0.5 Hz)
 * to isolate breathing from motion artifacts and cardiac interference.
 *
 * References:
 * - Charlton et al. (2016) "Breathing Rate Estimation From the ECG"
 * - Schafer & Kratky (2008) "Estimation of Breathing Rate from Respiratory
 *   Sinus Arrhythmia"
 */
class RespiratorySignalExtractor @Inject constructor() {

    companion object {
        // Respiratory bandpass filter bounds
        const val RESP_LOW_HZ = 0.05   // ~3 breaths/min (below RF range)
        const val RESP_HIGH_HZ = 0.5   // ~30 breaths/min (above normal range)

        // Target output rate for respiratory signal (sufficient for breathing frequencies)
        const val RESP_OUTPUT_RATE = 10.0 // Hz

        // EDR processing
        const val ECG_SAMPLE_RATE = 130.0 // Hz (Polar H10 ECG rate)
    }

    // --- Accelerometer-Derived Respiration (ADR) ---

    /**
     * Extract respiratory signal from accelerometer z-axis data.
     * The z-axis (anterior-posterior) on a chest-mounted sensor captures
     * thoracic expansion/contraction during breathing.
     *
     * Handles any ACC sample rate (25/50/100/200 Hz) by dynamically computing
     * the downsample factor to reach ~10 Hz output rate.
     *
     * @param accZ Array of z-axis accelerometer values (mg)
     * @param sampleRate Actual ACC sampling rate in Hz (varies: Polar H10 maxSettings() returns 200 Hz)
     * @return Filtered respiratory signal (arbitrary units) at ~10 Hz
     */
    fun extractFromAcc(accZ: DoubleArray, sampleRate: Double = 200.0): RespiratorySignal {
        if (accZ.size < 50) return RespiratorySignal(doubleArrayOf(), sampleRate)

        // Step 1: Remove DC offset (gravity component)
        val mean = accZ.average()
        val centered = DoubleArray(accZ.size) { accZ[it] - mean }

        // Step 2: Bandpass filter to respiratory band (0.05-0.5 Hz)
        val filtered = bandpassFilter(centered, sampleRate, RESP_LOW_HZ, RESP_HIGH_HZ)

        // Step 3: Downsample to ~10 Hz (sufficient for respiratory frequencies)
        // Dynamic factor handles any input rate (50, 100, 200 Hz)
        val downsampleFactor = maxOf(1, (sampleRate / RESP_OUTPUT_RATE).toInt())
        val downsampled = downsample(filtered, downsampleFactor)
        val outputRate = sampleRate / downsampleFactor

        return RespiratorySignal(downsampled, outputRate)
    }

    // --- ECG-Derived Respiration (EDR) ---

    /**
     * Extract respiratory signal from ECG using R-wave amplitude modulation.
     *
     * Respiration changes thoracic impedance, which modulates the amplitude
     * of the QRS complex. By tracking beat-to-beat R-peak amplitude and
     * interpolating to a uniform time grid, we obtain a respiratory signal.
     *
     * @param ecgVoltages Array of ECG voltages (uV) at 130 Hz
     * @param rPeakIndices Indices of detected R-peaks in the ECG signal
     * @param sampleRate ECG sample rate (130 Hz)
     * @return Respiratory signal derived from R-wave amplitude modulation
     */
    fun extractFromEcg(
        ecgVoltages: DoubleArray,
        rPeakIndices: List<Int>,
        sampleRate: Double = ECG_SAMPLE_RATE
    ): RespiratorySignal {
        if (rPeakIndices.size < 5) return RespiratorySignal(doubleArrayOf(), 4.0)

        // Step 1: Extract R-peak amplitudes
        val amplitudes = rPeakIndices.map { idx ->
            if (idx in ecgVoltages.indices) ecgVoltages[idx] else 0.0
        }

        // Step 2: Create time series of R-peak amplitudes
        val times = rPeakIndices.map { it / sampleRate }

        // Step 3: Interpolate to 4 Hz uniform grid
        val outputRate = 4.0
        val tStart = times.first()
        val tEnd = times.last()
        val numSamples = ((tEnd - tStart) * outputRate).toInt()

        if (numSamples < 10) return RespiratorySignal(doubleArrayOf(), outputRate)

        // Linear interpolation (adequate for the smooth respiratory envelope)
        val interpolated = DoubleArray(numSamples)
        var j = 0
        for (i in 0 until numSamples) {
            val t = tStart + i / outputRate
            while (j < times.size - 2 && times[j + 1] < t) j++
            val t0 = times[j]
            val t1 = times[j + 1]
            val frac = if (t1 > t0) (t - t0) / (t1 - t0) else 0.0
            interpolated[i] = amplitudes[j] + frac * (amplitudes[j + 1] - amplitudes[j])
        }

        // Step 4: Bandpass filter to respiratory band
        val filtered = bandpassFilter(interpolated, outputRate, RESP_LOW_HZ, RESP_HIGH_HZ)

        return RespiratorySignal(filtered, outputRate)
    }

    // --- R-Peak Detection ---

    /**
     * Simple R-peak detection from raw ECG using threshold-based approach.
     * Uses a modified Pan-Tompkins-style algorithm:
     * 1. Bandpass filter (5-15 Hz) to isolate QRS
     * 2. Square the signal to emphasize peaks
     * 3. Moving average for envelope detection
     * 4. Threshold-based peak detection with refractory period
     *
     * @param ecgVoltages Raw ECG in microvolts at 130 Hz
     * @return List of R-peak indices
     */
    fun detectRPeaks(ecgVoltages: DoubleArray, sampleRate: Double = ECG_SAMPLE_RATE): List<Int> {
        if (ecgVoltages.size < 50) return emptyList()

        // Bandpass 5-15 Hz to isolate QRS complex
        val filtered = bandpassFilter(ecgVoltages, sampleRate, 5.0, 15.0)

        // Square to emphasize peaks
        val squared = DoubleArray(filtered.size) { filtered[it] * filtered[it] }

        // Moving average (150ms window at 130Hz = ~20 samples)
        val windowSize = (0.150 * sampleRate).toInt().coerceAtLeast(3)
        val envelope = movingAverage(squared, windowSize)

        // Adaptive threshold: 60% of the running maximum
        val peaks = mutableListOf<Int>()
        val refractoryPeriod = (0.200 * sampleRate).toInt() // 200ms minimum between beats
        var lastPeak = -refractoryPeriod

        // Adaptive threshold using running maximum (no array allocation)
        val thresholdWindow = (2.0 * sampleRate).toInt() // 2-second window
        var runningMax = 0.0
        for (i in 1 until envelope.size - 1) {
            // Update running max: check new sample entering window and decay
            if (envelope[i] > runningMax) {
                runningMax = envelope[i]
            } else if (i > thresholdWindow) {
                // If the old max left the window, rescan (rare)
                val windowStart = i - thresholdWindow
                if (runningMax <= envelope[windowStart]) {
                    runningMax = 0.0
                    for (j in windowStart..i) {
                        if (envelope[j] > runningMax) runningMax = envelope[j]
                    }
                }
            }
            val threshold = runningMax * 0.4

            if (envelope[i] > threshold &&
                envelope[i] > envelope[i - 1] &&
                envelope[i] >= envelope[i + 1] &&
                i - lastPeak >= refractoryPeriod
            ) {
                // Refine to exact R-peak location in original signal
                val searchStart = maxOf(0, i - windowSize / 2)
                val searchEnd = minOf(ecgVoltages.size - 1, i + windowSize / 2)
                var maxIdx = searchStart
                var maxVal = ecgVoltages[searchStart]
                for (j in searchStart..searchEnd) {
                    if (ecgVoltages[j] > maxVal) {
                        maxVal = ecgVoltages[j]
                        maxIdx = j
                    }
                }
                peaks.add(maxIdx)
                lastPeak = i
            }
        }

        return peaks
    }

    // --- Breathing Rate Estimation ---

    /**
     * Estimate breathing rate from a respiratory signal.
     * Uses the dominant peak in the spectral analysis of the respiratory signal.
     *
     * @param signal Respiratory signal
     * @return Estimated breathing rate in breaths per minute, or 0 if insufficient data
     */
    fun estimateBreathingRate(signal: RespiratorySignal): Double {
        if (signal.data.size < 20) return 0.0

        // Simple approach: count zero-crossings and convert to rate
        val mean = signal.data.average()
        var crossings = 0
        for (i in 1 until signal.data.size) {
            if ((signal.data[i - 1] - mean) * (signal.data[i] - mean) < 0) {
                crossings++
            }
        }

        val durationSeconds = signal.data.size / signal.sampleRate
        // Each full breathing cycle has 2 zero crossings
        val breathsPerSecond = crossings / (2.0 * durationSeconds)
        return breathsPerSecond * 60.0
    }

    // --- Signal Processing Utilities ---

    /**
     * 4th-order Butterworth-style bandpass filter using cascaded biquad sections.
     * Implemented as forward-backward filtering (zero-phase) using 2nd-order sections.
     */
    private fun bandpassFilter(
        data: DoubleArray,
        sampleRate: Double,
        lowFreq: Double,
        highFreq: Double
    ): DoubleArray {
        // Apply highpass then lowpass
        val highpassed = biquadHighpass(data, sampleRate, lowFreq)
        return biquadLowpass(highpassed, sampleRate, highFreq)
    }

    /**
     * 2nd-order IIR lowpass filter (Butterworth approximation).
     * Applied forward and backward for zero-phase response.
     */
    private fun biquadLowpass(data: DoubleArray, sampleRate: Double, cutoff: Double): DoubleArray {
        val omega = 2.0 * PI * cutoff / sampleRate
        val alpha = sin(omega) / (2.0 * 0.7071) // Q = 0.7071 for Butterworth

        val a0 = 1.0 + alpha
        val b0 = ((1.0 - cos(omega)) / 2.0) / a0
        val b1 = (1.0 - cos(omega)) / a0
        val b2 = b0
        val a1 = (-2.0 * cos(omega)) / a0
        val a2 = (1.0 - alpha) / a0

        // Forward pass
        val forward = applyBiquad(data, b0, b1, b2, a1, a2)
        // Backward pass (reverse, filter, reverse)
        val reversed = forward.reversedArray()
        val backward = applyBiquad(reversed, b0, b1, b2, a1, a2)
        return backward.reversedArray()
    }

    /**
     * 2nd-order IIR highpass filter (Butterworth approximation).
     * Applied forward and backward for zero-phase response.
     */
    private fun biquadHighpass(data: DoubleArray, sampleRate: Double, cutoff: Double): DoubleArray {
        val omega = 2.0 * PI * cutoff / sampleRate
        val alpha = sin(omega) / (2.0 * 0.7071)

        val a0 = 1.0 + alpha
        val b0 = ((1.0 + cos(omega)) / 2.0) / a0
        val b1 = (-(1.0 + cos(omega))) / a0
        val b2 = b0
        val a1 = (-2.0 * cos(omega)) / a0
        val a2 = (1.0 - alpha) / a0

        val forward = applyBiquad(data, b0, b1, b2, a1, a2)
        val reversed = forward.reversedArray()
        val backward = applyBiquad(reversed, b0, b1, b2, a1, a2)
        return backward.reversedArray()
    }

    private fun applyBiquad(
        data: DoubleArray,
        b0: Double, b1: Double, b2: Double,
        a1: Double, a2: Double
    ): DoubleArray {
        val output = DoubleArray(data.size)
        var x1 = 0.0; var x2 = 0.0
        var y1 = 0.0; var y2 = 0.0

        for (i in data.indices) {
            val x0 = data[i]
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            output[i] = y0
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
        }
        return output
    }

    private fun downsample(data: DoubleArray, factor: Int): DoubleArray {
        return DoubleArray(data.size / factor) { i -> data[i * factor] }
    }

    private fun movingAverage(data: DoubleArray, windowSize: Int): DoubleArray {
        val result = DoubleArray(data.size)
        val half = windowSize / 2
        for (i in data.indices) {
            val start = maxOf(0, i - half)
            val end = minOf(data.size - 1, i + half)
            var sum = 0.0
            for (j in start..end) sum += data[j]
            result[i] = sum / (end - start + 1)
        }
        return result
    }
}

/**
 * A respiratory signal with its sample rate.
 */
data class RespiratorySignal(
    val data: DoubleArray,
    val sampleRate: Double
)
