package com.hrv.biofeedback.presentation.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.presentation.common.BreathingPacer
import com.hrv.biofeedback.presentation.common.HrTraceChart
import com.hrv.biofeedback.presentation.common.CoherenceIndicator
import com.hrv.biofeedback.presentation.common.MetricCard
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.CoherenceLow
import com.hrv.biofeedback.presentation.theme.CoherenceMedium
import com.hrv.biofeedback.presentation.theme.LfPowerColor
import com.hrv.biofeedback.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingSessionScreen(
    onBack: () -> Unit,
    onComplete: (Long) -> Unit,
    viewModel: TrainingSessionViewModel = hiltViewModel()
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val breathingRate by viewModel.breathingRate.collectAsStateWithLifecycle()
    val savedSessionId by viewModel.savedSessionId.collectAsStateWithLifecycle()
    val settings: com.hrv.biofeedback.data.local.preferences.AppSettings by viewModel.settings.collectAsStateWithLifecycle()
    val assessedRf by viewModel.assessedRf.collectAsStateWithLifecycle()

    var showStopDialog by remember { mutableStateOf(false) }

    // Navigate to report when session is saved
    LaunchedEffect(savedSessionId) {
        savedSessionId?.let { onComplete(it) }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("End Session?") },
            text = { Text("Your session data will be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showStopDialog = false
                    viewModel.stopSession()
                }) { Text("End Session") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Training Session") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (sessionState == SessionState.RUNNING || sessionState == SessionState.PAUSED) {
                            showStopDialog = true
                        } else {
                            onBack()
                        }
                    }) {
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                connectionState !is ConnectionState.Connected && sessionState == SessionState.NOT_STARTED -> {
                    // Not connected
                    Text(
                        text = "Please connect a heart rate sensor first",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 64.dp)
                    )
                }

                sessionState == SessionState.NOT_STARTED -> {
                    // Ready to start
                    Text(
                        text = "Ready to Train",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (assessedRf != null) {
                        Text(
                            text = "Starting at your RF: %.1f bpm".format(assessedRf),
                            style = MaterialTheme.typography.bodyLarge,
                            color = com.hrv.biofeedback.presentation.theme.CoherenceHigh
                        )
                    } else {
                        Text(
                            text = "No RF assessed yet — using %.1f bpm default".format(settings.defaultBreathingRate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Smart mode gently adjusts breathing rate based on\nyour live HRV response (experimental feature)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { viewModel.startSession() },
                        modifier = Modifier.fillMaxWidth(0.6f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Session")
                    }
                }

                sessionState == SessionState.RUNNING || sessionState == SessionState.PAUSED -> {
                    // Active session

                    // Timer
                    val minutes = elapsedSeconds / 60
                    val seconds = elapsedSeconds % 60
                    val totalMinutes = viewModel.sessionDurationMinutes
                    Text(
                        text = "%02d:%02d / %02d:00".format(minutes, seconds, totalMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Light
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Breathing rate
                    Text(
                        text = "%.1f breaths/min".format(breathingRate),
                        style = MaterialTheme.typography.labelLarge,
                        color = LfPowerColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Breathing pacer with user settings
                    BreathingPacer(
                        breathingRate = breathingRate,
                        inhaleRatio = settings.inhaleRatio.toFloat(),
                        vibrationEnabled = settings.vibrationEnabled,
                        audioCuesEnabled = settings.audioCuesEnabled,
                        audioVolume = settings.audioVolume,
                        modifier = Modifier
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // HR trace with target trajectory — the core biofeedback visual
                    val hrHistory by viewModel.hrHistory.collectAsStateWithLifecycle()
                    HrTraceChart(
                        hrData = hrHistory,
                        breathingRateBpm = breathingRate,
                        peakTroughAmplitude = metrics.peakTroughAmplitude,
                        inhaleRatio = settings.inhaleRatio.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Coherence indicator
                    CoherenceIndicator(level = metrics.coherenceLevel)

                    // Coaching tip
                    val tip by viewModel.coachingTip.collectAsStateWithLifecycle()
                    tip?.let { currentTip ->
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = when (currentTip.type) {
                                    com.hrv.biofeedback.domain.usecase.training.BreathingCoach.TipType.POSITIVE ->
                                        com.hrv.biofeedback.presentation.theme.CoherenceHigh.copy(alpha = 0.12f)
                                    com.hrv.biofeedback.domain.usecase.training.BreathingCoach.TipType.GUIDANCE ->
                                        com.hrv.biofeedback.presentation.theme.LfPowerColor.copy(alpha = 0.12f)
                                    com.hrv.biofeedback.domain.usecase.training.BreathingCoach.TipType.ENCOURAGE ->
                                        com.hrv.biofeedback.presentation.theme.Primary.copy(alpha = 0.12f)
                                }
                            )
                        ) {
                            Text(
                                text = currentTip.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (currentTip.type) {
                                    com.hrv.biofeedback.domain.usecase.training.BreathingCoach.TipType.POSITIVE ->
                                        com.hrv.biofeedback.presentation.theme.CoherenceHigh
                                    com.hrv.biofeedback.domain.usecase.training.BreathingCoach.TipType.GUIDANCE ->
                                        com.hrv.biofeedback.presentation.theme.LfPowerColor
                                    com.hrv.biofeedback.domain.usecase.training.BreathingCoach.TipType.ENCOURAGE ->
                                        com.hrv.biofeedback.presentation.theme.Primary
                                },
                                modifier = Modifier.padding(12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Metrics row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricCard(
                            label = "HR",
                            value = "${metrics.hr}",
                            unit = "bpm",
                            valueColor = ChartLine,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricCard(
                            label = "LF Power",
                            value = "%.0f".format(metrics.lfPower),
                            unit = "ms\u00B2",
                            valueColor = LfPowerColor,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricCard(
                            label = "Coherence",
                            value = "%.2f".format(metrics.coherenceScore),
                            valueColor = when (metrics.coherenceLevel) {
                                com.hrv.biofeedback.domain.model.CoherenceLevel.HIGH -> CoherenceHigh
                                com.hrv.biofeedback.domain.model.CoherenceLevel.MEDIUM -> CoherenceMedium
                                com.hrv.biofeedback.domain.model.CoherenceLevel.LOW -> CoherenceLow
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricCard(
                            label = "RMSSD",
                            value = "%.1f".format(metrics.rmssd),
                            unit = "ms",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricCard(
                            label = "Amplitude",
                            value = "%.1f".format(metrics.peakTroughAmplitude),
                            unit = "bpm",
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MetricCard(
                            label = "SDNN",
                            value = "%.1f".format(metrics.sdnn),
                            unit = "ms",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (sessionState == SessionState.RUNNING) {
                            OutlinedButton(onClick = { viewModel.pauseSession() }) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause")
                            }
                        } else {
                            Button(onClick = { viewModel.resumeSession() }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resume")
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { showStopDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop")
                        }
                    }
                }

                sessionState == SessionState.COMPLETED -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Saving session...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
