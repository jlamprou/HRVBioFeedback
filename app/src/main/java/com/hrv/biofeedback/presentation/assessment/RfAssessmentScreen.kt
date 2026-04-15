package com.hrv.biofeedback.presentation.assessment

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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.hrv.biofeedback.domain.model.LEHRER_PROTOCOL
import com.hrv.biofeedback.presentation.common.BreathingPacer
import com.hrv.biofeedback.presentation.common.MetricCard
import com.hrv.biofeedback.presentation.theme.CardDark
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.LfPowerColor
import com.hrv.biofeedback.presentation.theme.Primary
import com.hrv.biofeedback.presentation.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RfAssessmentScreen(
    onBack: () -> Unit,
    onComplete: (Long) -> Unit,
    viewModel: RfAssessmentViewModel = hiltViewModel()
) {
    val assessmentState by viewModel.assessmentState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val breathingRate by viewModel.currentBreathingRate.collectAsStateWithLifecycle()
    val timeRemaining by viewModel.stepTimeRemaining.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val savedSessionId by viewModel.savedSessionId.collectAsStateWithLifecycle()
    val stepResults by viewModel.stepResults.collectAsStateWithLifecycle()

    LaunchedEffect(savedSessionId) {
        savedSessionId?.let { onComplete(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RF Assessment") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (assessmentState == AssessmentState.RUNNING || assessmentState == AssessmentState.REST) {
                            viewModel.cancelAssessment()
                        }
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
            when (assessmentState) {
                AssessmentState.NOT_STARTED -> {
                    // Introduction
                    Text(
                        text = "Find Your Resonance Frequency",
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardDark)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "How it works",
                                style = MaterialTheme.typography.titleMedium,
                                color = Primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Based on the Lehrer protocol (Shaffer et al., 2020), " +
                                        "you'll breathe at 5 different rates (6.5, 6.0, 5.5, 5.0, 4.5 breaths/min) " +
                                        "for 2 minutes each with equal inhale/exhale timing.\n\n" +
                                        "Your HRV is analyzed using 6 scientific criteria: " +
                                        "phase synchrony, peak-to-trough amplitude, LF power, " +
                                        "spectral peak magnitude, waveform smoothness, and spectral simplicity.\n\n" +
                                        "Total duration: ~18 minutes (including 2-min rest periods between steps)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    if (connectionState !is ConnectionState.Connected) {
                        Text(
                            text = "Please connect a sensor first",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Button(
                            onClick = { viewModel.startAssessment() },
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Begin Assessment")
                        }
                    }
                }

                AssessmentState.RUNNING -> {
                    // Progress
                    Text(
                        text = "Step ${currentStep + 1} of ${viewModel.totalSteps}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    LinearProgressIndicator(
                        progress = { (currentStep.toFloat() + 1f) / viewModel.totalSteps },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = Primary
                    )

                    // Breathing rate label
                    Text(
                        text = "Breathing at %.1f breaths/min".format(breathingRate),
                        style = MaterialTheme.typography.headlineMedium,
                        color = LfPowerColor,
                        fontWeight = FontWeight.Bold
                    )

                    // Timer
                    Text(
                        text = "%d:%02d".format(timeRemaining / 60, timeRemaining % 60),
                        style = MaterialTheme.typography.displayLarge,
                        color = TextSecondary,
                        fontSize = 36.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Breathing pacer
                    // RF assessment uses 50/50 inhale/exhale per Lehrer protocol
                    // to avoid confounding RF detection with asymmetric breathing
                    BreathingPacer(
                        breathingRate = breathingRate,
                        inhaleRatio = viewModel.assessmentInhaleRatio,
                        vibrationEnabled = true,
                        audioCuesEnabled = true,
                        audioVolume = 50
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live metrics
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
                            label = "Amplitude",
                            value = "%.1f".format(metrics.peakTroughAmplitude),
                            unit = "bpm",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(onClick = {
                        viewModel.cancelAssessment()
                        onBack()
                    }) {
                        Text("Cancel")
                    }
                }

                AssessmentState.REST -> {
                    Text(
                        text = "Rest",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Breathe normally",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "${timeRemaining}s",
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Next: %.1f breaths/min".format(
                            if (currentStep + 1 < LEHRER_PROTOCOL.size)
                                LEHRER_PROTOCOL[currentStep + 1].breathingRate
                            else breathingRate
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LfPowerColor
                    )
                }

                AssessmentState.COMPLETED -> {
                    val rfResult = result
                    if (rfResult != null) {
                        Text(
                            text = "Your Resonance Frequency",
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "%.1f".format(rfResult.optimalRate),
                            style = MaterialTheme.typography.displayLarge,
                            color = CoherenceHigh,
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp
                        )
                        Text(
                            text = "breaths per minute",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Results for each step
                        Text(
                            text = "Step Results",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        rfResult.stepResults.forEach { step ->
                            val isOptimal = step.breathingRate == rfResult.optimalRate
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isOptimal) Primary.copy(alpha = 0.15f)
                                    else CardDark
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "%.1f bpm".format(step.breathingRate),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isOptimal) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isOptimal) Primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Score: %.2f".format(step.combinedScore),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isOptimal) Primary else TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Saving results...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                AssessmentState.ERROR -> {
                    Text(
                        text = "An error occurred during assessment",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}
