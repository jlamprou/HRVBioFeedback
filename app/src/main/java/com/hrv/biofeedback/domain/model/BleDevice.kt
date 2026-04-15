package com.hrv.biofeedback.domain.model

data class BleDevice(
    val deviceId: String,
    val name: String,
    val rssi: Int = 0,
    val isPolar: Boolean = false
)
