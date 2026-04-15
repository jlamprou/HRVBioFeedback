package com.hrv.biofeedback.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metrics_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class MetricsSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,

    // Time-domain
    val hr: Int,
    val rmssd: Double,
    val sdnn: Double,
    val pnn50: Double = 0.0,

    // Frequency-domain
    val lfPower: Double,
    val hfPower: Double,
    val lfHfRatio: Double = 0.0,
    val coherenceScore: Double,
    val peakFrequency: Double,
    val peakTroughAmplitude: Double,

    // Nonlinear
    val sd1: Double = 0.0,
    val sd2: Double = 0.0,
    val dfaAlpha1: Double = 0.0,
    val sampleEntropy: Double = 0.0,

    // Respiratory
    val cardiorespCoherence: Double = 0.0,
    val breathingRate: Double = 0.0,
    val cardiorespPhase: Double = 0.0
)
