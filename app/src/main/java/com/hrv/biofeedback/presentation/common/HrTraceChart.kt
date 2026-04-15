package com.hrv.biofeedback.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrv.biofeedback.presentation.theme.ChartGrid
import com.hrv.biofeedback.presentation.theme.ChartLine
import com.hrv.biofeedback.presentation.theme.InhaleColor
import com.hrv.biofeedback.presentation.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-time scrolling HR trace chart with phase-locked target trajectory.
 *
 * The target trajectory represents what the user's HR SHOULD look like
 * at resonance: a sinusoidal wave phase-locked to the breathing pacer.
 *
 * Phase alignment (at resonance, per Lehrer & Gevirtz 2014):
 * - HR peaks at end of inhale (pacer fully expanded)
 * - HR troughs at end of exhale (pacer fully contracted)
 * - This 0° phase relationship between HR and breathing is Shaffer criterion #1
 *
 * The target amplitude adapts to the user's best performance,
 * encouraging progressively deeper HR oscillations.
 *
 * @param hrData List of (elapsedSeconds, hr) pairs
 * @param breathingRateBpm Current pacer breathing rate
 * @param peakTroughAmplitude Session peak-to-trough for target amplitude
 * @param inhaleRatio Fraction of cycle spent on inhale (0.4 = 40%)
 * @param elapsedSeconds Current session elapsed time (for phase sync)
 */
@Composable
fun HrTraceChart(
    hrData: List<Pair<Int, Int>>,
    modifier: Modifier = Modifier,
    breathingRateBpm: Double = 0.0,
    peakTroughAmplitude: Double = 0.0,
    inhaleRatio: Float = 0.4f,
    maxPoints: Int = 120,
    lineColor: Color = ChartLine
) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = TextSecondary
    val gridColor = ChartGrid
    val targetColor = InhaleColor.copy(alpha = 0.35f)
    val showTarget = breathingRateBpm > 0 && hrData.size > 10

    Column(modifier = modifier) {
        Text(
            text = if (showTarget) "Heart Rate — match the wave" else "Heart Rate Trace",
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            if (hrData.size < 2) return@Canvas

            val data = if (hrData.size > maxPoints) hrData.takeLast(maxPoints) else hrData
            val paddingLeft = 40.dp.toPx()
            val paddingRight = 8.dp.toPx()
            val paddingTop = 8.dp.toPx()
            val paddingBottom = 20.dp.toPx()

            val chartWidth = size.width - paddingLeft - paddingRight
            val chartHeight = size.height - paddingTop - paddingBottom

            // Auto-scale Y axis
            val hrValues = data.map { it.second }
            val meanHr = hrValues.average()
            val targetHalfAmp = if (peakTroughAmplitude > 2) peakTroughAmplitude / 2.0 else 0.0
            val dataHalfRange = ((hrValues.max() - hrValues.min()) / 2.0).coerceAtLeast(5.0)
            val halfRange = maxOf(dataHalfRange, targetHalfAmp) + 3.0

            val minHr = (meanHr - halfRange).toInt().coerceAtLeast(40)
            val maxHr = (meanHr + halfRange).toInt().coerceAtMost(200)
            val hrRange = (maxHr - minHr).coerceAtLeast(10)

            // Grid lines and Y-axis labels
            val gridSteps = 4
            for (i in 0..gridSteps) {
                val y = paddingTop + chartHeight * (1f - i.toFloat() / gridSteps)
                val hrValue = minHr + hrRange * i / gridSteps

                drawLine(
                    color = gridColor,
                    start = Offset(paddingLeft, y),
                    end = Offset(size.width - paddingRight, y),
                    strokeWidth = 1.dp.toPx()
                )

                val label = hrValue.toString()
                val textResult = textMeasurer.measure(
                    label,
                    style = TextStyle(fontSize = 10.sp, color = labelColor)
                )
                drawText(
                    textResult,
                    topLeft = Offset(
                        paddingLeft - textResult.size.width - 4.dp.toPx(),
                        y - textResult.size.height / 2
                    )
                )
            }

            // --- TARGET TRAJECTORY (phase-locked to breathing pacer) ---
            if (showTarget) {
                val targetPath = Path()
                val targetAmp = if (peakTroughAmplitude > 2.0) {
                    peakTroughAmplitude / 2.0
                } else {
                    4.0 // Default 8 bpm peak-to-trough target
                }
                val breathingFreqHz = breathingRateBpm / 60.0

                // Phase offset: HR should PEAK at end of inhale.
                // The pacer inhale ends at (inhaleRatio * cycleDuration) seconds into each cycle.
                // For sin(x) to peak (=1) at time t_peak:
                //   2π * f * t_peak - phaseOffset = π/2
                //   phaseOffset = 2π * f * t_peak - π/2
                // Since t_peak = inhaleRatio / f:
                //   phaseOffset = 2π * inhaleRatio - π/2
                // This ensures the target wave peaks exactly when the pacer circle
                // is fully expanded (end of inhale), matching the physiological RSA.
                val phaseOffset = 2.0 * PI * inhaleRatio - PI / 2.0

                // Time range from the data points (elapsed seconds)
                val tStart = data.first().first.toDouble()
                val tEnd = data.last().first.toDouble()
                val tRange = (tEnd - tStart).coerceAtLeast(1.0)

                val numTargetPoints = 300
                for (i in 0..numTargetPoints) {
                    val x = paddingLeft + chartWidth * i.toFloat() / numTargetPoints
                    // Map chart position to elapsed seconds
                    val t = tStart + tRange * i.toDouble() / numTargetPoints
                    // Phase-locked sinusoid: peaks align with end-of-inhale moments
                    val targetHr = meanHr + targetAmp * sin(2.0 * PI * breathingFreqHz * t - phaseOffset)

                    val normalizedHr = (targetHr - minHr).toFloat() / hrRange
                    val y = paddingTop + chartHeight * (1f - normalizedHr)

                    if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
                }

                drawPath(
                    path = targetPath,
                    color = targetColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(12.dp.toPx(), 8.dp.toPx())
                        )
                    )
                )
            }

            // --- ACTUAL HR TRACE ---
            val path = Path()
            // Map data points by their actual elapsed time for correct x positioning
            val tStart = data.first().first.toDouble()
            val tEnd = data.last().first.toDouble()
            val tRange = (tEnd - tStart).coerceAtLeast(1.0)

            for (i in data.indices) {
                val tNormalized = (data[i].first.toDouble() - tStart) / tRange
                val x = paddingLeft + chartWidth * tNormalized.toFloat()
                val normalizedHr = (data[i].second - minHr).toFloat() / hrRange
                val y = paddingTop + chartHeight * (1f - normalizedHr)

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Current HR dot
            if (data.isNotEmpty()) {
                val lastNormalized = (data.last().second - minHr).toFloat() / hrRange
                val lastY = paddingTop + chartHeight * (1f - lastNormalized)
                drawCircle(
                    color = lineColor,
                    radius = 4.dp.toPx(),
                    center = Offset(paddingLeft + chartWidth, lastY)
                )
            }
        }
    }
}
