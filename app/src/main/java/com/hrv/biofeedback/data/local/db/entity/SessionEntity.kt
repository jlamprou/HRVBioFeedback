package com.hrv.biofeedback.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "TRAINING", "ASSESSMENT", "MORNING_CHECK"
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,

    // Time-domain
    val averageHr: Double,
    val averageRmssd: Double,
    val averageSdnn: Double,
    val averagePnn50: Double = 0.0,

    // Frequency-domain
    val averageLfPower: Double,
    val averageHfPower: Double,
    val averageLfHfRatio: Double = 0.0,

    // Coherence & biofeedback
    val averageCoherence: Double,
    val averagePeakTrough: Double,
    val breathingRate: Double,

    // Nonlinear
    val averageSd1: Double = 0.0,
    val averageSd2: Double = 0.0,
    val averageDfaAlpha1: Double = 0.0,
    val averageSampleEntropy: Double = 0.0,

    // Respiratory coupling
    val averageCardiorespCoherence: Double = 0.0,
    val detectedBreathingRate: Double = 0.0,

    // Signal quality
    val artifactRatePercent: Double = 0.0,

    // Assessment-specific
    val rfResult: Double? = null,
    val notes: String = ""
)
