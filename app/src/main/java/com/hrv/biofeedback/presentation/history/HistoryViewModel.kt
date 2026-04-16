package com.hrv.biofeedback.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.model.SessionType
import com.hrv.biofeedback.domain.repository.SessionRepository
import com.hrv.biofeedback.domain.repository.SessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _filter = MutableStateFlow<SessionType?>(null)
    val filter: StateFlow<SessionType?> = _filter.asStateFlow()

    val sessions: StateFlow<List<SessionSummary>> = _filter.flatMapLatest { type ->
        if (type != null) sessionRepository.getSessionsByType(type)
        else sessionRepository.getAllSessions()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(type: SessionType?) {
        _filter.value = type
    }

    fun convertToMorningCheck(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.convertSessionType(sessionId, SessionType.MORNING_CHECK)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
        }
    }
}
