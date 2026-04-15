package com.hrv.biofeedback.presentation.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrv.biofeedback.domain.model.BreathingPhase
import com.hrv.biofeedback.presentation.theme.ExhaleColor
import com.hrv.biofeedback.presentation.theme.InhaleColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun BreathingPacer(
    breathingRate: Double,
    modifier: Modifier = Modifier,
    inhaleRatio: Float = 0.4f,
    vibrationEnabled: Boolean = true,
    audioCuesEnabled: Boolean = false,
    audioVolume: Int = 50,
    onPhaseChange: ((BreathingPhase, Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val cycleDurationMs = ((60.0 / breathingRate) * 1000).toInt()
    val inhaleDurationMs = (cycleDurationMs * inhaleRatio).toInt()
    val exhaleDurationMs = cycleDurationMs - inhaleDurationMs

    var phase by remember { mutableStateOf(BreathingPhase.INHALE) }
    var progress by remember { mutableFloatStateOf(0f) }
    var cycleCount by remember { mutableIntStateOf(0) }

    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (_: Exception) { null }
    }

    // Continuous audio tone generator
    val tonePlayer = remember(audioCuesEnabled) {
        if (audioCuesEnabled) BreathingTonePlayer(audioVolume) else null
    }

    DisposableEffect(audioCuesEnabled) {
        onDispose { tonePlayer?.stop() }
    }

    LaunchedEffect(breathingRate, inhaleRatio, audioCuesEnabled, audioVolume) {
        cycleCount = 0
        tonePlayer?.setVolume(audioVolume)

        while (true) {
            // --- INHALE ---
            phase = BreathingPhase.INHALE
            onPhaseChange?.invoke(BreathingPhase.INHALE, cycleCount)

            // Vibration: gentle ramp-up pattern for inhale
            if (vibrationEnabled && vibrator != null) {
                launch {
                    try {
                        // Sustained gentle vibration during inhale
                        val effect = VibrationEffect.createWaveform(
                            longArrayOf(0, 100, 200, 100, 200, 100, 200, 100),
                            intArrayOf(0, 40, 0, 60, 0, 80, 0, 100),
                            -1 // no repeat
                        )
                        vibrator.vibrate(effect)
                    } catch (_: Exception) {}
                }
            }

            // Audio: rising tone during entire inhale
            if (audioCuesEnabled && tonePlayer != null) {
                launch(Dispatchers.Default) {
                    tonePlayer.playRisingTone(inhaleDurationMs)
                }
            }

            val inhaleSteps = inhaleDurationMs / 16
            for (i in 0..inhaleSteps) {
                progress = i.toFloat() / inhaleSteps
                delay(16)
            }

            // --- EXHALE ---
            phase = BreathingPhase.EXHALE
            onPhaseChange?.invoke(BreathingPhase.EXHALE, cycleCount)

            // Vibration: gentle fading pattern for exhale
            if (vibrationEnabled && vibrator != null) {
                launch {
                    try {
                        val effect = VibrationEffect.createWaveform(
                            longArrayOf(0, 100, 200, 100, 200, 100, 200, 100),
                            intArrayOf(0, 100, 0, 80, 0, 60, 0, 40),
                            -1
                        )
                        vibrator.vibrate(effect)
                    } catch (_: Exception) {}
                }
            }

            // Audio: falling tone during entire exhale
            if (audioCuesEnabled && tonePlayer != null) {
                launch(Dispatchers.Default) {
                    tonePlayer.playFallingTone(exhaleDurationMs)
                }
            }

            val exhaleSteps = exhaleDurationMs / 16
            for (i in 0..exhaleSteps) {
                progress = 1f - (i.toFloat() / exhaleSteps)
                delay(16)
            }

            cycleCount++
        }
    }

    val currentColor = if (phase == BreathingPhase.INHALE) InhaleColor else ExhaleColor
    val phaseText = if (phase == BreathingPhase.INHALE) "Breathe In" else "Breathe Out"

    Box(
        modifier = modifier.size(240.dp).aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val maxRadius = size.minDimension / 2f
            val minRadius = maxRadius * 0.35f
            val currentRadius = minRadius + (maxRadius - minRadius) * progress

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        currentColor.copy(alpha = 0.3f * progress),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = currentRadius * 1.3f
                )
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        currentColor.copy(alpha = 0.6f),
                        currentColor.copy(alpha = 0.2f)
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = currentRadius
                ),
                radius = currentRadius
            )

            drawCircle(
                color = currentColor.copy(alpha = 0.8f),
                radius = currentRadius,
                style = Stroke(width = 3.dp.toPx())
            )
        }

        Text(
            text = phaseText,
            style = MaterialTheme.typography.titleMedium,
            color = currentColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

/**
 * Generates continuous rising/falling tones for breathing guidance.
 * Uses a warm sine wave with harmonics for a relaxing sound.
 */
private class BreathingTonePlayer(private var volume: Int) {
    companion object {
        private const val SAMPLE_RATE = 22050
    }

    private var audioTrack: AudioTrack? = null

    fun setVolume(v: Int) { volume = v }

    fun playRisingTone(durationMs: Int) {
        // Frequency sweeps from 220 Hz to 440 Hz (A3 to A4) — gentle octave rise
        playToneSweep(220.0, 440.0, durationMs)
    }

    fun playFallingTone(durationMs: Int) {
        // Frequency sweeps from 440 Hz down to 220 Hz
        playToneSweep(440.0, 220.0, durationMs)
    }

    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun playToneSweep(startFreq: Double, endFreq: Double, durationMs: Int) {
        stop()

        val numSamples = SAMPLE_RATE * durationMs / 1000
        if (numSamples <= 0) return

        val samples = ShortArray(numSamples)
        val amplitude = (volume / 100.0) * 0.25 * Short.MAX_VALUE

        var phaseAccum = 0.0
        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            // Smooth frequency sweep (logarithmic for perceptual linearity)
            val freq = startFreq * Math.pow(endFreq / startFreq, t)

            // Fade envelope: smooth in/out to avoid clicks
            val envelope = when {
                t < 0.05 -> t / 0.05
                t > 0.95 -> (1.0 - t) / 0.05
                else -> 1.0
            }

            // Fundamental + soft 2nd harmonic for warmth
            val fundamental = sin(phaseAccum)
            val harmonic = 0.3 * sin(phaseAccum * 2.0)
            val sample = amplitude * envelope * (fundamental + harmonic) / 1.3

            samples[i] = sample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            // Accumulate phase (prevents discontinuities in frequency sweep)
            phaseAccum += 2.0 * PI * freq / SAMPLE_RATE
        }

        try {
            val bufferSize = samples.size * 2 // 16-bit = 2 bytes per sample
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.write(samples, 0, samples.size)
            audioTrack?.play()
        } catch (_: Exception) {
            stop()
        }
    }
}
