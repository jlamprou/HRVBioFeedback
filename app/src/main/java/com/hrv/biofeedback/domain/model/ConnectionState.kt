package com.hrv.biofeedback.domain.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Scanning : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val deviceId: String, val deviceName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}
