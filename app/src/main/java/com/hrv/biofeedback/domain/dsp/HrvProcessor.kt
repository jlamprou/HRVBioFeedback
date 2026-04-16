package com.hrv.biofeedback.domain.dsp

import com.hrv.biofeedback.domain.model.HrvMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Orchestrates the full HRV signal processing pipeline:
 * RR intervals -> artifact detection -> interpolation -> resampling ->
 * spectral analysis -> metrics (time, frequency, nonlinear, respiratory)
 *
 * Thread-safe: all public methods use a mutex to protect shared mutable state,
 * since processRrInterval(), addAccSample(), and addEcgSample() are called
 * from separate coroutine flows (HR, ACC, ECG streams).
 */
class HrvProcessor @Inject constructor(
    private val artifactDetector: ArtifactDetector,
    private val splineInterpolator: CubicSplineInterpolator,
    private val spectralAnalyzer: ArSpectralAnalyzer,
    private val metricsCalculator: HrvMetricsCalculator,
    private val coherenceCalculator: CoherenceCalculator,
    private val peakTroughAnalyzer: PeakTroughAnalyzer,
    private val nonlinearAnalyzer: NonlinearAnalyzer,
    private val crossSpectralAnalyzer: CrossSpectralAnalyzer,
    private val respiratoryExtractor: RespiratorySignalExtractor,
    val signalQuality: SignalQualityMonitor
) {

    companion object {
        const val SAMPLE_RATE = 4.0 // Hz for resampled data
        const val MIN_DATA_SECONDS = 60.0
        const val WINDOW_SECONDS = 120.0
        // All metrics run every heartbeat for maximum accuracy.
    }

    // Buffer of corrected RR intervals with cumulative timestamps
    // Thread safety: ACC and ECG buffers use synchronized blocks since they're
    // written from separate coroutine flows. RR buffer is only written from processRrInterval.
    private val rrBuffer = mutableListOf<Double>()
    private val timeBuffer = mutableListOf<Double>()
    private var cumulativeTime = 0.0

    // Accelerometer buffer for respiratory signal
    private val accZBuffer = mutableListOf<Double>()

    // ECG buffer for R-peak detection and ECG-derived respiration
    private val ecgBuffer = mutableListOf<Double>()
    private var ecgRPeakIndices = listOf<Int>()

    private val _metrics = MutableStateFlow(HrvMetrics())
    val metrics: StateFlow<HrvMetrics> = _metrics.asStateFlow()

    // Expose latest PSD for assessment scoring (criterion 6: LF peak count)
    @Volatile
    private var _latestPsd: PowerSpectralDensity? = null
    val latestPsd: PowerSpectralDensity? get() = _latestPsd

    // Latest resampled instantaneous HR for phase analysis
    @Volatile
    private var _latestInstantaneousHr: DoubleArray? = null
    val latestInstantaneousHr: DoubleArray? get() = _latestInstantaneousHr

    // Store all RR intervals for the session
    private val _allRrIntervals = mutableListOf<Pair<Long, Double>>()
    val allRrIntervals: List<Pair<Long, Double>> get() = synchronized(_allRrIntervals) { _allRrIntervals.toList() }

    private val _allMetricsSnapshots = mutableListOf<HrvMetrics>()
    val allMetricsSnapshots: List<HrvMetrics> get() = synchronized(_allMetricsSnapshots) { _allMetricsSnapshots.toList() }

    @Volatile
    private var currentBreathingRate: Double = 6.0
    private var rrCount = 0

    // Cached nonlinear metrics (updated less frequently)
    @Volatile private var cachedSd1 = 0.0
    @Volatile private var cachedSd2 = 0.0
    @Volatile private var cachedDfaAlpha1 = 0.0
    @Volatile private var cachedSampleEntropy = 0.0

    // Cached respiratory coupling metrics
    @Volatile private var cachedBreathingRate = 0.0
    @Volatile private var cachedCardiorespCoherence = 0.0
    @Volatile private var cachedCardiorespPhase = 0.0

    fun setBreathingRate(rate: Double) {
        currentBreathingRate = rate
    }

    /**
     * Add accelerometer z-axis sample for respiratory signal extraction.
     * Called from the ACC coroutine stream — thread-safe via synchronized block.
     */
    fun addAccSample(zValue: Double) {
        synchronized(accZBuffer) {
            accZBuffer.add(zValue)
            // Keep last 60 seconds at 200 Hz (maxSettings) = 12000 samples
            if (accZBuffer.size > 12000) {
                accZBuffer.removeAt(0)
            }
        }
    }

    /**
     * Add raw ECG sample for R-peak detection and ECG-derived respiration.
     * Called from the ECG coroutine stream — thread-safe via synchronized block.
     */
    fun addEcgSample(voltageUv: Int) {
        val buffer: DoubleArray
        synchronized(ecgBuffer) {
            ecgBuffer.add(voltageUv.toDouble())
            if (ecgBuffer.size > 7800) { // 60 seconds at 130 Hz
                ecgBuffer.removeAt(0)
            }
            // Snapshot for R-peak detection (every ~2 seconds)
            if (ecgBuffer.size % 260 != 0 || ecgBuffer.size < 1300) return
            buffer = ecgBuffer.toDoubleArray()
        }
        try {
            ecgRPeakIndices = respiratoryExtractor.detectRPeaks(buffer, 130.0)
        } catch (_: Exception) { }
    }

    fun reset() {
        synchronized(accZBuffer) { accZBuffer.clear() }
        synchronized(ecgBuffer) { ecgBuffer.clear() }
        synchronized(_allRrIntervals) { _allRrIntervals.clear() }
        synchronized(_allMetricsSnapshots) { _allMetricsSnapshots.clear() }
        signalQuality.reset()
        rrBuffer.clear()
        timeBuffer.clear()
        ecgRPeakIndices = emptyList()
        cumulativeTime = 0.0
        rrCount = 0
        _latestPsd = null
        _latestInstantaneousHr = null
        _metrics.value = HrvMetrics()
        cachedSd1 = 0.0; cachedSd2 = 0.0
        cachedDfaAlpha1 = 0.0; cachedSampleEntropy = 0.0
        cachedBreathingRate = 0.0; cachedCardiorespCoherence = 0.0
        cachedCardiorespPhase = 0.0
    }

    /**
     * Process a new RR interval. Call this for each RR interval received from the sensor.
     *
     * @param rrMs RR interval in milliseconds
     * @param timestamp Monotonic timestamp
     * @param contactDetected Whether the sensor has skin contact (from Polar H10)
     */
    fun processRrInterval(rrMs: Int, timestamp: Long, contactDetected: Boolean = true) {
        // Track contact status for quality monitoring.
        // NOTE: Polar H10 contactStatus is unreliable — it can report false even with
        // good electrode contact and valid RR data. So we use it as a quality WARNING
        // (shown to user via SignalQualityBar) but do NOT reject data based on it.
        // If RR intervals are arriving, the sensor is working regardless of the flag.
        signalQuality.recordBeat(timestamp, isArtifact = false, contactDetected = contactDetected)

        val rr = rrMs.toDouble()
        rrCount++

        // Artifact detection
        val result = artifactDetector.detect(rr, rrBuffer.takeLast(10))
        val correctedRr = result.correctedRr

        // Track signal quality
        signalQuality.recordBeat(timestamp, result.isArtifact, contactDetected = true)

        // Store raw data
        synchronized(_allRrIntervals) { _allRrIntervals.add(Pair(timestamp, rr)) }

        // Add to sliding window buffer
        cumulativeTime += correctedRr / 1000.0
        rrBuffer.add(correctedRr)
        timeBuffer.add(cumulativeTime)
        trimBuffer()

        // Time-domain metrics (always available)
        val currentHr = if (correctedRr > 0) (60000.0 / correctedRr).toInt() else 0
        val rmssd = metricsCalculator.calculateRmssd(rrBuffer)
        val sdnn = metricsCalculator.calculateSdnn(rrBuffer)
        val pnn50 = metricsCalculator.calculatePnn50(rrBuffer)

        // Frequency-domain metrics require minimum data
        val totalDataSeconds = if (timeBuffer.size >= 2) {
            timeBuffer.last() - timeBuffer.first()
        } else 0.0

        var lfPower = 0.0
        var hfPower = 0.0
        var lfHfRatio = 0.0
        var totalPower = 0.0
        var coherenceScore = 0.0
        var peakFrequency = 0.0
        var peakTroughAmplitude = 0.0

        if (totalDataSeconds >= MIN_DATA_SECONDS && rrBuffer.size >= 20) {
            try {
                val (_, resampledValues) = splineInterpolator.resample(
                    timeBuffer.toDoubleArray(),
                    rrBuffer.toDoubleArray(),
                    SAMPLE_RATE
                )

                val minSamples = (MIN_DATA_SECONDS * SAMPLE_RATE).toInt()
                if (resampledValues.size >= minSamples) {
                    // AR spectral analysis (Burg order 16)
                    val psd = spectralAnalyzer.analyze(resampledValues, SAMPLE_RATE)
                    if (psd.frequencies.isEmpty()) throw IllegalStateException("PSD empty")
                    _latestPsd = psd

                    lfPower = metricsCalculator.calculateLfPower(psd)
                    hfPower = metricsCalculator.calculateHfPower(psd)
                    totalPower = metricsCalculator.calculateTotalPower(psd)
                    lfHfRatio = if (hfPower > 0) lfPower / hfPower else 0.0
                    peakFrequency = metricsCalculator.peakLfFrequency(psd)

                    // HeartMath coherence: peak/(total-peak)
                    val coherenceResult = coherenceCalculator.calculate(psd)
                    coherenceScore = coherenceResult.coherenceScore

                    // Peak-to-trough (local extrema detection)
                    val instantaneousHr = resampledValues.map { 60000.0 / it }.toDoubleArray()
                    _latestInstantaneousHr = instantaneousHr
                    peakTroughAmplitude = peakTroughAnalyzer.analyze(
                        instantaneousHr, SAMPLE_RATE, currentBreathingRate
                    )

                    // --- Respiratory signal extraction (ACC primary, ECG fallback) ---
                    var respSignal: RespiratorySignal? = null

                    // ACC-derived respiration (most reliable from chest strap z-axis)
                    val accSnapshot: DoubleArray?
                    synchronized(accZBuffer) {
                        accSnapshot = if (accZBuffer.size >= 2000) accZBuffer.toDoubleArray() else null
                    }
                    if (accSnapshot != null) {
                        try {
                            respSignal = respiratoryExtractor.extractFromAcc(accSnapshot, 200.0)
                            if (respSignal.data.size < 40) respSignal = null
                        } catch (_: Exception) { respSignal = null }
                    }

                    // ECG-derived respiration (fallback — R-wave amplitude modulation)
                    if (respSignal == null && ecgRPeakIndices.size >= 5) {
                        val ecgSnapshot: DoubleArray?
                        synchronized(ecgBuffer) {
                            ecgSnapshot = if (ecgBuffer.size >= 1300) ecgBuffer.toDoubleArray() else null
                        }
                        if (ecgSnapshot != null) {
                            try {
                                respSignal = respiratoryExtractor.extractFromEcg(
                                    ecgSnapshot, ecgRPeakIndices, 130.0
                                )
                                if (respSignal.data.size < 20) respSignal = null
                            } catch (_: Exception) { respSignal = null }
                        }
                    }

                    // Cross-spectral coherence between HR and respiratory signal
                    if (respSignal != null && respSignal.data.size >= 40) {
                        try {
                            val hrResampled = linearResampleHr(
                                instantaneousHr, SAMPLE_RATE,
                                respSignal.data.size, respSignal.sampleRate
                            )
                            if (hrResampled.size == respSignal.data.size && hrResampled.size >= 64) {
                                val coherenceSpectrum = crossSpectralAnalyzer.computeCoherence(
                                    hrResampled, respSignal.data, respSignal.sampleRate,
                                    segmentLength = minOf(64, hrResampled.size)
                                )
                                val coupling = crossSpectralAnalyzer.extractCouplingMetrics(
                                    coherenceSpectrum, currentBreathingRate / 60.0
                                )
                                cachedCardiorespCoherence = coupling.coherenceAtBreathingFreq
                                cachedCardiorespPhase = coupling.phaseAtBreathingFreq
                            }
                            cachedBreathingRate = respiratoryExtractor.estimateBreathingRate(respSignal)
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) {
                // DSP errors must not crash the app
            }
        }

        // Nonlinear metrics (computed less frequently — O(N²) for SampEn)
        if (rrBuffer.size >= 50) {
            try {
                val nonlinear = nonlinearAnalyzer.computeAll(rrBuffer)
                cachedSd1 = nonlinear.sd1
                cachedSd2 = nonlinear.sd2
                cachedDfaAlpha1 = nonlinear.dfaAlpha1
                cachedSampleEntropy = nonlinear.sampleEntropy
            } catch (_: Exception) { }
        }

        val newMetrics = HrvMetrics(
            hr = currentHr,
            rmssd = rmssd,
            sdnn = sdnn,
            pnn50 = pnn50,
            lfPower = lfPower,
            hfPower = hfPower,
            lfHfRatio = lfHfRatio,
            totalPower = totalPower,
            coherenceScore = coherenceScore,
            peakFrequency = peakFrequency,
            peakTroughAmplitude = peakTroughAmplitude,
            sd1 = cachedSd1,
            sd2 = cachedSd2,
            dfaAlpha1 = cachedDfaAlpha1,
            sampleEntropy = cachedSampleEntropy,
            breathingRate = cachedBreathingRate,
            cardiorespCoherence = cachedCardiorespCoherence,
            cardiorespPhase = cachedCardiorespPhase,
            timestamp = timestamp
        )

        _metrics.value = newMetrics
        synchronized(_allMetricsSnapshots) { _allMetricsSnapshots.add(newMetrics) }
    }

    /**
     * Resample HR to match respiratory signal using linear interpolation.
     * Linear interpolation preserves spectral content better than nearest-neighbor,
     * which is important for accurate cross-spectral coherence computation.
     */
    private fun linearResampleHr(
        hr: DoubleArray,
        hrRate: Double,
        targetLength: Int,
        targetRate: Double
    ): DoubleArray {
        if (hr.isEmpty() || targetLength <= 0) return doubleArrayOf()

        val hrDuration = hr.size / hrRate
        val targetDuration = targetLength / targetRate
        val duration = minOf(hrDuration, targetDuration)
        val outputLength = (duration * targetRate).toInt()

        return DoubleArray(outputLength) { i ->
            val t = i / targetRate
            val exactIdx = t * hrRate
            val idx0 = exactIdx.toInt().coerceIn(0, hr.size - 1)
            val idx1 = (idx0 + 1).coerceIn(0, hr.size - 1)
            val frac = exactIdx - idx0
            hr[idx0] + frac * (hr[idx1] - hr[idx0]) // Linear interpolation
        }
    }

    private fun trimBuffer() {
        if (timeBuffer.size < 2) return
        val cutoffTime = timeBuffer.last() - WINDOW_SECONDS
        while (timeBuffer.size > 2 && timeBuffer.first() < cutoffTime) {
            timeBuffer.removeAt(0)
            rrBuffer.removeAt(0)
        }
    }
}
