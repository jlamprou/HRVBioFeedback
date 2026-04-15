package com.hrv.biofeedback.presentation.report

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrv.biofeedback.domain.model.SessionType
import com.hrv.biofeedback.presentation.common.MetricCard
import com.hrv.biofeedback.presentation.theme.CardDark
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.HfPowerColor
import com.hrv.biofeedback.presentation.theme.LfPowerColor
import com.hrv.biofeedback.presentation.theme.Primary
import com.hrv.biofeedback.presentation.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionReportScreen(
    onBack: () -> Unit,
    viewModel: SessionReportViewModel = hiltViewModel()
) {
    val sessionDetail by viewModel.sessionDetail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Report") },
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
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
            return@Scaffold
        }

        val detail = sessionDetail ?: return@Scaffold
        val summary = detail.summary

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = summary.type.name.lowercase().replaceFirstChar { it.uppercase() } +
                                " Session",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = SimpleDateFormat(
                            "EEEE, MMMM d, yyyy 'at' h:mm a",
                            Locale.getDefault()
                        ).format(Date(summary.startTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${summary.durationSeconds / 60} min ${summary.durationSeconds % 60}s",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )

                    // RF result for assessment sessions
                    if (summary.type == SessionType.ASSESSMENT && summary.rfResult != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Resonance Frequency",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = "%.1f bpm".format(summary.rfResult),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = CoherenceHigh,
                            fontSize = 48.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metrics summary
            Text(
                text = "Metrics Summary",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard(
                    label = "Avg HR",
                    value = "%.0f".format(summary.averageHr),
                    unit = "bpm",
                    valueColor = ChartLine,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard(
                    label = "Coherence",
                    value = "%.2f".format(summary.averageCoherence),
                    valueColor = CoherenceHigh,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard(
                    label = "Amplitude",
                    value = "%.1f".format(summary.averagePeakTrough),
                    unit = "bpm",
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
                    value = "%.1f".format(summary.averageRmssd),
                    unit = "ms",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard(
                    label = "SDNN",
                    value = "%.1f".format(summary.averageSdnn),
                    unit = "ms",
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard(
                    label = "LF/HF",
                    value = "%.1f".format(
                        if (summary.averageHfPower > 0) summary.averageLfPower / summary.averageHfPower
                        else 0.0
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Frequency domain
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard("LF Power", "%.0f".format(summary.averageLfPower), "ms\u00B2",
                    Modifier.weight(1f), valueColor = LfPowerColor)
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard("HF Power", "%.0f".format(summary.averageHfPower), "ms\u00B2",
                    Modifier.weight(1f), valueColor = HfPowerColor)
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard("LF/HF", "%.2f".format(summary.averageLfHfRatio), "",
                    Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nonlinear metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard("SD1", "%.1f".format(summary.averageSd1), "ms", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard("SD2", "%.1f".format(summary.averageSd2), "ms", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard("DFA \u03B11", "%.2f".format(summary.averageDfaAlpha1), "",
                    Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Entropy, respiratory, signal quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard("SampEn", "%.3f".format(summary.averageSampleEntropy), "",
                    Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard("Breathing", "%.1f".format(
                    if (summary.detectedBreathingRate > 0) summary.detectedBreathingRate
                    else summary.breathingRate
                ), "bpm", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                MetricCard("CR Coh", "%.2f".format(summary.averageCardiorespCoherence), "",
                    Modifier.weight(1f))
            }

            if (summary.artifactRatePercent > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Artifact rate: %.1f%%".format(summary.artifactRatePercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (summary.artifactRatePercent > 5) HfPowerColor else com.hrv.biofeedback.presentation.theme.TextSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // RF Assessment step results
            if (detail.rfStepResults != null && detail.rfStepResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "RF Assessment Results",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                detail.rfStepResults.forEach { step ->
                    val isOptimal = step.breathingRate == summary.rfResult
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isOptimal) Primary.copy(alpha = 0.15f)
                            else CardDark
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "%.1f bpm".format(step.breathingRate),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isOptimal) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isOptimal) Primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Score: %.2f".format(step.combinedScore),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isOptimal) Primary else TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "LF: %.0f".format(step.lfPower),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Amp: %.1f".format(step.peakTroughAmplitude),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Phase: %.2f".format(step.phaseSync),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "Coh: %.2f".format(step.coherenceScore),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Metrics timeline summary
            if (detail.metricsTimeline.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Session Timeline",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val timeline = detail.metricsTimeline
                        val hrRange = timeline.filter { it.hr > 0 }
                        if (hrRange.isNotEmpty()) {
                            Text(
                                text = "HR Range: ${hrRange.minOf { it.hr }} - ${hrRange.maxOf { it.hr }} bpm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        val coherenceValues = timeline.filter { it.coherenceScore > 0 }
                        if (coherenceValues.isNotEmpty()) {
                            // HeartMath High coherence threshold = 1.8 (Challenge Level 1)
                            val highCoherencePct = coherenceValues.count { it.coherenceScore >= 1.8 } * 100.0 / coherenceValues.size
                            Text(
                                text = "Time in High Coherence: %.0f%%".format(highCoherencePct),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoherenceHigh
                            )
                        }
                        Text(
                            text = "Data points: ${detail.rrIntervals.size} RR intervals",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
