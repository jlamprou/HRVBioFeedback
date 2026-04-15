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
    val hr: Int,
    val rmssd: Double,
    val sdnn: Double,
    val lfPower: Double,
    val hfPower: Double,
    val coherenceScore: Double,
    val peakFrequency: Double,
    val peakTroughAmplitude: Double
)
