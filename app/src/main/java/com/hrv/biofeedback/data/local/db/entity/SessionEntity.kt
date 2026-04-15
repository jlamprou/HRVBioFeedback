package com.hrv.biofeedback.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "TRAINING" or "ASSESSMENT"
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val averageHr: Double,
    val averageRmssd: Double,
    val averageSdnn: Double,
    val averageLfPower: Double,
    val averageHfPower: Double,
    val averageCoherence: Double,
    val averagePeakTrough: Double,
    val breathingRate: Double,
    val rfResult: Double? = null, // Only for assessment sessions
    val notes: String = ""
)
