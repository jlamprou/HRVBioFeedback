package com.hrv.biofeedback.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrv.biofeedback.domain.dsp.SignalQualityMonitor
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.CoherenceLow
import com.hrv.biofeedback.presentation.theme.CoherenceMedium

/**
 * Displays real-time signal quality status and alerts.
 * Shows a colored bar (green/yellow/red) and any active warnings.
 */
@Composable
fun SignalQualityBar(
    report: SignalQualityMonitor.QualityReport,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (report.overall) {
        SignalQualityMonitor.Quality.GOOD -> CoherenceHigh to "Signal: Good"
        SignalQualityMonitor.Quality.FAIR -> CoherenceMedium to "Signal: Fair"
        SignalQualityMonitor.Quality.POOR -> CoherenceLow to "Signal: Poor"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Quality indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Card(
                modifier = Modifier.size(10.dp),
                shape = RoundedCornerShape(5.dp),
                colors = CardDefaults.cardColors(containerColor = color)
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Alerts
        report.alerts.forEach { alert ->
            AnimatedVisibility(visible = true) {
                val alertColor = when (alert.severity) {
                    SignalQualityMonitor.Severity.CRITICAL -> CoherenceLow
                    SignalQualityMonitor.Severity.WARNING -> CoherenceMedium
                    SignalQualityMonitor.Severity.INFO -> CoherenceHigh
                }
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = alertColor,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                )
            }
        }
    }
}
