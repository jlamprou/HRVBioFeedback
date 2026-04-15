package com.hrv.biofeedback.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hrv.biofeedback.presentation.theme.InhaleColor
import com.hrv.biofeedback.presentation.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.sin

/**
 * Displays the actual detected breathing waveform (from accelerometer)
 * overlaid on the target breathing pattern (from pacer).
 *
 * This gives the user visual feedback on how well they're following the pacer:
 * - Target: smooth sinusoidal line (from pacer settings)
 * - Actual: real respiratory signal (from ACC z-axis)
 *
 * When the two lines align closely, the user is breathing correctly.
 */
@Composable
fun BreathingComplianceOverlay(
    actualBreathingData: List<Float>,
    breathingRateBpm: Double,
    durationSeconds: Float = 30f,
    modifier: Modifier = Modifier
) {
    val targetColor = InhaleColor.copy(alpha = 0.4f)
    val actualColor = InhaleColor

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        // Draw target breathing pattern (sinusoidal)
        val targetPath = Path()
        val breathingFreq = breathingRateBpm / 60.0
        val numPoints = 200

        for (i in 0..numPoints) {
            val x = (i.toFloat() / numPoints) * width
            val t = (i.toFloat() / numPoints) * durationSeconds
            val y = centerY - (height * 0.35f * sin(2.0 * PI * breathingFreq * t)).toFloat()

            if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
        }

        drawPath(
            path = targetPath,
            color = targetColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw actual breathing signal
        if (actualBreathingData.size >= 2) {
            val actualPath = Path()

            // Normalize actual data to fit display
            val maxVal = actualBreathingData.maxOrNull()?.let { kotlin.math.abs(it) } ?: 1f
            val minVal = actualBreathingData.minOrNull()?.let { kotlin.math.abs(it) } ?: 1f
            val range = maxOf(maxVal, minVal, 0.001f)

            for (i in actualBreathingData.indices) {
                val x = (i.toFloat() / actualBreathingData.size) * width
                val normalized = actualBreathingData[i] / range
                val y = centerY - (height * 0.35f * normalized)

                if (i == 0) actualPath.moveTo(x, y) else actualPath.lineTo(x, y)
            }

            drawPath(
                path = actualPath,
                color = actualColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Center line
        drawLine(
            color = TextSecondary.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )
    }
}
