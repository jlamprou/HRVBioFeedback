package com.hrv.biofeedback.presentation.freetraining

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FreeSessionState { NOT_STARTED, RUNNING, COMPLETED }

/**
 * Advanced freeform biofeedback training — no breathing pacer.
 *
 * Scientific basis: This is the advanced phase of the Lehrer protocol
 * (Lehrer & Gevirtz 2014). After learning to breathe at their RF with
 * a pacer, users progress to watching the raw HR trace and maximizing
 * oscillation amplitude by timing their breathing to their heartbeat:
 * - Inhale when HR is rising
 * - Exhale when HR is falling
 * - Goal: make the waves as large as possible
 *
 * This develops interoceptive awareness and naturally tracks RF shifts
 * within a session without relying on a fixed pacer rate.
 */
@HiltViewModel
class FreeTrainingViewModel @Inject constructor(
    private val hrDataSource: HrDataSource,
    private val hrvProcessor: HrvProcessor,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hrDataSource.connectionState
    val metrics: StateFlow<HrvMetrics> = hrvProcessor.metrics

    private val _state = MutableStateFlow(FreeSessionState.NOT_STARTED)
    val state: StateFlow<FreeSessionState> = _state.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _hrHistory = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val hrHistory: StateFlow<List<Pair<Int, Int>>> = _hrHistory.asStateFlow()

    private val _savedSessionId = MutableStateFlow<Long?>(null)
    val savedSessionId: StateFlow<Long?> = _savedSessionId.asStateFlow()

    // Track the user's best amplitude this session for encouragement
    private val _bestAmplitude = MutableStateFlow(0.0)
    val bestAmplitude: StateFlow<Double> = _bestAmplitude.asStateFlow()

    private var hrStreamJob: Job? = null
    private var accStreamJob: Job? = null
    private var ecgStreamJob: Job? = null
    private var timerJob: Job? = null
    private var startTime: Long = 0

    var sessionDurationMinutes: Int = 20

    fun startSession() {
        hrvProcessor.reset()
        _state.value = FreeSessionState.RUNNING
        _elapsedSeconds.value = 0
        _hrHistory.value = emptyList()
        _bestAmplitude.value = 0.0
        startTime = System.currentTimeMillis()

        hrStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamHr()
                    .catch { }
                    .collect { sample ->
                        sample.rrsMs.forEach { rr ->
                            hrvProcessor.processRrInterval(rr, sample.timestamp, sample.contactDetected)
                        }
                        val seconds = _elapsedSeconds.value
                        val current = _hrHistory.value.toMutableList()
                        current.add(seconds to sample.hr)
                        _hrHistory.value = if (current.size > 600) current.takeLast(600) else current

                        // Track best amplitude
                        val amp = metrics.value.peakTroughAmplitude
                        if (amp > _bestAmplitude.value) _bestAmplitude.value = amp
                    }
            } catch (_: Exception) { }
        }

        accStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamAcc()
                    .catch { }
                    .collect { sample -> hrvProcessor.addAccSample(sample.z.toDouble()) }
            } catch (_: Exception) { }
        }

        ecgStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamEcg()
                    .catch { }
                    .collect { sample -> hrvProcessor.addEcgSample(sample.voltage) }
            } catch (_: Exception) { }
        }

        timerJob = viewModelScope.launch {
            while (_state.value == FreeSessionState.RUNNING) {
                delay(1000)
                _elapsedSeconds.value++
                if (_elapsedSeconds.value >= sessionDurationMinutes * 60) {
                    stopSession()
                    break
                }
            }
        }
    }

    fun stopSession() {
        _state.value = FreeSessionState.COMPLETED
        hrStreamJob?.cancel()
        accStreamJob?.cancel()
        ecgStreamJob?.cancel()
        timerJob?.cancel()

        viewModelScope.launch {
            val sessionId = sessionRepository.saveTrainingSession(
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                breathingRate = metrics.value.breathingRate, // Detected, not paced
                rrIntervals = hrvProcessor.allRrIntervals,
                metricsSnapshots = hrvProcessor.allMetricsSnapshots,
                artifactRate = hrvProcessor.signalQuality.artifactRatePercent
            )
            _savedSessionId.value = sessionId
        }
    }

    fun getSignalQuality() = hrvProcessor.signalQuality.getReport()

    override fun onCleared() {
        super.onCleared()
        hrStreamJob?.cancel()
        accStreamJob?.cancel()
        ecgStreamJob?.cancel()
        timerJob?.cancel()
    }
}
