package com.hrv.biofeedback.domain.model

enum class BreathingPhase { INHALE, EXHALE }

data class PacerState(
    val phase: BreathingPhase = BreathingPhase.INHALE,
    val progress: Float = 0f,
    val cycleCount: Int = 0,
    val breathingRate: Double = 6.0
)
