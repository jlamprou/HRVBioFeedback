package com.hrv.biofeedback.presentation.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private val _currentHr = MutableStateFlow(0)
    val currentHr: StateFlow<Int> = _currentHr.asStateFlow()

    private var scanJob: Job? = null
    private var hrStreamJob: Job? = null

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
            // Start streaming HR once connected
            startHrStream()
        }
    }

    fun disconnect() {
        hrStreamJob?.cancel()
        viewModelScope.launch { hrDataSource.disconnect() }
    }

    private fun startHrStream() {
        hrStreamJob?.cancel()
        hrStreamJob = viewModelScope.launch {
            try {
                hrDataSource.streamHr()
                    .catch { /* stream ended */ }
                    .collect { sample ->
                        _currentHr.value = sample.hr
                    }
            } catch (e: Exception) {
                // Device not connected yet, will retry when state changes
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        hrStreamJob?.cancel()
    }
}
