package com.hrv.biofeedback.presentation.assessment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.dsp.ArSpectralAnalyzer
import com.hrv.biofeedback.domain.dsp.CubicSplineInterpolator
import com.hrv.biofeedback.domain.dsp.HrvMetricsCalculator
import com.hrv.biofeedback.domain.dsp.HrvProcessor
import com.hrv.biofeedback.domain.dsp.PhaseAnalyzer
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.domain.model.HrvMetrics
import com.hrv.biofeedback.domain.model.LEHRER_PROTOCOL
import com.hrv.biofeedback.domain.model.RfAssessmentResult
import com.hrv.biofeedback.domain.model.RfStepResult
import com.hrv.biofeedback.domain.repository.HrDataSource
import com.hrv.biofeedback.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AssessmentState {
    NOT_STARTED, RUNNING, REST, COMPLETED, ERROR
}

/**
 * Implements the Lehrer/Vaschillo RF assessment protocol as refined by
 * Shaffer et al. (2020) "A Practical Guide to Resonance Frequency Assessment".
 *
 * Evaluates 6 criteria from Shaffer et al. (2020) in their published priority order:
 * 1. Phase synchrony (HR peaks align with inhalation)
 * 2. Peak-to-trough HR amplitude (HR Max - HR Min)
 * 3. LF power (absolute power in 0.04-0.15 Hz band)
 * 4. Maximum LF amplitude peak
 * 5. HR curve smoothness (sinusoidal waveform)
 * 6. Fewest LF peaks (single dominant oscillation)
 *
 * NOTE: Shaffer et al. define the priority order but not numerical weights.
 * The weighted scoring formula is our own implementation choice.
 *
 * Breathing pacer uses 45/55 inhale/exhale per Shaffer recommendation
 * of longer exhalation during assessment.
 */
@HiltViewModel
class RfAssessmentViewModel @Inject constructor(
    private val hrDataSource: HrDataSource,
    private val hrvProcessor: HrvProcessor,
    private val sessionRepository: SessionRepository,
    private val phaseAnalyzer: PhaseAnalyzer,
    private val spectralAnalyzer: ArSpectralAnalyzer,
    private val splineInterpolator: CubicSplineInterpolator,
    private val metricsCalculator: HrvMetricsCalculator
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hrDataSource.connectionState
    val metrics: StateFlow<HrvMetrics> = hrvProcessor.metrics

    private val _assessmentState = MutableStateFlow(AssessmentState.NOT_STARTED)
    val assessmentState: StateFlow<AssessmentState> = _assessmentState.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _currentBreathingRate = MutableStateFlow(6.5)
    val currentBreathingRate: StateFlow<Double> = _currentBreathingRate.asStateFlow()

    private val _stepTimeRemaining = MutableStateFlow(0)
    val stepTimeRemaining: StateFlow<Int> = _stepTimeRemaining.asStateFlow()

    private val _stepResults = MutableStateFlow<List<RfStepResult>>(emptyList())
    val stepResults: StateFlow<List<RfStepResult>> = _stepResults.asStateFlow()

    private val _result = MutableStateFlow<RfAssessmentResult?>(null)
    val result: StateFlow<RfAssessmentResult?> = _result.asStateFlow()

    private val _savedSessionId = MutableStateFlow<Long?>(null)
    val savedSessionId: StateFlow<Long?> = _savedSessionId.asStateFlow()

    private var assessmentJob: Job? = null
    private var hrStreamJob: Job? = null
    private var accStreamJob: Job? = null
    private var ecgStreamJob: Job? = null
    private var startTime: Long = 0

    // Collect raw RR intervals per step for dedicated spectral analysis
    private val stepRrIntervals = mutableListOf<Pair<Long, Int>>() // (timestamp, rrMs)

    val totalSteps: Int = LEHRER_PROTOCOL.size

    /**
     * Inhale ratio for RF assessment: 50% (equal inhale/exhale).
     * Per Lehrer protocol, asymmetric breathing patterns can bias RF detection.
     * Training sessions may use different ratios (e.g., 40/60) after RF is found.
     */
    /**
     * Inhale ratio for RF assessment.
     * Shaffer et al. (2020) recommends longer exhalation than inhalation.
     * We use 45/55 as a moderate asymmetry that promotes relaxation without
     * heavily biasing the RF measurement. Van Diest et al. (2014) found no
     * significant HRV metric differences between 1:1 and 1:2 ratios at 5.5 bpm.
     */
    val assessmentInhaleRatio: Float = 0.45f

    companion object {
        /**
         * Rest period between assessment steps in seconds.
         * Shaffer et al. (2020) specifies 2-minute rest periods between trials
         * to allow the cardiovascular system to return to baseline.
         */
        const val REST_PERIOD_SECONDS = 120
    }

    fun startAssessment() {
        hrvProcessor.reset()
        startTime = System.currentTimeMillis()
        _stepResults.value = emptyList()
        _result.value = null

        startHrStream()
        startAccStream()
        startEcgStream()

        assessmentJob = viewModelScope.launch {
            _assessmentState.value = AssessmentState.RUNNING

            for (stepIndex in LEHRER_PROTOCOL.indices) {
                val step = LEHRER_PROTOCOL[stepIndex]
                _currentStepIndex.value = stepIndex
                _currentBreathingRate.value = step.breathingRate
                hrvProcessor.setBreathingRate(step.breathingRate)

                // Rest period between steps: 2 minutes per Shaffer et al. (2020).
                // The cardiovascular system needs time to return to baseline
                // between breathing rate changes for accurate RF measurement.
                if (stepIndex > 0) {
                    _assessmentState.value = AssessmentState.REST
                    val restDurationSeconds = REST_PERIOD_SECONDS
                    _stepTimeRemaining.value = restDurationSeconds
                    for (i in restDurationSeconds downTo 1) {
                        _stepTimeRemaining.value = i
                        delay(1000)
                    }
                }

                // Begin breathing step - collect RR intervals for this step
                _assessmentState.value = AssessmentState.RUNNING
                stepRrIntervals.clear()
                val stepMetrics = mutableListOf<HrvMetrics>()
                val stepStartTime = System.currentTimeMillis()

                for (second in step.durationSeconds downTo 1) {
                    _stepTimeRemaining.value = second
                    delay(1000)

                    // Collect metrics for the second half of each step only.
                    // First half is stabilization time — cardiovascular system needs
                    // ~30-60 seconds to entrain to a new breathing rate (Vaschillo et al.)
                    if (second <= step.durationSeconds / 2) {
                        stepMetrics.add(metrics.value)
                    }
                }

                // Perform dedicated spectral analysis on this step's data
                val stepResult = analyzeStep(step.breathingRate, stepMetrics)
                _stepResults.value = _stepResults.value + stepResult
            }

            // Score all steps using Shaffer's 6 criteria
            val allResults = _stepResults.value
            val scored = scoreResults(allResults)
            val optimalRate = scored.maxByOrNull { it.combinedScore }?.breathingRate ?: 6.0

            val assessmentResult = RfAssessmentResult(
                stepResults = scored,
                optimalRate = optimalRate,
                timestamp = System.currentTimeMillis()
            )
            _result.value = assessmentResult
            _assessmentState.value = AssessmentState.COMPLETED

            // Save to database
            hrStreamJob?.cancel()
            accStreamJob?.cancel()
            ecgStreamJob?.cancel()
            val sessionId = sessionRepository.saveAssessmentSession(
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                result = assessmentResult,
                rrIntervals = hrvProcessor.allRrIntervals,
                metricsSnapshots = hrvProcessor.allMetricsSnapshots,
                artifactRate = hrvProcessor.signalQuality.artifactRatePercent,
                definitiveMetrics = hrvProcessor.computeDefinitive()
            )
            _savedSessionId.value = sessionId
        }
    }

    fun cancelAssessment() {
        assessmentJob?.cancel()
        hrStreamJob?.cancel()
        accStreamJob?.cancel()
        ecgStreamJob?.cancel()
        _assessmentState.value = AssessmentState.NOT_STARTED
    }

    private fun startHrStream() {
        hrStreamJob?.cancel()
        hrStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamHr()
                    .catch { /* stream ended */ }
                    .collect { sample ->
                        sample.rrsMs.forEach { rr ->
                            hrvProcessor.processRrInterval(
                                rr, sample.timestamp, sample.contactDetected
                            )
                            stepRrIntervals.add(sample.timestamp to rr)
                        }
                    }
            } catch (e: Exception) {
                _assessmentState.value = AssessmentState.ERROR
            }
        }
    }

    /** Stream ACC data for respiratory signal during assessment. */
    private fun startAccStream() {
        accStreamJob?.cancel()
        accStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamAcc()
                    .catch { /* ACC not available */ }
                    .collect { sample -> hrvProcessor.addAccSample(sample.z.toDouble()) }
            } catch (_: Exception) { }
        }
    }

    /** Stream ECG data for R-peak detection and EDR during assessment. */
    private fun startEcgStream() {
        ecgStreamJob?.cancel()
        ecgStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamEcg()
                    .catch { /* ECG not available */ }
                    .collect { sample -> hrvProcessor.addEcgSample(sample.voltage) }
            } catch (_: Exception) { }
        }
    }

    /**
     * Analyze a single assessment step using all 6 Shaffer criteria.
     * Uses the HRV metrics collected during the measurement window (second half of step)
     * plus the latest PSD from HrvProcessor for spectral criteria (5, 6).
     */
    private fun analyzeStep(
        breathingRate: Double,
        stepMetrics: List<HrvMetrics>
    ): RfStepResult {
        if (stepMetrics.isEmpty()) {
            return RfStepResult(breathingRate, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val avgLf = stepMetrics.map { it.lfPower }.average()
        val avgHf = stepMetrics.map { it.hfPower }.average()
        val avgCoherence = stepMetrics.map { it.coherenceScore }.average()
        val avgAmplitude = stepMetrics.map { it.peakTroughAmplitude }.average()

        val breathingFreqHz = breathingRate / 60.0
        val avgPeakFreq = stepMetrics.map { it.peakFrequency }.filter { it > 0 }
            .let { if (it.isNotEmpty()) it.average() else 0.0 }

        // --- Criterion 1: Phase synchrony ---
        // Three tiers of measurement quality:
        // Tier 1: True cross-spectral coherence from ACC/ECG respiratory signal (best)
        // Tier 2: DFT-based phase analysis on resampled instantaneous HR (good)
        // Tier 3: Frequency alignment proxy (basic fallback)
        val avgCRCoherence = stepMetrics.map { it.cardiorespCoherence }.average()
        val latestHr = hrvProcessor.latestInstantaneousHr
        val phaseSync = if (avgCRCoherence > 0.01) {
            // Tier 1: Direct cross-spectral coherence at breathing frequency (0-1)
            avgCRCoherence
        } else if (latestHr != null && latestHr.size >= 20) {
            // Tier 2: DFT-based phase synchrony between HR and a synthetic
            // breathing reference at the target frequency
            phaseAnalyzer.calculatePhaseSynchrony(
                latestHr, HrvProcessor.SAMPLE_RATE, breathingFreqHz
            )
        } else if (avgPeakFreq > 0 && breathingFreqHz > 0) {
            // Tier 3: Frequency alignment weighted by power concentration
            val freqAlignment = 1.0 - (kotlin.math.abs(avgPeakFreq - breathingFreqHz) /
                    breathingFreqHz).coerceIn(0.0, 1.0)
            freqAlignment * 0.7 + (avgCoherence / (avgCoherence + 1.0)) * 0.3
        } else 0.0

        // --- Criterion 5: Waveform smoothness (spectral concentration) ---
        // --- Criterion 6: Number of LF peaks ---
        // Use the actual PSD from HrvProcessor when available
        var curveSmoothness = 0.0
        var lfPeakCount = 1
        val psd = hrvProcessor.latestPsd
        if (psd != null) {
            curveSmoothness = phaseAnalyzer.calculateWaveformSmoothness(psd)
            lfPeakCount = phaseAnalyzer.countLfPeaks(psd)
        } else {
            // Fallback: use normalized coherence as smoothness proxy
            curveSmoothness = (avgCoherence / (avgCoherence + 1.0))
        }

        return RfStepResult(
            breathingRate = breathingRate,
            lfPower = avgLf,
            hfPower = avgHf,
            coherenceScore = avgCoherence,
            peakTroughAmplitude = avgAmplitude,
            phaseSync = phaseSync.coerceIn(0.0, 1.0),
            curveSmoothness = curveSmoothness.coerceIn(0.0, 1.0),
            lfPeakCount = lfPeakCount
        )
    }

    /**
     * Score all step results based on 6 criteria from Shaffer et al. (2020).
     *
     * IMPORTANT: Shaffer et al. (2020) defines the priority ORDER of criteria
     * but does NOT specify numerical weights. The paper states:
     * "Researchers have not validated these weights and they require
     * experimental confirmation."
     *
     * The numerical weights below are our own approximation of the priority
     * ordering described in the paper. They are NOT from the published literature.
     * Clinically, the Lehrer/Shaffer approach uses expert judgment to find
     * "best convergence" across criteria rather than a weighted formula.
     *
     * Priority order (from Shaffer 2020) and our approximated weights:
     * 1. Phase synchrony          — 0.25 (highest priority per Shaffer)
     * 2. Peak-to-trough amplitude — 0.25
     * 3. LF power                 — 0.20
     * 4. Coherence/LF peak        — 0.12
     * 5. Waveform smoothness      — 0.10
     * 6. Fewest LF peaks          — 0.08 (lowest priority per Shaffer)
     *
     * Each metric is min-max normalized across the 5 breathing rates before weighting.
     */
    private fun scoreResults(results: List<RfStepResult>): List<RfStepResult> {
        if (results.isEmpty()) return results

        fun normalize(values: List<Double>): List<Double> {
            val min = values.min()
            val max = values.max()
            return if (max - min > 0) values.map { (it - min) / (max - min) }
            else values.map { 0.5 }
        }

        val normPhaseSync = normalize(results.map { it.phaseSync })
        val normAmplitude = normalize(results.map { it.peakTroughAmplitude })
        val normLf = normalize(results.map { it.lfPower })
        val normCoherence = normalize(results.map { it.coherenceScore })
        val normSmoothness = normalize(results.map { it.curveSmoothness })

        // Criterion 6: Fewest LF peaks — use actual peak count from PhaseAnalyzer.
        // Convert to score: 1 peak = 1.0 (ideal), 2 peaks = 0.5, 3+ = 0.0
        // Then normalize across steps like all other criteria.
        val normFewestPeaks = normalize(results.map { phaseAnalyzer.lfPeakScore(it.lfPeakCount) })

        return results.mapIndexed { i, result ->
            val score = 0.25 * normPhaseSync[i] +     // Criterion 1: Phase synchrony
                    0.25 * normAmplitude[i] +          // Criterion 2: Peak-to-trough
                    0.20 * normLf[i] +                 // Criterion 3: LF power
                    0.12 * normCoherence[i] +           // Criterion 4: LF peak magnitude
                    0.10 * normSmoothness[i] +          // Criterion 5: Waveform smoothness
                    0.08 * normFewestPeaks[i]           // Criterion 6: Fewest LF peaks
            result.copy(combinedScore = score)
        }
    }

    override fun onCleared() {
        super.onCleared()
        assessmentJob?.cancel()
        hrStreamJob?.cancel()
        accStreamJob?.cancel()
        ecgStreamJob?.cancel()
    }
}
