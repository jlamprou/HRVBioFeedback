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

    // Dedicated HR flow — updates at the exact same moment as the graph
    private val _currentHr = MutableStateFlow(0)
    val currentHr: StateFlow<Int> = _currentHr.asStateFlow()

    /**
     * HR trend direction via linear regression with adaptive noise threshold.
     *
     * Window: 4 beats — covers ~40% of one half-cycle at RF (6 bpm = 10s).
     * Responsive enough to detect direction within ~2s of a turn, long enough
     * to average out beat-to-beat noise.
     *
     * Deadzone: ADAPTIVE based on current RMSSD (the actual noise level).
     * Beat-to-beat HR noise ≈ RMSSD(ms) * meanHR² / 60000² — converted to bpm.
     * For N-point regression, slope noise σ = noise_bpm * sqrt(12/(N*(N²-1))).
     * Deadzone = 1.5 * σ (filters 87% of noise-only slopes).
     *
     * This means: low noise (trained user) → responsive arrows;
     * high noise (beginner) → less flickering.
     */
    private val _hrTrend = MutableStateFlow(0) // -1, 0, +1
    val hrTrend: StateFlow<Int> = _hrTrend.asStateFlow()
    private val recentHrValues = ArrayDeque<Int>(TREND_WINDOW)

    companion object {
        private const val TREND_WINDOW = 4
        private const val MIN_DEADZONE = 0.3   // minimum to prevent flicker at peaks
        private const val DEADZONE_FACTOR = 1.5 // multiplier on estimated noise σ
    }

    private val _state = MutableStateFlow(FreeSessionState.NOT_STARTED)
    val state: StateFlow<FreeSessionState> = _state.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    // Use Long ms timestamps for smooth graph positioning (not integer seconds)
    private val hrHistoryBuffer = ArrayDeque<Pair<Int, Int>>(300)
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
        hrHistoryBuffer.clear()
        recentHrValues.clear()
        _hrHistory.value = emptyList()
        _hrTrend.value = 0
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
                        // Update HR, trend, and graph in ONE shot — perfectly synced
                        _currentHr.value = sample.hr

                        // Compute trend via linear regression with adaptive noise threshold
                        recentHrValues.addLast(sample.hr)
                        while (recentHrValues.size > TREND_WINDOW) recentHrValues.removeFirst()
                        if (recentHrValues.size >= TREND_WINDOW) {
                            val vals = recentHrValues.toList()
                            val n = vals.size

                            // Linear regression slope
                            val meanX = (n - 1) / 2.0
                            val meanY = vals.average()
                            var num = 0.0
                            var den = 0.0
                            for (i in vals.indices) {
                                num += (i - meanX) * (vals[i] - meanY)
                                den += (i - meanX) * (i - meanX)
                            }
                            val slope = if (den > 0) num / den else 0.0

                            // Adaptive deadzone from RMSSD (actual beat-to-beat noise)
                            // RMSSD in ms → approximate bpm noise via: noise_bpm ≈ RMSSD * HR² / 60000
                            val rmssd = metrics.value.rmssd
                            val hr = metrics.value.hr.toDouble()
                            val noiseBpm = if (hr > 0 && rmssd > 0) rmssd * hr * hr / 3600000.0 else 2.0
                            // Slope noise for N-point regression: σ_slope = noise * sqrt(12/(N*(N²-1)))
                            val slopeNoise = noiseBpm * kotlin.math.sqrt(12.0 / (n * (n.toDouble() * n - 1)))
                            val deadzone = maxOf(MIN_DEADZONE, DEADZONE_FACTOR * slopeNoise)

                            _hrTrend.value = when {
                                slope > deadzone -> 1     // rising — user should be inhaling
                                slope < -deadzone -> -1   // falling — user should be exhaling
                                else -> 0                  // at peak/trough — transition point
                            }
                        }

                        hrHistoryBuffer.addLast(_elapsedSeconds.value to sample.hr)
                        while (hrHistoryBuffer.size > 300) hrHistoryBuffer.removeFirst()
                        _hrHistory.value = hrHistoryBuffer.toList()

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
