package com.hrv.biofeedback.presentation.morning

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.hrv.biofeedback.presentation.common.HrTraceChart
import com.hrv.biofeedback.presentation.common.MetricCard
import com.hrv.biofeedback.presentation.theme.CardDark
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.CoherenceLow
import com.hrv.biofeedback.presentation.theme.CoherenceMedium
import com.hrv.biofeedback.presentation.theme.LfPowerColor
import com.hrv.biofeedback.presentation.theme.Primary
import com.hrv.biofeedback.presentation.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningCheckScreen(
    onBack: () -> Unit,
    viewModel: MorningCheckViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val hrHistory by viewModel.hrHistory.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Morning Check") },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                CheckState.NOT_STARTED -> {
                    Text(
                        text = "2-Minute Baseline",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "How to measure",
                                style = MaterialTheme.typography.titleMedium,
                                color = Primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "1. Lie down or sit comfortably\n" +
                                        "2. Put on your Polar H10 chest strap\n" +
                                        "3. Breathe naturally — do NOT pace your breathing\n" +
                                        "4. Stay still and relaxed for 2 minutes\n\n" +
                                        "Best done first thing in the morning before getting out of bed. " +
                                        "This establishes your resting HRV baseline and tracks " +
                                        "how your biofeedback training improves autonomic function over time.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (connectionState !is ConnectionState.Connected) {
                        Text(
                            "Connect your sensor first",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Button(onClick = { viewModel.startCheck() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start 2-Min Check")
                        }
                    }

                    // Show trend if we have previous checks
                    if (trend.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Recent History",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                        trend.forEach { check ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                colors = CardDefaults.cardColors(containerColor = CardDark)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = SimpleDateFormat("MMM d", Locale.getDefault())
                                            .format(Date(check.startTime)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = "RMSSD %.1f".format(check.averageRmssd),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Primary
                                    )
                                    Text(
                                        text = "HR %.0f".format(check.averageHr),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ChartLine
                                    )
                                    Text(
                                        text = "SDNN %.1f".format(check.averageSdnn),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                CheckState.RECORDING -> {
                    // Timer
                    val remaining = MorningCheckViewModel.CHECK_DURATION_SECONDS - elapsed
                    Text(
                        text = "%d:%02d".format(remaining / 60, remaining % 60),
                        style = MaterialTheme.typography.displayLarge,
                        color = Primary,
                        fontSize = 48.sp
                    )
                    LinearProgressIndicator(
                        progress = { elapsed.toFloat() / MorningCheckViewModel.CHECK_DURATION_SECONDS },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = Primary
                    )
                    Text(
                        text = "Breathe naturally and stay still",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live HR display
                    Text(
                        text = "${metrics.hr}",
                        style = MaterialTheme.typography.displayLarge,
                        color = ChartLine,
                        fontWeight = FontWeight.Bold,
                        fontSize = 56.sp
                    )
                    Text("bpm", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

                    Spacer(modifier = Modifier.height(12.dp))

                    // HR trace (no target wave — natural breathing)
                    HrTraceChart(
                        hrData = hrHistory,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live metrics building up
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
                }

                CheckState.COMPLETED -> {
                    val r = result ?: return@Column

                    Text(
                        text = "Morning Baseline",
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
                            .format(Date()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary metric: RMSSD with change indicator
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("RMSSD", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                            Text(
                                text = "%.1f ms".format(r.rmssd),
                                style = MaterialTheme.typography.displayLarge,
                                color = Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 48.sp
                            )
                            r.rmssdChange?.let { change ->
                                val color = if (change >= 0) CoherenceHigh else CoherenceLow
                                val arrow = if (change >= 0) "\u2191" else "\u2193"
                                Text(
                                    text = "$arrow %.1f%% vs 7-day avg".format(change),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = color,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (r.rmssdChange == null) {
                                Text(
                                    text = "First check — keep measuring daily to see trends",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // All metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricCard("HR", "${r.hr}", "bpm", Modifier.weight(1f), valueColor = ChartLine)
                        Spacer(Modifier.width(8.dp))
                        MetricCard("SDNN", "%.1f".format(r.sdnn), "ms", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("pNN50", "%.1f".format(r.pnn50), "%", Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricCard("SD1", "%.1f".format(r.sd1), "ms", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("SD2", "%.1f".format(r.sd2), "ms", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        MetricCard("DFA \u03B11", "%.2f".format(r.dfaAlpha1), "", Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Interpretation
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "What this means",
                                style = MaterialTheme.typography.titleMedium,
                                color = Primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = buildInterpretation(r),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    r.hrChange?.let { hrChange ->
                        Spacer(modifier = Modifier.height(4.dp))
                        val hrColor = if (hrChange <= 0) CoherenceHigh else CoherenceMedium
                        Text(
                            text = "Resting HR: %s%.1f%% vs 7-day avg".format(
                                if (hrChange <= 0) "" else "+", hrChange
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = hrColor
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Check #${r.totalChecks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

private fun buildInterpretation(r: MorningCheckResult): String {
    val parts = mutableListOf<String>()

    // RMSSD interpretation (primary metric for vagal tone)
    when {
        r.rmssd >= 50 -> parts.add("Your RMSSD is in the healthy range (>50 ms), indicating good parasympathetic tone.")
        r.rmssd >= 30 -> parts.add("Your RMSSD is moderate. Regular biofeedback training can help improve this.")
        else -> parts.add("Your RMSSD is below average. This is a good reason to continue biofeedback training.")
    }

    // DFA alpha1 interpretation
    when {
        r.dfaAlpha1 in 0.75..1.25 -> parts.add("DFA \u03B11 near 1.0 shows healthy fractal dynamics.")
        r.dfaAlpha1 > 1.5 -> parts.add("DFA \u03B11 is elevated — may indicate reduced complexity.")
        r.dfaAlpha1 in 0.01..0.74 -> parts.add("DFA \u03B11 is low — suggests increased randomness in HR patterns.")
    }

    // Change interpretation
    r.rmssdChange?.let { change ->
        when {
            change > 10 -> parts.add("RMSSD is notably higher than your 7-day average — good recovery!")
            change < -10 -> parts.add("RMSSD is lower than your average — you may be fatigued or stressed. Consider a lighter day.")
            else -> parts.add("RMSSD is stable compared to your recent average.")
        }
    }

    return parts.joinToString("\n\n")
}
