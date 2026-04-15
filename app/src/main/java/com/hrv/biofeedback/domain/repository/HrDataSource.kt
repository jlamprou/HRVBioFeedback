package com.hrv.biofeedback.domain.repository

import com.hrv.biofeedback.domain.model.AccSample
import com.hrv.biofeedback.domain.model.BleDevice
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.domain.model.EcgSample
import com.hrv.biofeedback.domain.model.HrSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface HrDataSource {
    val connectionState: StateFlow<ConnectionState>
    val batteryLevel: StateFlow<Int> // 0-100, -1 = unknown

    fun scanForDevices(): Flow<BleDevice>
    suspend fun stopScan()
    suspend fun connect(deviceId: String)
    suspend fun disconnect()
    fun streamHr(): Flow<HrSample>

    /**
     * Stream raw ECG data at 130 Hz from Polar H10.
     * Each sample contains a voltage in microvolts.
     * Requires FEATURE_POLAR_ONLINE_STREAMING capability.
     */
    fun streamEcg(): Flow<EcgSample>

    /**
     * Stream accelerometer data from Polar H10 (up to 200 Hz via maxSettings).
     * Z-axis (anterior-posterior) captures chest wall respiratory movement.
     * Requires FEATURE_POLAR_ONLINE_STREAMING capability.
     */
    fun streamAcc(): Flow<AccSample>
}
