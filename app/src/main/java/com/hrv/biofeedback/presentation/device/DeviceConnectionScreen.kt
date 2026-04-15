package com.hrv.biofeedback.presentation.device

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.presentation.theme.CardDark
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.Primary
import com.hrv.biofeedback.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DeviceConnectionScreen(
    onBack: () -> Unit,
    viewModel: DeviceConnectionViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val devices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val currentHr by viewModel.currentHr.collectAsStateWithLifecycle()
    val batteryLevel by viewModel.batteryLevel.collectAsStateWithLifecycle()

    val blePermissions = rememberMultiplePermissionsState(
        permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Device") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Connection status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            is ConnectionState.Connected -> Icons.Default.BluetoothConnected
                            is ConnectionState.Scanning -> Icons.Default.BluetoothSearching
                            is ConnectionState.Connecting -> Icons.Default.BluetoothSearching
                            else -> Icons.Default.Bluetooth
                        },
                        contentDescription = null,
                        tint = when (connectionState) {
                            is ConnectionState.Connected -> ChartLine
                            is ConnectionState.Error -> MaterialTheme.colorScheme.error
                            else -> Primary
                        },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (val state = connectionState) {
                                is ConnectionState.Disconnected -> "Not Connected"
                                is ConnectionState.Scanning -> "Scanning..."
                                is ConnectionState.Connecting -> "Connecting..."
                                is ConnectionState.Connected -> "Connected to ${state.deviceName}"
                                is ConnectionState.Error -> state.message
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (connectionState is ConnectionState.Connected && currentHr > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$currentHr bpm",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                                if (batteryLevel in 0..100) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "\uD83D\uDD0B $batteryLevel%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (batteryLevel <= 20) MaterialTheme.colorScheme.error
                                               else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions
            when (connectionState) {
                is ConnectionState.Connected -> {
                    OutlinedButton(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disconnect")
                    }
                }
                is ConnectionState.Connecting -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                else -> {
                    if (!blePermissions.allPermissionsGranted) {
                        Button(
                            onClick = { blePermissions.launchMultiplePermissionRequest() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Bluetooth Permissions")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startScan() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (connectionState is ConnectionState.Scanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning...")
                            } else {
                                Text("Scan for Devices")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device list
            if (devices.isNotEmpty()) {
                Text(
                    text = "Available Devices",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.connect(device.deviceId) },
                            colors = CardDefaults.cardColors(containerColor = CardDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = Primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = device.deviceId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = "${device.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
