package com.hrv.biofeedback.domain.model

data class RfStepResult(
    val breathingRate: Double,
    val lfPower: Double,
    val hfPower: Double,
    val coherenceScore: Double,
    val peakTroughAmplitude: Double,
    val phaseSync: Double,
    val curveSmoothness: Double,    // Spectral concentration in LF band (0-1)
    val lfPeakCount: Int = 1,       // Shaffer criterion #6: number of distinct LF peaks
    val combinedScore: Double = 0.0
)

data class RfAssessmentResult(
    val stepResults: List<RfStepResult>,
    val optimalRate: Double,
    val timestamp: Long = System.currentTimeMillis()
)

data class RfProtocolStep(
    val breathingRate: Double,
    val durationSeconds: Int = 120
)

val LEHRER_PROTOCOL = listOf(
    RfProtocolStep(6.5),
    RfProtocolStep(6.0),
    RfProtocolStep(5.5),
    RfProtocolStep(5.0),
    RfProtocolStep(4.5)
)
