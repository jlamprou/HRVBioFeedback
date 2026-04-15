package com.hrv.biofeedback.presentation.live

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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrv.biofeedback.domain.model.ConnectionState
import com.hrv.biofeedback.presentation.common.CoherenceIndicator
import com.hrv.biofeedback.presentation.common.HrTraceChart
import com.hrv.biofeedback.presentation.common.SignalQualityBar
import com.hrv.biofeedback.presentation.common.MetricCard
import com.hrv.biofeedback.presentation.theme.CardDark
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.CoherenceLow
import com.hrv.biofeedback.presentation.theme.CoherenceMedium
import com.hrv.biofeedback.presentation.theme.HfPowerColor
import com.hrv.biofeedback.presentation.theme.LfPowerColor
import com.hrv.biofeedback.presentation.theme.Primary
import com.hrv.biofeedback.presentation.theme.Secondary
import com.hrv.biofeedback.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMetricsScreen(
    onBack: () -> Unit,
    viewModel: LiveMetricsViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Metrics") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopStreaming()
                        onBack()
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
            // Start/Stop
            if (connectionState !is ConnectionState.Connected) {
                Text(
                    "Connect a sensor first",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 32.dp)
                )
            } else if (!isStreaming) {
                Button(onClick = { viewModel.startStreaming() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Live View")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "All Polar H10 streams: HR + ECG + ACC",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                // HR Display
                Text(
                    text = "${metrics.hr}",
                    style = MaterialTheme.typography.displayLarge,
                    color = ChartLine,
                    fontWeight = FontWeight.Bold,
                    fontSize = 64.sp
                )
                Text("bpm", style = MaterialTheme.typography.titleMedium, color = TextSecondary)

                // Signal quality
                SignalQualityBar(report = viewModel.getSignalQuality())

                Spacer(modifier = Modifier.height(8.dp))

                // HR trace chart
                val hrHistory by viewModel.hrHistory.collectAsStateWithLifecycle()
                HrTraceChart(
                    hrData = hrHistory,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                CoherenceIndicator(level = metrics.coherenceLevel)

                Spacer(modifier = Modifier.height(16.dp))

                // === TIME DOMAIN ===
                SectionHeader("Time Domain")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard("RMSSD", "%.1f".format(metrics.rmssd), "ms", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MetricCard("SDNN", "%.1f".format(metrics.sdnn), "ms", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MetricCard("pNN50", "%.1f".format(metrics.pnn50), "%", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // === FREQUENCY DOMAIN ===
                SectionHeader("Frequency Domain (AR-16 Burg)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard("LF Power", "%.0f".format(metrics.lfPower), "ms\u00B2",
                        Modifier.weight(1f), valueColor = LfPowerColor)
                    Spacer(Modifier.width(8.dp))
                    MetricCard("HF Power", "%.0f".format(metrics.hfPower), "ms\u00B2",
                        Modifier.weight(1f), valueColor = HfPowerColor)
                    Spacer(Modifier.width(8.dp))
                    MetricCard("LF/HF", "%.2f".format(metrics.lfHfRatio), "",
                        Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard("Total", "%.0f".format(metrics.totalPower), "ms\u00B2",
                        Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MetricCard("Peak Freq", "%.3f".format(metrics.peakFrequency), "Hz",
                        Modifier.weight(1f), valueColor = Primary)
                    Spacer(Modifier.width(8.dp))
                    MetricCard("Amplitude", "%.1f".format(metrics.peakTroughAmplitude), "bpm",
                        Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // === COHERENCE ===
                SectionHeader("HeartMath Coherence")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard("Score", "%.2f".format(metrics.coherenceScore), "",
                        Modifier.weight(1f), valueColor = when (metrics.coherenceLevel) {
                            com.hrv.biofeedback.domain.model.CoherenceLevel.HIGH -> CoherenceHigh
                            com.hrv.biofeedback.domain.model.CoherenceLevel.MEDIUM -> CoherenceMedium
                            com.hrv.biofeedback.domain.model.CoherenceLevel.LOW -> CoherenceLow
                        })
                    Spacer(Modifier.width(8.dp))
                    MetricCard("CR Coh", "%.2f".format(metrics.cardiorespCoherence), "",
                        Modifier.weight(1f), valueColor = Secondary)
                    Spacer(Modifier.width(8.dp))
                    MetricCard("Phase", "%.1f".format(metrics.cardiorespPhase), "\u00B0",
                        Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // === NONLINEAR ===
                SectionHeader("Nonlinear (Poincar\u00E9, DFA, SampEn)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard("SD1", "%.1f".format(metrics.sd1), "ms", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MetricCard("SD2", "%.1f".format(metrics.sd2), "ms", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MetricCard("DFA \u03B11", "%.2f".format(metrics.dfaAlpha1), "",
                        Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard("SampEn", "%.3f".format(metrics.sampleEntropy), "",
                        Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MetricCard("SD1/SD2", "%.2f".format(
                        if (metrics.sd2 > 0) metrics.sd1 / metrics.sd2 else 0.0
                    ), "", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    MetricCard("Breath", "%.1f".format(metrics.breathingRate), "bpm",
                        Modifier.weight(1f), valueColor = Primary)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Stop button
                OutlinedButton(
                    onClick = { viewModel.stopStreaming() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
