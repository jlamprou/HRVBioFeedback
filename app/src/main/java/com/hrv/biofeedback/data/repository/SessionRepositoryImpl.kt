package com.hrv.biofeedback.data.repository

import com.hrv.biofeedback.data.local.db.dao.MetricsSnapshotDao
import com.hrv.biofeedback.data.local.db.dao.RfAssessmentDao
import com.hrv.biofeedback.data.local.db.dao.RrIntervalDao
import com.hrv.biofeedback.data.local.db.dao.SessionDao
import com.hrv.biofeedback.data.local.db.entity.MetricsSnapshotEntity
import com.hrv.biofeedback.data.local.db.entity.RfAssessmentEntity
import com.hrv.biofeedback.data.local.db.entity.RrIntervalEntity
import com.hrv.biofeedback.data.local.db.entity.SessionEntity
import com.hrv.biofeedback.domain.model.HrvMetrics
import com.hrv.biofeedback.domain.model.RfAssessmentResult
import com.hrv.biofeedback.domain.model.RfStepResult
import com.hrv.biofeedback.domain.model.SessionType
import com.hrv.biofeedback.domain.repository.SessionDetail
import com.hrv.biofeedback.domain.repository.SessionRepository
import com.hrv.biofeedback.domain.repository.SessionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val rrIntervalDao: RrIntervalDao,
    private val metricsSnapshotDao: MetricsSnapshotDao,
    private val rfAssessmentDao: RfAssessmentDao
) : SessionRepository {

    override fun getAllSessions(): Flow<List<SessionSummary>> {
        return sessionDao.getAllSessions().map { entities ->
            entities.map { it.toSummary() }
        }
    }

    override fun getSessionsByType(type: SessionType): Flow<List<SessionSummary>> {
        return sessionDao.getByType(type.name).map { entities ->
            entities.map { it.toSummary() }
        }
    }

    override suspend fun getSessionDetail(sessionId: Long): SessionDetail? {
        val session = sessionDao.getById(sessionId) ?: return null
        val rrIntervals = rrIntervalDao.getBySession(sessionId).map { it.timestamp to it.rrMs }
        val metricsSnapshots = metricsSnapshotDao.getBySession(sessionId).map { it.toHrvMetrics() }

        val rfResults = if (session.type == SessionType.ASSESSMENT.name) {
            rfAssessmentDao.getBySession(sessionId).map { it.toStepResult() }
        } else null

        return SessionDetail(
            summary = session.toSummary(),
            rrIntervals = rrIntervals,
            metricsTimeline = metricsSnapshots,
            rfStepResults = rfResults
        )
    }

    override suspend fun getLatestSession(): SessionSummary? {
        return sessionDao.getLatest()?.toSummary()
    }

    override suspend fun getLatestRfResult(): Double? {
        return sessionDao.getLatestAssessment()?.rfResult
    }

    override suspend fun saveTrainingSession(
        startTime: Long,
        endTime: Long,
        breathingRate: Double,
        rrIntervals: List<Pair<Long, Double>>,
        metricsSnapshots: List<HrvMetrics>
    ): Long {
        val avgMetrics = averageMetrics(metricsSnapshots)

        val sessionId = sessionDao.insert(
            SessionEntity(
                type = SessionType.TRAINING.name,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = ((endTime - startTime) / 1000).toInt(),
                averageHr = avgMetrics.hr.toDouble(),
                averageRmssd = avgMetrics.rmssd,
                averageSdnn = avgMetrics.sdnn,
                averageLfPower = avgMetrics.lfPower,
                averageHfPower = avgMetrics.hfPower,
                averageCoherence = avgMetrics.coherenceScore,
                averagePeakTrough = avgMetrics.peakTroughAmplitude,
                breathingRate = breathingRate
            )
        )

        saveRrIntervals(sessionId, rrIntervals)
        saveMetricsSnapshots(sessionId, metricsSnapshots)

        return sessionId
    }

    override suspend fun saveAssessmentSession(
        startTime: Long,
        endTime: Long,
        result: RfAssessmentResult,
        rrIntervals: List<Pair<Long, Double>>,
        metricsSnapshots: List<HrvMetrics>
    ): Long {
        val avgMetrics = averageMetrics(metricsSnapshots)

        val sessionId = sessionDao.insert(
            SessionEntity(
                type = SessionType.ASSESSMENT.name,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = ((endTime - startTime) / 1000).toInt(),
                averageHr = avgMetrics.hr.toDouble(),
                averageRmssd = avgMetrics.rmssd,
                averageSdnn = avgMetrics.sdnn,
                averageLfPower = avgMetrics.lfPower,
                averageHfPower = avgMetrics.hfPower,
                averageCoherence = avgMetrics.coherenceScore,
                averagePeakTrough = avgMetrics.peakTroughAmplitude,
                breathingRate = result.optimalRate,
                rfResult = result.optimalRate
            )
        )

        saveRrIntervals(sessionId, rrIntervals)
        saveMetricsSnapshots(sessionId, metricsSnapshots)

        // Save RF step results
        rfAssessmentDao.insertAll(
            result.stepResults.map { step ->
                RfAssessmentEntity(
                    sessionId = sessionId,
                    breathingRate = step.breathingRate,
                    lfPower = step.lfPower,
                    hfPower = step.hfPower,
                    coherenceScore = step.coherenceScore,
                    peakTroughAmplitude = step.peakTroughAmplitude,
                    phaseSync = step.phaseSync,
                    curveSmoothness = step.curveSmoothness,
                    lfPeakCount = step.lfPeakCount,
                    combinedScore = step.combinedScore
                )
            }
        )

        return sessionId
    }

    override suspend fun saveMorningCheck(
        startTime: Long,
        endTime: Long,
        rrIntervals: List<Pair<Long, Double>>,
        metricsSnapshots: List<HrvMetrics>
    ): Long {
        val avgMetrics = averageMetrics(metricsSnapshots)

        val sessionId = sessionDao.insert(
            SessionEntity(
                type = SessionType.MORNING_CHECK.name,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = ((endTime - startTime) / 1000).toInt(),
                averageHr = avgMetrics.hr.toDouble(),
                averageRmssd = avgMetrics.rmssd,
                averageSdnn = avgMetrics.sdnn,
                averageLfPower = avgMetrics.lfPower,
                averageHfPower = avgMetrics.hfPower,
                averageCoherence = avgMetrics.coherenceScore,
                averagePeakTrough = avgMetrics.peakTroughAmplitude,
                breathingRate = 0.0 // Natural breathing, not paced
            )
        )

        saveRrIntervals(sessionId, rrIntervals)
        saveMetricsSnapshots(sessionId, metricsSnapshots)

        return sessionId
    }

    override suspend fun getMorningCheckTrend(): List<SessionSummary> {
        // Get all morning checks, already ordered by startTime DESC from DAO
        val flow = sessionDao.getByType(SessionType.MORNING_CHECK.name)
        // Collect first emission
        var result = emptyList<SessionEntity>()
        flow.collect { result = it; return@collect }
        return result.map { it.toSummary() }
    }

    override suspend fun deleteSession(sessionId: Long) {
        sessionDao.delete(sessionId)
    }

    override suspend fun getSessionCount(): Int = sessionDao.getCount()

    private suspend fun saveRrIntervals(sessionId: Long, intervals: List<Pair<Long, Double>>) {
        rrIntervalDao.insertAll(
            intervals.map { (timestamp, rr) ->
                RrIntervalEntity(
                    sessionId = sessionId,
                    timestamp = timestamp,
                    rrMs = rr
                )
            }
        )
    }

    private suspend fun saveMetricsSnapshots(sessionId: Long, snapshots: List<HrvMetrics>) {
        // Save every 5th snapshot to reduce database size
        metricsSnapshotDao.insertAll(
            snapshots.filterIndexed { index, _ -> index % 5 == 0 }.map { m ->
                MetricsSnapshotEntity(
                    sessionId = sessionId,
                    timestamp = m.timestamp,
                    hr = m.hr,
                    rmssd = m.rmssd,
                    sdnn = m.sdnn,
                    lfPower = m.lfPower,
                    hfPower = m.hfPower,
                    coherenceScore = m.coherenceScore,
                    peakFrequency = m.peakFrequency,
                    peakTroughAmplitude = m.peakTroughAmplitude
                )
            }
        )
    }

    private fun averageMetrics(snapshots: List<HrvMetrics>): HrvMetrics {
        if (snapshots.isEmpty()) return HrvMetrics()
        // Filter out initial zero-value snapshots (before enough data accumulated)
        val valid = snapshots.filter { it.lfPower > 0 || it.rmssd > 0 }
        if (valid.isEmpty()) return HrvMetrics()

        return HrvMetrics(
            hr = valid.map { it.hr }.average().toInt(),
            rmssd = valid.map { it.rmssd }.average(),
            sdnn = valid.map { it.sdnn }.average(),
            lfPower = valid.map { it.lfPower }.average(),
            hfPower = valid.map { it.hfPower }.average(),
            coherenceScore = valid.map { it.coherenceScore }.average(),
            peakFrequency = valid.map { it.peakFrequency }.average(),
            peakTroughAmplitude = valid.map { it.peakTroughAmplitude }.average()
        )
    }

    private fun SessionEntity.toSummary() = SessionSummary(
        id = id,
        type = SessionType.valueOf(type),
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        averageHr = averageHr,
        averageRmssd = averageRmssd,
        averageSdnn = averageSdnn,
        averageLfPower = averageLfPower,
        averageHfPower = averageHfPower,
        averageCoherence = averageCoherence,
        averagePeakTrough = averagePeakTrough,
        breathingRate = breathingRate,
        rfResult = rfResult,
        notes = notes
    )

    private fun MetricsSnapshotEntity.toHrvMetrics() = HrvMetrics(
        hr = hr,
        rmssd = rmssd,
        sdnn = sdnn,
        lfPower = lfPower,
        hfPower = hfPower,
        coherenceScore = coherenceScore,
        peakFrequency = peakFrequency,
        peakTroughAmplitude = peakTroughAmplitude,
        timestamp = timestamp
    )

    private fun RfAssessmentEntity.toStepResult() = RfStepResult(
        breathingRate = breathingRate,
        lfPower = lfPower,
        hfPower = hfPower,
        coherenceScore = coherenceScore,
        peakTroughAmplitude = peakTroughAmplitude,
        phaseSync = phaseSync,
        curveSmoothness = curveSmoothness,
        lfPeakCount = lfPeakCount,
        combinedScore = combinedScore
    )
}
