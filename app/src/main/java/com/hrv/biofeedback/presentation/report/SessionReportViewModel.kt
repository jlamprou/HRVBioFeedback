package com.hrv.biofeedback.presentation.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.repository.SessionDetail
import com.hrv.biofeedback.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val sessionId: Long = savedStateHandle["sessionId"] ?: -1L

    private val _sessionDetail = MutableStateFlow<SessionDetail?>(null)
    val sessionDetail: StateFlow<SessionDetail?> = _sessionDetail.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
}
