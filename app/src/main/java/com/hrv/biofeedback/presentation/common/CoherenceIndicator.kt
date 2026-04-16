package com.hrv.biofeedback.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hrv.biofeedback.domain.model.HrvMetrics
import com.hrv.biofeedback.presentation.theme.CoherenceHigh
import com.hrv.biofeedback.presentation.theme.CoherenceLow
import com.hrv.biofeedback.presentation.theme.CoherenceMedium

/**
 * Biofeedback effectiveness indicator based on the metrics used in
 * the actual peer-reviewed literature:
 *
 * Primary: Peak-to-trough HR amplitude (Shaffer et al. 2020 criterion #2,
 *   Lehrer & Gevirtz 2014 primary training target)
 * Secondary: LF power (0.04-0.15 Hz, concentrates at breathing frequency during RF)
 *
 * Thresholds from published biofeedback literature:
 * - Amplitude: 5-10 = untrained, 10-15 = beginning, 15-25 = good, 25+ = excellent
 *   (Lehrer & Gevirtz 2014, adjusted for typical training progression)
 * - LF power during RF breathing: typically 5-10x resting values
 *   Resting LF ~519 ms² (Nunan 2010), so RF-LF of 1000-3000+ indicates good resonance
 *
 * Note: These are population-level approximations. Age-adjusted thresholds
 * are available in the Full Evaluation report via HrvNorms.
 */
enum class BiofeedbackLevel { LOW, BUILDING, GOOD, STRONG }

fun assessBiofeedbackLevel(metrics: HrvMetrics): BiofeedbackLevel {
    val amp = metrics.peakTroughAmplitude
    val lf = metrics.lfPower

    // Primary criterion: peak-to-trough amplitude (Shaffer #2)
    // These thresholds come from Lehrer & Gevirtz (2014) training progressions
    return when {
        amp >= 25 && lf >= 2000 -> BiofeedbackLevel.STRONG   // Excellent: well-trained
        amp >= 15 && lf >= 1000 -> BiofeedbackLevel.GOOD     // Good: effective biofeedback
        amp >= 8  && lf >= 300  -> BiofeedbackLevel.BUILDING  // Building: resonance developing
        else -> BiofeedbackLevel.LOW                           // Low: not yet entrained
    }
}

@Composable
fun BiofeedbackIndicator(
    metrics: HrvMetrics,
    modifier: Modifier = Modifier
) {
    val level = assessBiofeedbackLevel(metrics)
    val (color, label) = when (level) {
        BiofeedbackLevel.STRONG -> CoherenceHigh to "Strong resonance"
        BiofeedbackLevel.GOOD -> CoherenceHigh.copy(alpha = 0.7f) to "Good resonance"
        BiofeedbackLevel.BUILDING -> CoherenceMedium to "Building resonance"
        BiofeedbackLevel.LOW -> CoherenceLow to "Low — follow the pacer"
    }

    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label (Amp: %.1f bpm | LF: %.0f ms\u00B2)".format(
                metrics.peakTroughAmplitude, metrics.lfPower
            ),
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
    }
}

/**
 * HeartMath coherence indicator — kept for users familiar with HeartMath products.
 * This is a proprietary metric NOT used in the mainstream Lehrer/Gevirtz/Shaffer
 * peer-reviewed literature. The standard research metrics are LF power and
 * peak-to-trough amplitude.
 */
@Composable
fun CoherenceIndicator(
    level: com.hrv.biofeedback.domain.model.CoherenceLevel,
    modifier: Modifier = Modifier
) {
    val color = when (level) {
        com.hrv.biofeedback.domain.model.CoherenceLevel.HIGH -> CoherenceHigh
        com.hrv.biofeedback.domain.model.CoherenceLevel.MEDIUM -> CoherenceMedium
        com.hrv.biofeedback.domain.model.CoherenceLevel.LOW -> CoherenceLow
    }

    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "HeartMath: ${level.name}",
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}
