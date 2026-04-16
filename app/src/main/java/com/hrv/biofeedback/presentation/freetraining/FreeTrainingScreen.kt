package com.hrv.biofeedback.presentation.freetraining

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.presentation.common.BiofeedbackIndicator
import com.hrv.biofeedback.presentation.common.HrTraceChart
import com.hrv.biofeedback.presentation.common.MetricCard
import com.hrv.biofeedback.presentation.common.SignalQualityBar
import com.hrv.biofeedback.presentation.theme.CardDark
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.LfPowerColor
import com.hrv.biofeedback.presentation.theme.Primary
import com.hrv.biofeedback.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeTrainingScreen(
    onBack: () -> Unit,
    onComplete: (Long) -> Unit,
    viewModel: FreeTrainingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val currentHr by viewModel.currentHr.collectAsStateWithLifecycle()
    val hrTrend by viewModel.hrTrend.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val hrHistory by viewModel.hrHistory.collectAsStateWithLifecycle()
    val bestAmplitude by viewModel.bestAmplitude.collectAsStateWithLifecycle()
    val savedSessionId by viewModel.savedSessionId.collectAsStateWithLifecycle()

    LaunchedEffect(savedSessionId) {
        savedSessionId?.let { onComplete(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Training") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state == FreeSessionState.RUNNING) viewModel.stopSession()
                        else onBack()
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
            when (state) {
                FreeSessionState.NOT_STARTED -> {
                    if (connectionState !is ConnectionState.Connected) {
                        Text("Connect a sensor first", color = TextSecondary,
                            modifier = Modifier.padding(top = 32.dp))
                    } else {
                        Text(
                            text = "Freeform Biofeedback",
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardDark)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("How it works", style = MaterialTheme.typography.titleMedium, color = Primary)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "No breathing pacer — you guide yourself using the HR trace.\n\n" +
                                            "1. Watch your heart rate line on the graph\n" +
                                            "2. Inhale slowly when HR is rising\n" +
                                            "3. Exhale slowly when HR is falling\n" +
                                            "4. Goal: make the waves as large as possible\n\n" +
                                            "This is the advanced phase of the Lehrer protocol " +
                                            "(Lehrer & Gevirtz 2014). It develops interoceptive " +
                                            "awareness and naturally finds your current resonance frequency.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { viewModel.startSession() }) {
                            Text("Start Advanced Training")
                        }
                    }
                }

                FreeSessionState.RUNNING -> {
                    // Timer
                    val min = elapsed / 60
                    val sec = elapsed % 60
                    Text(
                        text = "%02d:%02d".format(min, sec),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Light
                    )

                    // Signal quality
                    SignalQualityBar(report = viewModel.getSignalQuality())

                    Spacer(Modifier.height(8.dp))

                    // HR centerpiece with trend arrow (synced with graph)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${currentHr}",
                            style = MaterialTheme.typography.displayLarge,
                            color = ChartLine,
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        // Trend arrow: sized to match HR text, color-coded
                        val (arrow, arrowColor, arrowLabel) = when (hrTrend) {
                            1 -> Triple("\u2191", CoherenceHigh, "Inhale")    // ↑ green
                            -1 -> Triple("\u2193", LfPowerColor, "Exhale")   // ↓ blue
                            else -> Triple("\u2022", TextSecondary, "")       // · gray dot at peak/trough
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = arrow,
                                fontSize = 40.sp,
                                color = arrowColor,
                                fontWeight = FontWeight.Bold
                            )
                            if (arrowLabel.isNotEmpty()) {
                                Text(
                                    text = arrowLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = arrowColor
                                )
                            }
                        }
                    }
                    Text("bpm", style = MaterialTheme.typography.titleMedium, color = TextSecondary)

                    Spacer(Modifier.height(8.dp))

                    // THE MAIN BIOFEEDBACK: large HR trace
                    HrTraceChart(
                        hrData = hrHistory,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Inhale when the line rises \u2191  Exhale when it falls \u2193",
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    // Biofeedback indicator
                    BiofeedbackIndicator(metrics = metrics)

                    Spacer(Modifier.height(8.dp))

                    // Best amplitude this session
                    if (bestAmplitude > 0) {
                        Text(
                            text = "Session best: %.1f bpm amplitude".format(bestAmplitude),
                            style = MaterialTheme.typography.labelMedium,
                            color = CoherenceHigh
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Key metrics
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricCard("Amplitude", "%.1f".format(metrics.peakTroughAmplitude), "bpm",
                            Modifier.weight(1f), valueColor = CoherenceHigh)
                        Spacer(Modifier.width(8.dp))
                        MetricCard("LF Power", "%.0f".format(metrics.lfPower), "ms\u00B2",
                            Modifier.weight(1f), valueColor = LfPowerColor)
                        Spacer(Modifier.width(8.dp))
                        MetricCard("HF Power", "%.0f".format(metrics.hfPower), "ms\u00B2", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricCard("RMSSD", "%.1f".format(metrics.rmssd), "ms", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("SDNN", "%.1f".format(metrics.sdnn), "ms", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("pNN50", "%.1f".format(metrics.pnn50), "%", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricCard("LF/HF", "%.2f".format(metrics.lfHfRatio), "", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("Peak Freq", "%.3f".format(metrics.peakFrequency), "Hz",
                            Modifier.weight(1f), valueColor = Primary)
                        Spacer(Modifier.width(8.dp))
                        MetricCard("Breath", "%.1f".format(metrics.breathingRate), "bpm", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricCard("SD1", "%.1f".format(metrics.sd1), "ms", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("SD2", "%.1f".format(metrics.sd2), "ms", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("DFA \u03B11", "%.2f".format(metrics.dfaAlpha1), "", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MetricCard("SampEn", "%.3f".format(metrics.sampleEntropy), "", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("CR Coh", "%.2f".format(metrics.cardiorespCoherence), "", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("SD1/SD2", "%.2f".format(
                            if (metrics.sd2 > 0) metrics.sd1 / metrics.sd2 else 0.0
                        ), "", Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.stopSession() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }

                FreeSessionState.COMPLETED -> {
                    Text("Saving session...", color = TextSecondary)
                }
            }
        }
    }
}
