package com.hrv.biofeedback.presentation.evaluation

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrv.biofeedback.presentation.common.MetricCard
import com.hrv.biofeedback.presentation.theme.CardDark
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.CoherenceLow
import com.hrv.biofeedback.presentation.theme.CoherenceMedium
import com.hrv.biofeedback.presentation.theme.LfPowerColor
import com.hrv.biofeedback.presentation.theme.Primary
import com.hrv.biofeedback.presentation.theme.Secondary
import com.hrv.biofeedback.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationScreen(
    onBack: () -> Unit,
    viewModel: EvaluationViewModel = hiltViewModel()
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full Evaluation") },
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
        if (isLoading || report == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator(color = Primary) }
            return@Scaffold
        }

        val r = report!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // === HEADER ===
            Text(
                text = "HRV Health Report",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${r.daysSinceFirstSession} days of tracking \u2022 ${r.totalSessions} sessions",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // === CURRENT BASELINE ===
            SectionTitle("Current Baseline (Morning Check)")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                if (r.currentRmssd != null) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Resting RMSSD", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Text(
                            text = "%.1f ms".format(r.currentRmssd),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 48.sp,
                            color = when {
                                r.currentRmssd >= 50 -> CoherenceHigh
                                r.currentRmssd >= 30 -> CoherenceMedium
                                else -> CoherenceLow
                            }
                        )
                        // Period averages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            r.weekRmssdAvg?.let {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("%.1f".format(it), fontWeight = FontWeight.Bold, color = Primary)
                                    Text("7-day avg", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                            r.monthRmssdAvg?.let {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("%.1f".format(it), fontWeight = FontWeight.Bold, color = Primary)
                                    Text("30-day avg", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                            r.allTimeRmssdAvg?.let {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("%.1f".format(it), fontWeight = FontWeight.Bold, color = Primary)
                                    Text("All-time", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No morning checks yet. Start daily 2-minute checks to establish your baseline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (r.currentRestingHr != null || r.currentSdnn != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    r.currentRestingHr?.let {
                        MetricCard("Resting HR", "%.0f".format(it), "bpm", Modifier.weight(1f), valueColor = ChartLine)
                    }
                    if (r.currentRestingHr != null && r.currentSdnn != null) Spacer(Modifier.width(8.dp))
                    r.currentSdnn?.let {
                        MetricCard("SDNN", "%.1f".format(it), "ms", Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === RESONANCE FREQUENCY ===
            SectionTitle("Resonance Frequency")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (r.currentRf != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "%.1f".format(r.currentRf),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                color = CoherenceHigh,
                                fontSize = 48.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("breaths/min", color = TextSecondary)
                                Text(
                                    "${r.totalAssessments} assessment${if (r.totalAssessments != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        if (r.rfHistory.size > 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                            Text("RF History:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                text = r.rfHistory.joinToString(" \u2192 ") { "%.1f".format(it.second) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = LfPowerColor
                            )
                        }
                    } else {
                        Text(
                            "No RF assessment yet. Run the Find RF protocol to determine your personal resonance frequency.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === TRAINING STATS ===
            SectionTitle("Training Progress")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard("Sessions", "${trainingSessions(r)}", "", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                MetricCard("Total Time", "${r.totalTrainingMinutes}", "min", Modifier.weight(1f), valueColor = Primary)
                Spacer(Modifier.width(8.dp))
                MetricCard("Avg Coherence", r.avgTrainingCoherence?.let { "%.2f".format(it) } ?: "--", "",
                    Modifier.weight(1f), valueColor = CoherenceHigh)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard("Best Coherence", r.bestTrainingCoherence?.let { "%.2f".format(it) } ?: "--", "",
                    Modifier.weight(1f), valueColor = CoherenceHigh)
                Spacer(Modifier.width(8.dp))
                MetricCard("Avg Amplitude", r.avgTrainingAmplitude?.let { "%.1f".format(it) } ?: "--", "bpm",
                    Modifier.weight(1f), valueColor = LfPowerColor)
                Spacer(Modifier.width(8.dp))
                MetricCard("Checks", "${r.totalMorningChecks}", "", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === AGE/SEX-ADJUSTED METRIC ASSESSMENTS ===
            if (r.metricAssessments.isNotEmpty()) {
                SectionTitle(
                    if (r.userAge > 0) "Your Metrics vs Age ${r.userAge} ${r.userSex.replaceFirstChar { it.uppercase() }} Norms"
                    else "Your Metrics vs Population Norms"
                )
                r.metricAssessments.forEach { assessment ->
                    MetricAssessmentCard(assessment)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else if (r.userAge == 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = CoherenceMedium.copy(alpha = 0.10f)
                    )
                ) {
                    Text(
                        text = "Set your birth year and sex in Settings to get personalized, " +
                                "age-adjusted metric assessments with scientific norm comparisons.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoherenceMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // === INSIGHTS ===
            SectionTitle("Insights & Recommendations")
            r.insights.forEach { insight ->
                InsightCard(insight)
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricAssessmentCard(
    assessment: com.hrv.biofeedback.domain.usecase.analysis.HrvNorms.MetricAssessment
) {
    val statusColor = when (assessment.status) {
        com.hrv.biofeedback.domain.usecase.analysis.HrvNorms.Status.EXCELLENT -> CoherenceHigh
        com.hrv.biofeedback.domain.usecase.analysis.HrvNorms.Status.GOOD -> CoherenceHigh.copy(alpha = 0.8f)
        com.hrv.biofeedback.domain.usecase.analysis.HrvNorms.Status.AVERAGE -> CoherenceMedium
        com.hrv.biofeedback.domain.usecase.analysis.HrvNorms.Status.BELOW_AVERAGE -> CoherenceLow.copy(alpha = 0.7f)
        com.hrv.biofeedback.domain.usecase.analysis.HrvNorms.Status.CONCERNING -> CoherenceLow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: metric name, value, status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = assessment.metric,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (assessment.normRange.unit.isNotEmpty())
                            "%.1f %s".format(assessment.value, assessment.normRange.unit)
                        else "%.2f".format(assessment.value),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = assessment.percentile.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Norm range bar
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Age norm: %.0f – %.0f – %.0f %s (low – avg – high)".format(
                    assessment.normRange.low, assessment.normRange.mean,
                    assessment.normRange.high, assessment.normRange.unit
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            // Explanation
            Spacer(Modifier.height(6.dp))
            Text(
                text = assessment.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun InsightCard(insight: Insight) {
    val (bgColor, textColor) = when (insight.type) {
        InsightType.POSITIVE -> CoherenceHigh.copy(alpha = 0.10f) to CoherenceHigh
        InsightType.WARNING -> CoherenceLow.copy(alpha = 0.10f) to CoherenceLow
        InsightType.NEUTRAL -> Primary.copy(alpha = 0.08f) to Primary
        InsightType.SCIENCE -> Secondary.copy(alpha = 0.08f) to Secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = insight.body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

private fun trainingSessions(r: EvaluationReport): String =
    r.trainingCoherenceTrend.size.toString()
