package com.hrv.biofeedback.data.ble.polar

import android.content.Context
import android.util.Log
import com.hrv.biofeedback.domain.model.AccSample
import com.hrv.biofeedback.domain.model.BleDevice
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.domain.model.EcgSample as DomainEcgSample
import com.hrv.biofeedback.domain.model.HrSample
import com.hrv.biofeedback.domain.repository.HrDataSource
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.EcgSample as PolarEcgSample
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PolarHrSource @Inject constructor(
    @ApplicationContext private val context: Context
) : HrDataSource {

    companion object {
        private const val TAG = "PolarHrSource"
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _batteryLevel = MutableStateFlow(-1)
    override val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    // Passive HR from callback (always active when connected, doesn't conflict with streaming)
    private val _lastHr = MutableStateFlow(0)
    val lastHr: StateFlow<Int> = _lastHr.asStateFlow()

    private var connectedDeviceId: String? = null

    @Suppress("OVERRIDE_DEPRECATION")
    private val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            context,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING
            )
        ).also { polarApi ->
            polarApi.setApiCallback(object : PolarBleApiCallbackProvider {
                override fun blePowerStateChanged(powered: Boolean) {
                    Log.d(TAG, "BLE power: $powered")
                    if (!powered) {
                        _connectionState.value = ConnectionState.Error("Bluetooth is turned off")
                    }
                }

                override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                    Log.d(TAG, "Device connected: ${polarDeviceInfo.deviceId}")
                    _connectionState.value = ConnectionState.Connected(
                        deviceId = polarDeviceInfo.deviceId,
                        deviceName = polarDeviceInfo.name
                    )
                }

                override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                    _connectionState.value = ConnectionState.Connecting
                }

                override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                    connectedDeviceId = null
                    _connectionState.value = ConnectionState.Disconnected
                }

                override fun bleSdkFeatureReady(
                    identifier: String,
                    feature: PolarBleApi.PolarBleSdkFeature
                ) {
                    Log.d(TAG, "Feature ready: $feature for $identifier")
                }

                override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                    Log.d(TAG, "Device info: $uuid = $value")
                }

                override fun disInformationReceived(
                    identifier: String,
                    disInfo: com.polar.androidcommunications.api.ble.model.DisInfo
                ) {
                    Log.d(TAG, "Device info: $disInfo")
                }

                override fun batteryLevelReceived(identifier: String, level: Int) {
                    Log.d(TAG, "Battery level: $level%")
                    _batteryLevel.value = level
                }

                override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                    // Passive HR updates (always active when connected, no streaming needed)
                    _lastHr.value = data.hr
                }

                override fun bleSdkFeaturesReadiness(
                    identifier: String,
                    ready: List<PolarBleApi.PolarBleSdkFeature>,
                    unavailable: List<PolarBleApi.PolarBleSdkFeature>
                ) {
                    Log.d(TAG, "Features readiness - ready: $ready, unavailable: $unavailable")
                }

                override fun batteryChargingStatusReceived(
                    identifier: String,
                    chargingStatus: com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
                ) {
                    Log.d(TAG, "Charging: $chargingStatus")
                }

                override fun powerSourcesStateReceived(
                    identifier: String,
                    powerSourcesState: com.polar.androidcommunications.api.ble.model.gatt.client.PowerSourcesState
                ) {
                    Log.d(TAG, "Power sources: $powerSourcesState")
                }

                override fun htsNotificationReceived(
                    identifier: String,
                    data: com.polar.sdk.api.model.PolarHealthThermometerData
                ) {
                    // Not used for HRV biofeedback
                }
            })
        }
    }

    override fun scanForDevices(): Flow<BleDevice> = callbackFlow {
        _connectionState.value = ConnectionState.Scanning

        api.searchForDevice().collect { deviceInfo ->
            trySend(
                BleDevice(
                    deviceId = deviceInfo.deviceId,
                    name = deviceInfo.name.ifEmpty { "Unknown (${deviceInfo.deviceId})" },
                    rssi = deviceInfo.rssi,
                    isPolar = true
                )
            )
        }

        awaitClose {
            if (_connectionState.value is ConnectionState.Scanning) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    override suspend fun stopScan() {
        if (_connectionState.value is ConnectionState.Scanning) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    override suspend fun connect(deviceId: String) {
        try {
            _connectionState.value = ConnectionState.Connecting
            connectedDeviceId = deviceId
            api.connectToDevice(deviceId)
        } catch (e: Exception) {
            Log.e(TAG, "Connect error", e)
            _connectionState.value = ConnectionState.Error("Connection failed: ${e.message}")
        }
    }

    override suspend fun disconnect() {
        connectedDeviceId?.let { deviceId ->
            try {
                api.disconnectFromDevice(deviceId)
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error", e)
            }
        }
        connectedDeviceId = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun streamHr(): Flow<HrSample> {
        val deviceId = connectedDeviceId
            ?: throw IllegalStateException("No device connected")

        return flow {
            api.startHrStreaming(deviceId).collect { hrData ->
                hrData.samples.forEach { sample ->
                    emit(
                        HrSample(
                            hr = sample.hr,
                            rrsMs = sample.rrsMs,
                            timestamp = System.currentTimeMillis(),
                            contactDetected = sample.contactStatus
                        )
                    )
                }
            }
        }
    }

    override fun streamEcg(): Flow<DomainEcgSample> {
        val deviceId = connectedDeviceId
            ?: throw IllegalStateException("No device connected")

        return flow {
            val settings = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ECG)
            api.startEcgStreaming(deviceId, settings.maxSettings()).collect { ecgData ->
                ecgData.samples.forEach { sample ->
                    // PolarEcgDataSample is sealed; PolarEcgSample subclass has .voltage
                    if (sample is PolarEcgSample) {
                        emit(
                            DomainEcgSample(
                                timestamp = sample.timeStamp,
                                voltage = sample.voltage
                            )
                        )
                    }
                }
            }
        }
    }

    override fun streamAcc(): Flow<AccSample> {
        val deviceId = connectedDeviceId
            ?: throw IllegalStateException("No device connected")

        return flow {
            val settings = api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.ACC)
            api.startAccStreaming(deviceId, settings.maxSettings()).collect { accData ->
                accData.samples.forEach { sample ->
                    emit(
                        AccSample(
                            timestamp = sample.timeStamp,
                            x = sample.x,
                            y = sample.y,
                            z = sample.z
                        )
                    )
                }
            }
        }
    }
}
