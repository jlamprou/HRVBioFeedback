package com.hrv.biofeedback.presentation.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.dsp.HrvProcessor
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.domain.model.HrvMetrics
import com.hrv.biofeedback.domain.repository.HrDataSource
import com.hrv.biofeedback.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SessionState { NOT_STARTED, RUNNING, PAUSED, COMPLETED }

/**
 * Training session with adaptive breathing rate adjustment.
 *
 * Evidence basis:
 * - Core protocol: Lehrer & Gevirtz (2014) "Heart rate variability biofeedback:
 *   how and why does it work?" - standard 10-20 min sessions at RF
 * - Metrics: 1996 Task Force standards for HRV measurement
 * - Biofeedback display: LF power and coherence as primary training targets
 *   per Lehrer protocol
 *
 * Adaptive breathing rate adjustment is an EMERGING approach. Laborde et al. (2022)
 * identified "Individual RF" real-time protocols as a distinct category, but noted
 * that ~2/3 of published studies lack sufficient protocol detail for replication.
 * Additionally, van Diest et al. (2021) found RF changes between sessions in 67%
 * of participants, supporting real-time adaptation over static rates.
 *
 * The adaptive algorithm nudges breathing rate by max 0.1 bpm every 2 minutes,
 * constrained to +/-0.5 bpm from the assessed RF — conservative by design.
 */
@HiltViewModel
class TrainingSessionViewModel @Inject constructor(
    private val hrDataSource: HrDataSource,
    private val hrvProcessor: HrvProcessor,
    private val sessionRepository: SessionRepository,
    private val userPreferences: com.hrv.biofeedback.data.local.preferences.UserPreferences,
    private val breathingCoach: com.hrv.biofeedback.domain.usecase.training.BreathingCoach
) : ViewModel() {

    val settings: StateFlow<com.hrv.biofeedback.data.local.preferences.AppSettings> =
        userPreferences.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            com.hrv.biofeedback.data.local.preferences.AppSettings()
        )

    val connectionState: StateFlow<ConnectionState> = hrDataSource.connectionState
    val metrics: StateFlow<HrvMetrics> = hrvProcessor.metrics

    private val _sessionState = MutableStateFlow(SessionState.NOT_STARTED)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _breathingRate = MutableStateFlow(6.0)
    val breathingRate: StateFlow<Double> = _breathingRate.asStateFlow()

    private val _hrHistory = MutableStateFlow<List<Pair<Int, Int>>>(emptyList()) // (seconds, hr)
    val hrHistory: StateFlow<List<Pair<Int, Int>>> = _hrHistory.asStateFlow()

    private val _savedSessionId = MutableStateFlow<Long?>(null)
    val savedSessionId: StateFlow<Long?> = _savedSessionId.asStateFlow()

    // Respiratory signal for breathing compliance display
    private val _breathingWaveform = MutableStateFlow<List<Float>>(emptyList())
    val breathingWaveform: StateFlow<List<Float>> = _breathingWaveform.asStateFlow()

    // Loaded from latest RF assessment, or user default
    private val _assessedRf = MutableStateFlow<Double?>(null)
    val assessedRf: StateFlow<Double?> = _assessedRf.asStateFlow()

    // Real-time coaching tip
    private val _coachingTip = MutableStateFlow<com.hrv.biofeedback.domain.usecase.training.BreathingCoach.CoachingTip?>(null)
    val coachingTip: StateFlow<com.hrv.biofeedback.domain.usecase.training.BreathingCoach.CoachingTip?> = _coachingTip.asStateFlow()

    var sessionDurationMinutes: Int = 20
    private var startTime: Long = 0
    private var hrStreamJob: Job? = null
    private var accStreamJob: Job? = null
    private var ecgStreamJob: Job? = null
    private var timerJob: Job? = null
    private var adaptiveJob: Job? = null

    // Adaptive training parameters
    private val adaptiveIntervalSeconds = 120 // Re-evaluate every 2 minutes
    private val maxAdjustment = 0.5 // Max +/- from baseline

    init {
        // Load latest RF assessment result to use as starting breathing rate
        viewModelScope.launch {
            _assessedRf.value = sessionRepository.getLatestRfResult()
        }
    }

    fun startSession(initialBreathingRate: Double? = null) {
        // Priority: explicit parameter > latest RF assessment > user default setting
        val rate = initialBreathingRate
            ?: _assessedRf.value
            ?: settings.value.defaultBreathingRate
        startSessionWithRate(rate)
    }

    private fun startSessionWithRate(initialBreathingRate: Double) {
        _breathingRate.value = initialBreathingRate
        hrvProcessor.reset()
        breathingCoach.reset()
        hrvProcessor.setBreathingRate(initialBreathingRate)
        _sessionState.value = SessionState.RUNNING
        _elapsedSeconds.value = 0
        _hrHistory.value = emptyList()
        startTime = System.currentTimeMillis()

        startHrStream()
        startAccStream()
        startEcgStream()
        startTimer()
        startAdaptiveBreathing(initialBreathingRate)
    }

    fun pauseSession() {
        if (_sessionState.value == SessionState.RUNNING) {
            _sessionState.value = SessionState.PAUSED
            hrStreamJob?.cancel()
            accStreamJob?.cancel()
            ecgStreamJob?.cancel()
            timerJob?.cancel()
            adaptiveJob?.cancel()
        }
    }

    fun resumeSession() {
        if (_sessionState.value == SessionState.PAUSED) {
            _sessionState.value = SessionState.RUNNING
            startHrStream()
            startAccStream()
            startEcgStream()
            startTimer()
            startAdaptiveBreathing(_breathingRate.value)
        }
    }

    fun stopSession() {
        _sessionState.value = SessionState.COMPLETED
        hrStreamJob?.cancel()
        accStreamJob?.cancel()
        ecgStreamJob?.cancel()
        timerJob?.cancel()
        adaptiveJob?.cancel()

        // Save session
        viewModelScope.launch {
            val sessionId = sessionRepository.saveTrainingSession(
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                breathingRate = _breathingRate.value,
                rrIntervals = hrvProcessor.allRrIntervals,
                metricsSnapshots = hrvProcessor.allMetricsSnapshots
            )
            _savedSessionId.value = sessionId
        }
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
                        }
                        // Add to HR history for chart
                        val seconds = _elapsedSeconds.value
                        val current = _hrHistory.value.toMutableList()
                        current.add(seconds to sample.hr)
                        if (current.size > 480) {
                            _hrHistory.value = current.takeLast(480)
                        } else {
                            _hrHistory.value = current
                        }

                        // Update coaching tip
                        val tip = breathingCoach.analyze(
                            metrics.value,
                            _breathingRate.value,
                            _elapsedSeconds.value
                        )
                        if (tip != null) _coachingTip.value = tip
                    }
            } catch (e: Exception) {
                // Handle stream error
            }
        }
    }

    /**
     * Stream accelerometer data for respiratory signal extraction.
     * The z-axis of the chest-mounted Polar H10 captures breathing movements.
     * Failures are silently ignored (ACC is optional enhancement).
     */
    private fun startAccStream() {
        accStreamJob?.cancel()
        accStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamAcc()
                    .catch { /* ACC not available or stream ended */ }
                    .collect { sample ->
                        hrvProcessor.addAccSample(sample.z.toDouble())

                        // Update breathing waveform for UI (keep last 300 points = ~30s at 10Hz)
                        val current = _breathingWaveform.value.toMutableList()
                        current.add(sample.z.toFloat())
                        if (current.size > 300) {
                            _breathingWaveform.value = current.takeLast(300)
                        } else {
                            _breathingWaveform.value = current
                        }
                    }
            } catch (e: Exception) {
                // ACC streaming not supported or device doesn't have it — that's fine
            }
        }
    }

    /**
     * Stream raw ECG data at 130 Hz for R-peak detection and ECG-derived respiration.
     * ECG-derived respiration serves as fallback when ACC signal is unavailable
     * or as a supplementary respiratory signal for improved accuracy.
     * Failures are silently ignored (ECG is optional enhancement).
     */
    private fun startEcgStream() {
        ecgStreamJob?.cancel()
        ecgStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamEcg()
                    .catch { /* ECG not available or stream ended */ }
                    .collect { sample ->
                        hrvProcessor.addEcgSample(sample.voltage)
                    }
            } catch (e: Exception) {
                // ECG streaming not supported — that's fine, ACC is primary
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_sessionState.value == SessionState.RUNNING) {
                delay(1000)
                _elapsedSeconds.value++
                if (_elapsedSeconds.value >= sessionDurationMinutes * 60) {
                    stopSession()
                    break
                }
            }
        }
    }

    private fun startAdaptiveBreathing(baselineRate: Double) {
        adaptiveJob?.cancel()
        adaptiveJob = viewModelScope.launch {
            while (_sessionState.value == SessionState.RUNNING) {
                delay(adaptiveIntervalSeconds * 1000L)

                val currentMetrics = metrics.value
                if (currentMetrics.lfPower > 0 && currentMetrics.peakFrequency > 0) {
                    // The peak frequency in the LF band indicates where the body
                    // is naturally resonating. Nudge breathing rate toward it.
                    val peakBreathingRate = currentMetrics.peakFrequency * 60.0 // Hz to bpm

                    // Only adjust if the peak is within reasonable range
                    if (peakBreathingRate in 4.0..7.0) {
                        val adjustment = (peakBreathingRate - _breathingRate.value)
                            .coerceIn(-0.1, 0.1) // Small nudges

                        val newRate = (_breathingRate.value + adjustment)
                            .coerceIn(baselineRate - maxAdjustment, baselineRate + maxAdjustment)

                        _breathingRate.value = newRate
                        hrvProcessor.setBreathingRate(newRate)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        hrStreamJob?.cancel()
        accStreamJob?.cancel()
        ecgStreamJob?.cancel()
        timerJob?.cancel()
        adaptiveJob?.cancel()
    }
}
