package com.hrv.biofeedback.presentation.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.data.ble.polar.PolarHrSource
import com.hrv.biofeedback.domain.model.BleDevice
import com.hrv.biofeedback.domain.model.ConnectionState
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
class DeviceConnectionViewModel @Inject constructor(
    private val hrDataSource: HrDataSource
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = hrDataSource.connectionState
    val batteryLevel: StateFlow<Int> = hrDataSource.batteryLevel

    // Use passive HR from callback — does NOT start a streaming session,
    // so it won't conflict with Training/Assessment/MorningCheck streams.
    val currentHr: StateFlow<Int> = (hrDataSource as PolarHrSource).lastHr

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        scanJob?.cancel()
        _discoveredDevices.value = emptyList()

        scanJob = viewModelScope.launch {
            hrDataSource.scanForDevices()
                .catch { /* scan ended or error */ }
                .collect { device ->
                    val current = _discoveredDevices.value.toMutableList()
                    if (current.none { it.deviceId == device.deviceId }) {
                        current.add(device)
                        _discoveredDevices.value = current
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        viewModelScope.launch { hrDataSource.stopScan() }
    }

    fun connect(deviceId: String) {
        stopScan()
        viewModelScope.launch {
            hrDataSource.connect(deviceId)
        }
    }

    fun disconnect() {
        viewModelScope.launch { hrDataSource.disconnect() }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
