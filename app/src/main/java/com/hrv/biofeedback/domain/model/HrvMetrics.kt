package com.hrv.biofeedback.domain.model

data class HrvMetrics(
    // --- Time-Domain (1996 Task Force) ---
    val hr: Int = 0,
    val rmssd: Double = 0.0,
    val sdnn: Double = 0.0,
    val pnn50: Double = 0.0,

    // --- Frequency-Domain (AR spectral analysis) ---
    val lfPower: Double = 0.0,      // ms² - LF band (0.04-0.15 Hz)
    val hfPower: Double = 0.0,      // ms² - HF band (0.15-0.40 Hz)
    val lfHfRatio: Double = 0.0,    // LF/HF
    val totalPower: Double = 0.0,   // ms² - total (0.04-0.40 Hz)
    val peakFrequency: Double = 0.0, // Hz - dominant LF peak

    /**
     * HeartMath coherence score: peak_power / (total_power - peak_power).
     * Unbounded above (typical range 0-16). NOT a 0-1 ratio.
     * See McCraty (2022) and HeartMath Institute documentation.
     */
    val coherenceScore: Double = 0.0,

    // --- Peak-to-Trough (Shaffer criterion #2) ---
    val peakTroughAmplitude: Double = 0.0, // bpm - HR Max-Min per breath cycle

    // --- Nonlinear Metrics ---
    val sd1: Double = 0.0,          // ms - Poincaré short-term (≈ RMSSD/√2)
    val sd2: Double = 0.0,          // ms - Poincaré long-term
    val dfaAlpha1: Double = 0.0,    // DFA short-term scaling exponent
    val sampleEntropy: Double = 0.0, // SampEn(m=2, r=0.2*SDNN)

    // --- Respiratory Coupling (when ACC/ECG available) ---
    val breathingRate: Double = 0.0,           // bpm - detected from respiratory signal
    val cardiorespCoherence: Double = 0.0,     // MSC at breathing freq (0-1)
    val cardiorespPhase: Double = 0.0,         // degrees - HR-breathing phase difference

    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Coherence level thresholds per HeartMath Inner Balance (Challenge Level 1):
     * - Low: score < 0.5
     * - Medium: 0.5 <= score < 1.8
     * - High: score >= 1.8
     *
     * Source: HeartMath Inner Balance Coherence Scoring System document.
     */
    val coherenceLevel: CoherenceLevel
        get() = when {
            coherenceScore >= 1.8 -> CoherenceLevel.HIGH
            coherenceScore >= 0.5 -> CoherenceLevel.MEDIUM
            else -> CoherenceLevel.LOW
        }
}

enum class CoherenceLevel { LOW, MEDIUM, HIGH }
