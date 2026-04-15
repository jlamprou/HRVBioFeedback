package com.hrv.biofeedback.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.domain.repository.HrDataSource
import com.hrv.biofeedback.domain.repository.SessionRepository
import com.hrv.biofeedback.domain.repository.SessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val hrDataSource: HrDataSource,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hrDataSource.connectionState

    val recentSessions = sessionRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentRf = MutableStateFlow<Double?>(null)
    val currentRf: StateFlow<Double?> = _currentRf.asStateFlow()

    private val _latestSession = MutableStateFlow<SessionSummary?>(null)
    val latestSession: StateFlow<SessionSummary?> = _latestSession.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _currentRf.value = sessionRepository.getLatestRfResult()
            _latestSession.value = sessionRepository.getLatestSession()
            _sessionCount.value = sessionRepository.getSessionCount()
        }
    }
}
