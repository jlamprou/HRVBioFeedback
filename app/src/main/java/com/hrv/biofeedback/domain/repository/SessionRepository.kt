package com.hrv.biofeedback.domain.repository

import com.hrv.biofeedback.domain.model.HrvMetrics
import com.hrv.biofeedback.domain.model.RfAssessmentResult
import com.hrv.biofeedback.domain.model.SessionType
import kotlinx.coroutines.flow.Flow

data class SessionSummary(
    val id: Long,
    val type: SessionType,
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
    val rfResult: Double? = null,
    val notes: String = ""
)

data class SessionDetail(
    val summary: SessionSummary,
    val rrIntervals: List<Pair<Long, Double>>, // timestamp, rrMs
    val metricsTimeline: List<HrvMetrics>,
    val rfStepResults: List<com.hrv.biofeedback.domain.model.RfStepResult>? = null
)

interface SessionRepository {
    fun getAllSessions(): Flow<List<SessionSummary>>
    fun getSessionsByType(type: SessionType): Flow<List<SessionSummary>>
    suspend fun getSessionDetail(sessionId: Long): SessionDetail?
    suspend fun getLatestSession(): SessionSummary?
    suspend fun getLatestRfResult(): Double?

    suspend fun saveTrainingSession(
        startTime: Long,
        endTime: Long,
        breathingRate: Double,
        rrIntervals: List<Pair<Long, Double>>,
        metricsSnapshots: List<HrvMetrics>
    ): Long

    suspend fun saveAssessmentSession(
        startTime: Long,
        endTime: Long,
        result: RfAssessmentResult,
        rrIntervals: List<Pair<Long, Double>>,
        metricsSnapshots: List<HrvMetrics>
    ): Long

    suspend fun saveMorningCheck(
        startTime: Long,
        endTime: Long,
        rrIntervals: List<Pair<Long, Double>>,
        metricsSnapshots: List<HrvMetrics>
    ): Long

    suspend fun getMorningCheckTrend(): List<SessionSummary>

    suspend fun deleteSession(sessionId: Long)
    suspend fun getSessionCount(): Int
}
