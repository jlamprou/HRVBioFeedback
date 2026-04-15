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
        metricsSnapshots: List<HrvMetrics>,
        artifactRate: Double
    ): Long {
        val avgMetrics = averageMetrics(metricsSnapshots)
        val sessionId = sessionDao.insert(
            avgMetrics.toSessionEntity(SessionType.TRAINING, startTime, endTime, breathingRate, artifactRate)
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
        metricsSnapshots: List<HrvMetrics>,
        artifactRate: Double
    ): Long {
        val avgMetrics = averageMetrics(metricsSnapshots)
        val sessionId = sessionDao.insert(
            avgMetrics.toSessionEntity(
                SessionType.ASSESSMENT, startTime, endTime,
                result.optimalRate, artifactRate, rfResult = result.optimalRate
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
        metricsSnapshots: List<HrvMetrics>,
        artifactRate: Double
    ): Long {
        val avgMetrics = averageMetrics(metricsSnapshots)
        val sessionId = sessionDao.insert(
            avgMetrics.toSessionEntity(SessionType.MORNING_CHECK, startTime, endTime, 0.0, artifactRate)
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
                    pnn50 = m.pnn50,
                    lfPower = m.lfPower,
                    hfPower = m.hfPower,
                    lfHfRatio = m.lfHfRatio,
                    coherenceScore = m.coherenceScore,
                    peakFrequency = m.peakFrequency,
                    peakTroughAmplitude = m.peakTroughAmplitude,
                    sd1 = m.sd1,
                    sd2 = m.sd2,
                    dfaAlpha1 = m.dfaAlpha1,
                    sampleEntropy = m.sampleEntropy,
                    cardiorespCoherence = m.cardiorespCoherence,
                    breathingRate = m.breathingRate,
                    cardiorespPhase = m.cardiorespPhase
                )
            }
        )
    }

    private fun averageMetrics(snapshots: List<HrvMetrics>): HrvMetrics {
        if (snapshots.isEmpty()) return HrvMetrics()
        val valid = snapshots.filter { it.lfPower > 0 || it.rmssd > 0 }
        if (valid.isEmpty()) return HrvMetrics()

        return HrvMetrics(
            hr = valid.map { it.hr }.average().toInt(),
            rmssd = valid.map { it.rmssd }.average(),
            sdnn = valid.map { it.sdnn }.average(),
            pnn50 = valid.map { it.pnn50 }.average(),
            lfPower = valid.map { it.lfPower }.average(),
            hfPower = valid.map { it.hfPower }.average(),
            lfHfRatio = valid.map { it.lfHfRatio }.average(),
            coherenceScore = valid.map { it.coherenceScore }.average(),
            peakFrequency = valid.map { it.peakFrequency }.average(),
            peakTroughAmplitude = valid.map { it.peakTroughAmplitude }.average(),
            sd1 = valid.map { it.sd1 }.average(),
            sd2 = valid.map { it.sd2 }.average(),
            dfaAlpha1 = valid.filter { it.dfaAlpha1 > 0 }.let { if (it.isNotEmpty()) it.map { m -> m.dfaAlpha1 }.average() else 0.0 },
            sampleEntropy = valid.filter { it.sampleEntropy > 0 }.let { if (it.isNotEmpty()) it.map { m -> m.sampleEntropy }.average() else 0.0 },
            cardiorespCoherence = valid.map { it.cardiorespCoherence }.average(),
            breathingRate = valid.filter { it.breathingRate > 0 }.let { if (it.isNotEmpty()) it.map { m -> m.breathingRate }.average() else 0.0 }
        )
    }

    private fun HrvMetrics.toSessionEntity(
        type: SessionType,
        startTime: Long,
        endTime: Long,
        breathingRate: Double,
        artifactRate: Double = 0.0,
        rfResult: Double? = null
    ) = SessionEntity(
        type = type.name,
        startTime = startTime,
        endTime = endTime,
        durationSeconds = ((endTime - startTime) / 1000).toInt(),
        averageHr = hr.toDouble(),
        averageRmssd = rmssd,
        averageSdnn = sdnn,
        averagePnn50 = pnn50,
        averageLfPower = lfPower,
        averageHfPower = hfPower,
        averageLfHfRatio = lfHfRatio,
        averageCoherence = coherenceScore,
        averagePeakTrough = peakTroughAmplitude,
        breathingRate = breathingRate,
        averageSd1 = sd1,
        averageSd2 = sd2,
        averageDfaAlpha1 = dfaAlpha1,
        averageSampleEntropy = sampleEntropy,
        averageCardiorespCoherence = cardiorespCoherence,
        detectedBreathingRate = this.breathingRate,
        artifactRatePercent = artifactRate,
        rfResult = rfResult
    )

    private fun SessionEntity.toSummary() = SessionSummary(
        id = id,
        type = SessionType.valueOf(type),
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds,
        averageHr = averageHr,
        averageRmssd = averageRmssd,
        averageSdnn = averageSdnn,
        averagePnn50 = averagePnn50,
        averageLfPower = averageLfPower,
        averageHfPower = averageHfPower,
        averageLfHfRatio = averageLfHfRatio,
        averageCoherence = averageCoherence,
        averagePeakTrough = averagePeakTrough,
        breathingRate = breathingRate,
        averageSd1 = averageSd1,
        averageSd2 = averageSd2,
        averageDfaAlpha1 = averageDfaAlpha1,
        averageSampleEntropy = averageSampleEntropy,
        averageCardiorespCoherence = averageCardiorespCoherence,
        detectedBreathingRate = detectedBreathingRate,
        artifactRatePercent = artifactRatePercent,
        rfResult = rfResult,
        notes = notes
    )

    private fun MetricsSnapshotEntity.toHrvMetrics() = HrvMetrics(
        hr = hr,
        rmssd = rmssd,
        sdnn = sdnn,
        pnn50 = pnn50,
        lfPower = lfPower,
        hfPower = hfPower,
        lfHfRatio = lfHfRatio,
        coherenceScore = coherenceScore,
        peakFrequency = peakFrequency,
        peakTroughAmplitude = peakTroughAmplitude,
        sd1 = sd1,
        sd2 = sd2,
        dfaAlpha1 = dfaAlpha1,
        sampleEntropy = sampleEntropy,
        cardiorespCoherence = cardiorespCoherence,
        breathingRate = breathingRate,
        cardiorespPhase = cardiorespPhase,
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
