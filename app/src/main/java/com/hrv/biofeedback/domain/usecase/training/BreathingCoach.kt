package com.hrv.biofeedback.domain.usecase.training

import com.hrv.biofeedback.domain.model.HrvMetrics
import javax.inject.Inject
import kotlin.math.abs

/**
 * Real-time breathing coach that generates actionable feedback based on HRV metrics.
 *
 * Mirrors what a trained clinician does during in-person HRV biofeedback sessions
 * (Lehrer protocol): observing metrics and giving specific, encouraging cues
 * to help the user optimize their breathing pattern.
 *
 * Feedback is prioritized — only the most important tip is shown at a time
 * to avoid overwhelming the user.
 */
class BreathingCoach @Inject constructor() {

    data class CoachingTip(
        val message: String,
        val type: TipType,
        val priority: Int // lower = more important
    )

    enum class TipType {
        POSITIVE,    // User is doing well
        GUIDANCE,    // Gentle correction needed
        ENCOURAGE    // General encouragement
    }

    // Rolling history for trend detection
    private val coherenceHistory = mutableListOf<Double>()
    private val amplitudeHistory = mutableListOf<Double>()
    private val rmssdHistory = mutableListOf<Double>()
    private var lastTipTime = 0L
    private var lastTipMessage = ""

    companion object {
        private const val MIN_TIP_INTERVAL_MS = 8000 // Don't change tip too frequently
        private const val HISTORY_SIZE = 30 // ~30 seconds of history
    }

    fun reset() {
        coherenceHistory.clear()
        amplitudeHistory.clear()
        rmssdHistory.clear()
        lastTipTime = 0L
        lastTipMessage = ""
    }

    /**
     * Analyze current metrics and generate the most relevant coaching tip.
     *
     * @param metrics Current HRV metrics from the processor
     * @param targetBreathingRate The pacer's current breathing rate in bpm
     * @param elapsedSeconds Seconds since session started
     * @return CoachingTip or null if no change needed
     */
    fun analyze(
        metrics: HrvMetrics,
        targetBreathingRate: Double,
        elapsedSeconds: Int
    ): CoachingTip? {
        // Don't generate tips too frequently
        val now = System.currentTimeMillis()
        if (now - lastTipTime < MIN_TIP_INTERVAL_MS) return null

        // Update history
        if (metrics.coherenceScore > 0) coherenceHistory.add(metrics.coherenceScore)
        if (metrics.peakTroughAmplitude > 0) amplitudeHistory.add(metrics.peakTroughAmplitude)
        if (metrics.rmssd > 0) rmssdHistory.add(metrics.rmssd)

        // Trim history
        while (coherenceHistory.size > HISTORY_SIZE) coherenceHistory.removeAt(0)
        while (amplitudeHistory.size > HISTORY_SIZE) amplitudeHistory.removeAt(0)
        while (rmssdHistory.size > HISTORY_SIZE) rmssdHistory.removeAt(0)

        // Wait for enough data before coaching
        if (elapsedSeconds < 30) {
            return makeTip("Relax and follow the breathing circle", TipType.GUIDANCE, 100)
        }
        if (elapsedSeconds < 65) {
            return makeTip("Building your HRV baseline...", TipType.ENCOURAGE, 100)
        }

        // Generate tips by priority (lowest priority number = most important)
        val tips = mutableListOf<CoachingTip>()

        // --- Check breathing compliance (ACC-detected rate vs pacer) ---
        if (metrics.breathingRate > 0.5) {
            val rateError = abs(metrics.breathingRate - targetBreathingRate)
            if (rateError > 1.5) {
                tips.add(CoachingTip(
                    "Match the breathing circle — aim for %.0f breaths/min".format(targetBreathingRate),
                    TipType.GUIDANCE, 1
                ))
            } else if (rateError > 0.8) {
                tips.add(CoachingTip(
                    "Almost there — try to sync with the pacer",
                    TipType.GUIDANCE, 3
                ))
            }
        }

        // --- Check frequency entrainment (LF peak vs breathing frequency) ---
        val targetFreqHz = targetBreathingRate / 60.0
        if (metrics.peakFrequency > 0) {
            val freqError = abs(metrics.peakFrequency - targetFreqHz)
            if (freqError > 0.03) { // More than ~2 bpm off
                tips.add(CoachingTip(
                    "Breathe slowly and deeply — let your heart rhythm follow",
                    TipType.GUIDANCE, 2
                ))
            }
        }

        // --- Check amplitude (depth of breathing) ---
        if (amplitudeHistory.size >= 10) {
            val recentAmplitude = amplitudeHistory.takeLast(5).average()
            val earlierAmplitude = amplitudeHistory.take(amplitudeHistory.size / 2).average()

            if (recentAmplitude < 5.0) {
                tips.add(CoachingTip(
                    "Breathe deeper into your belly — expand your diaphragm",
                    TipType.GUIDANCE, 4
                ))
            } else if (recentAmplitude < earlierAmplitude * 0.7) {
                tips.add(CoachingTip(
                    "Your breaths are getting shallower — take fuller breaths",
                    TipType.GUIDANCE, 5
                ))
            }
        }

        // --- Check cardiorespiratory phase ---
        if (metrics.cardiorespCoherence > 0.01) {
            val phaseAbs = abs(metrics.cardiorespPhase)
            if (phaseAbs > 90 && phaseAbs < 270) {
                tips.add(CoachingTip(
                    "Inhale as the circle expands, exhale as it shrinks",
                    TipType.GUIDANCE, 3
                ))
            }
        }

        // --- Positive feedback ---
        if (coherenceHistory.size >= 5) {
            val recentCoherence = coherenceHistory.takeLast(5).average()

            if (recentCoherence >= 1.8) {
                tips.add(CoachingTip(
                    "Excellent coherence! You're in the zone",
                    TipType.POSITIVE, 10
                ))
            } else if (recentCoherence >= 0.5) {
                val earlierCoherence = coherenceHistory.take(coherenceHistory.size / 2).average()
                if (recentCoherence > earlierCoherence * 1.2) {
                    tips.add(CoachingTip(
                        "Great improvement — your coherence is rising",
                        TipType.POSITIVE, 8
                    ))
                }
            }
        }

        // --- RMSSD trend (vagal tone) ---
        if (rmssdHistory.size >= 15) {
            val recentRmssd = rmssdHistory.takeLast(5).average()
            val earlierRmssd = rmssdHistory.take(rmssdHistory.size / 2).average()
            if (recentRmssd > earlierRmssd * 1.15) {
                tips.add(CoachingTip(
                    "Your vagal tone is increasing — keep this rhythm",
                    TipType.POSITIVE, 9
                ))
            }
        }

        // --- Amplitude positive feedback ---
        if (amplitudeHistory.size >= 10) {
            val recentAmp = amplitudeHistory.takeLast(5).average()
            if (recentAmp > 15.0) {
                tips.add(CoachingTip(
                    "Strong HR oscillations — your breathing is very effective",
                    TipType.POSITIVE, 7
                ))
            }
        }

        // --- Default encouragement ---
        if (tips.isEmpty()) {
            tips.add(CoachingTip(
                "Steady breathing — stay relaxed and focused",
                TipType.ENCOURAGE, 50
            ))
        }

        // Pick highest priority tip (lowest number), avoid repeating the same one
        val best = tips.minByOrNull { it.priority } ?: return null

        // Don't repeat the exact same message
        if (best.message == lastTipMessage && tips.size > 1) {
            val second = tips.filter { it.message != lastTipMessage }.minByOrNull { it.priority }
            if (second != null) return makeTip(second.message, second.type, second.priority)
        }

        return makeTip(best.message, best.type, best.priority)
    }

    private fun makeTip(message: String, type: TipType, priority: Int): CoachingTip {
        lastTipTime = System.currentTimeMillis()
        lastTipMessage = message
        return CoachingTip(message, type, priority)
    }
}
