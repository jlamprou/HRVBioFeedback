package com.hrv.biofeedback.presentation.report

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.repository.SessionDetail
import com.hrv.biofeedback.domain.repository.SessionRepository
import com.hrv.biofeedback.domain.usecase.export.SessionExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val sessionExporter: SessionExporter
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: -1L

    private val _sessionDetail = MutableStateFlow<SessionDetail?>(null)
    val sessionDetail: StateFlow<SessionDetail?> = _sessionDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _exportIntent = MutableStateFlow<Intent?>(null)
    val exportIntent: StateFlow<Intent?> = _exportIntent.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _isLoading.value = true
            _sessionDetail.value = sessionRepository.getSessionDetail(sessionId)
            _isLoading.value = false
        }
    }

    fun exportSession() {
        viewModelScope.launch {
            _exportIntent.value = sessionExporter.exportSession(sessionId)
        }
    }

    fun clearExportIntent() {
        _exportIntent.value = null
    }
}
