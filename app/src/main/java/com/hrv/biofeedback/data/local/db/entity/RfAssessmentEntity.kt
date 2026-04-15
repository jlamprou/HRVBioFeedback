package com.hrv.biofeedback.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rf_assessments",
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
data class RfAssessmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val breathingRate: Double,
    val lfPower: Double,
    val hfPower: Double,
    val coherenceScore: Double,
    val peakTroughAmplitude: Double,
    val phaseSync: Double,
    val curveSmoothness: Double,
    val lfPeakCount: Int,
    val combinedScore: Double
)
