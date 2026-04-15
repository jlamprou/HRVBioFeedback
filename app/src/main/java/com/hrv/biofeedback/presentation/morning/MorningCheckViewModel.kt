package com.hrv.biofeedback.presentation.morning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.dsp.HrvProcessor
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.domain.model.HrvMetrics
import com.hrv.biofeedback.domain.repository.HrDataSource
import com.hrv.biofeedback.domain.repository.SessionRepository
import com.hrv.biofeedback.domain.repository.SessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CheckState { NOT_STARTED, RECORDING, COMPLETED }

/**
 * 2-minute morning HRV baseline check.
 *
 * Scientific basis:
 * - Morning resting HRV is the gold standard for tracking autonomic adaptation
 *   (Plews et al. 2013, "Training Adaptation and Heart Rate Variability in Athletes")
 * - Should be performed supine, immediately after waking, before standing
 * - Minimum 1 minute for reliable RMSSD (Task Force 1996), we use 2 minutes
 *   for more stable frequency-domain estimates
 * - Tracking RMSSD trend over weeks shows whether biofeedback training is
 *   improving baseline parasympathetic tone
 */
@HiltViewModel
class MorningCheckViewModel @Inject constructor(
    private val hrDataSource: HrDataSource,
    private val hrvProcessor: HrvProcessor,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hrDataSource.connectionState
    val metrics: StateFlow<HrvMetrics> = hrvProcessor.metrics

    private val _state = MutableStateFlow(CheckState.NOT_STARTED)
    val state: StateFlow<CheckState> = _state.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _hrHistory = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val hrHistory: StateFlow<List<Pair<Int, Int>>> = _hrHistory.asStateFlow()

    private val _savedSessionId = MutableStateFlow<Long?>(null)
    val savedSessionId: StateFlow<Long?> = _savedSessionId.asStateFlow()

    // Trend data from previous morning checks
    private val _trend = MutableStateFlow<List<SessionSummary>>(emptyList())
    val trend: StateFlow<List<SessionSummary>> = _trend.asStateFlow()

    // Final results
    private val _result = MutableStateFlow<MorningCheckResult?>(null)
    val result: StateFlow<MorningCheckResult?> = _result.asStateFlow()

    private var hrStreamJob: Job? = null
    private var timerJob: Job? = null
    private var startTime: Long = 0

    companion object {
        const val CHECK_DURATION_SECONDS = 120 // 2 minutes
    }

    init {
        loadTrend()
    }

    private fun loadTrend() {
        viewModelScope.launch {
            _trend.value = sessionRepository.getMorningCheckTrend()
        }
    }

    fun startCheck() {
        hrvProcessor.reset()
        _state.value = CheckState.RECORDING
        _elapsedSeconds.value = 0
        _hrHistory.value = emptyList()
        _result.value = null
        startTime = System.currentTimeMillis()

        // Stream HR (natural breathing, no pacer)
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
                        _hrHistory.value = if (current.size > 240) current.takeLast(240) else current
                    }
            } catch (_: Exception) { }
        }

        // Timer
        timerJob = viewModelScope.launch {
            while (_elapsedSeconds.value < CHECK_DURATION_SECONDS) {
                delay(1000)
                _elapsedSeconds.value++
            }
            finishCheck()
        }
    }

    private fun finishCheck() {
        hrStreamJob?.cancel()
        timerJob?.cancel()

        val currentMetrics = metrics.value
        val previousChecks = _trend.value

        // Compare with previous morning checks
        val previousRmssd = previousChecks.takeIf { it.isNotEmpty() }?.let {
            it.take(7).map { s -> s.averageRmssd }.average() // 7-day average
        }
        val previousHr = previousChecks.takeIf { it.isNotEmpty() }?.let {
            it.take(7).map { s -> s.averageHr }.average()
        }

        _result.value = MorningCheckResult(
            rmssd = currentMetrics.rmssd,
            sdnn = currentMetrics.sdnn,
            hr = currentMetrics.hr,
            pnn50 = currentMetrics.pnn50,
            sd1 = currentMetrics.sd1,
            sd2 = currentMetrics.sd2,
            dfaAlpha1 = currentMetrics.dfaAlpha1,
            lfHfRatio = currentMetrics.lfHfRatio,
            rmssdChange = if (previousRmssd != null && previousRmssd > 0)
                ((currentMetrics.rmssd - previousRmssd) / previousRmssd * 100) else null,
            hrChange = if (previousHr != null && previousHr > 0)
                ((currentMetrics.hr - previousHr) / previousHr * 100) else null,
            totalChecks = previousChecks.size + 1
        )

        _state.value = CheckState.COMPLETED

        // Save
        viewModelScope.launch {
            val sessionId = sessionRepository.saveMorningCheck(
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                rrIntervals = hrvProcessor.allRrIntervals,
                metricsSnapshots = hrvProcessor.allMetricsSnapshots
            )
            _savedSessionId.value = sessionId
            loadTrend() // Refresh trend
        }
    }

    override fun onCleared() {
        super.onCleared()
        hrStreamJob?.cancel()
        timerJob?.cancel()
    }
}

data class MorningCheckResult(
    val rmssd: Double,
    val sdnn: Double,
    val hr: Int,
    val pnn50: Double,
    val sd1: Double,
    val sd2: Double,
    val dfaAlpha1: Double,
    val lfHfRatio: Double,
    val rmssdChange: Double?, // % change vs 7-day average, null if no history
    val hrChange: Double?,
    val totalChecks: Int
)
