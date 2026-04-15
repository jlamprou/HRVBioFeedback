package com.hrv.biofeedback.presentation.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.dsp.HrvProcessor
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.domain.model.HrvMetrics
import com.hrv.biofeedback.domain.repository.HrDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveMetricsViewModel @Inject constructor(
    private val hrDataSource: HrDataSource,
    private val hrvProcessor: HrvProcessor
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hrDataSource.connectionState
    val metrics: StateFlow<HrvMetrics> = hrvProcessor.metrics

    private val _hrHistory = MutableStateFlow<List<Pair<Int, Int>>>(emptyList())
    val hrHistory: StateFlow<List<Pair<Int, Int>>> = _hrHistory.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private var hrStreamJob: Job? = null
    private var accStreamJob: Job? = null
    private var ecgStreamJob: Job? = null
    private var elapsedSeconds = 0

    fun startStreaming() {
        if (_isStreaming.value) return
        hrvProcessor.reset()
        _isStreaming.value = true
        _hrHistory.value = emptyList()
        elapsedSeconds = 0

        hrStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamHr()
                    .catch { _isStreaming.value = false }
                    .collect { sample ->
                        sample.rrsMs.forEach { rr ->
                            hrvProcessor.processRrInterval(rr, sample.timestamp, sample.contactDetected)
                        }
                        elapsedSeconds = (hrvProcessor.allRrIntervals.size)
                        val current = _hrHistory.value.toMutableList()
                        current.add(elapsedSeconds to sample.hr)
                        _hrHistory.value = if (current.size > 600) current.takeLast(600) else current
                    }
            } catch (_: Exception) {
                _isStreaming.value = false
            }
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
    }

    fun stopStreaming() {
        hrStreamJob?.cancel()
        accStreamJob?.cancel()
        ecgStreamJob?.cancel()
        _isStreaming.value = false
    }

    fun getSignalQuality() = hrvProcessor.signalQuality.getReport()

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
    }
}
