package com.hrv.biofeedback.presentation.evaluation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrv.biofeedback.domain.model.SessionType
import com.hrv.biofeedback.domain.repository.SessionRepository
import com.hrv.biofeedback.domain.repository.SessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EvaluationReport(
    // Overall
    val totalSessions: Int,
    val totalTrainingMinutes: Int,
    val totalMorningChecks: Int,
    val totalAssessments: Int,
    val daysSinceFirstSession: Int,

    // Current RF
    val currentRf: Double?,
    val rfHistory: List<Pair<Long, Double>>, // timestamp, rf

    // Morning check trends (chronological)
    val morningRmssdTrend: List<Pair<Long, Double>>,
    val morningHrTrend: List<Pair<Long, Double>>,
    val morningSdnnTrend: List<Pair<Long, Double>>,

    // Current baseline (latest morning check)
    val currentRmssd: Double?,
    val currentRestingHr: Double?,
    val currentSdnn: Double?,

    // Averages
    val weekRmssdAvg: Double?,
    val monthRmssdAvg: Double?,
    val allTimeRmssdAvg: Double?,
    val weekHrAvg: Double?,
    val monthHrAvg: Double?,

    // Training progression
    val trainingCoherenceTrend: List<Pair<Long, Double>>,
    val trainingAmplitudeTrend: List<Pair<Long, Double>>,
    val avgTrainingCoherence: Double?,
    val bestTrainingCoherence: Double?,
    val avgTrainingAmplitude: Double?,

    // Age/sex-adjusted metric assessments
    val metricAssessments: List<com.hrv.biofeedback.domain.usecase.analysis.HrvNorms.MetricAssessment>,
    val userAge: Int,
    val userSex: String,

    // Insights
    val insights: List<Insight>
)

data class Insight(
    val title: String,
    val body: String,
    val type: InsightType
)

enum class InsightType { POSITIVE, WARNING, NEUTRAL, SCIENCE }

@HiltViewModel
class EvaluationViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val userPreferences: com.hrv.biofeedback.data.local.preferences.UserPreferences,
    private val hrvNorms: com.hrv.biofeedback.domain.usecase.analysis.HrvNorms
) : ViewModel() {

    private val _report = MutableStateFlow<EvaluationReport?>(null)
    val report: StateFlow<EvaluationReport?> = _report.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        generateReport()
    }

    fun generateReport() {
        viewModelScope.launch {
            _isLoading.value = true

            val allSessions = sessionRepository.getAllSessions().first()
            val morningChecks = allSessions.filter { it.type == SessionType.MORNING_CHECK }
                .sortedBy { it.startTime }
            val trainingSessions = allSessions.filter { it.type == SessionType.TRAINING }
                .sortedBy { it.startTime }
            val assessments = allSessions.filter { it.type == SessionType.ASSESSMENT }
                .sortedBy { it.startTime }

            val now = System.currentTimeMillis()
            val weekAgo = now - 7 * 24 * 3600 * 1000L
            val monthAgo = now - 30 * 24 * 3600 * 1000L

            val daysSinceFirst = if (allSessions.isNotEmpty()) {
                ((now - allSessions.minOf { it.startTime }) / (24 * 3600 * 1000L)).toInt()
            } else 0

            // Morning check trends
            val morningRmssd = morningChecks.map { it.startTime to it.averageRmssd }
            val morningHr = morningChecks.map { it.startTime to it.averageHr }
            val morningSdnn = morningChecks.map { it.startTime to it.averageSdnn }

            // Averages by period
            val weekMornings = morningChecks.filter { it.startTime >= weekAgo }
            val monthMornings = morningChecks.filter { it.startTime >= monthAgo }

            // RF history
            val rfHistory = assessments.mapNotNull { a ->
                a.rfResult?.let { a.startTime to it }
            }

            // Training trends
            val trainingCoherence = trainingSessions.map { it.startTime to it.averageCoherence }
            val trainingAmplitude = trainingSessions.map { it.startTime to it.averagePeakTrough }

            // Age/sex-adjusted norms assessment
            val profile = userPreferences.profile.first()

            // Generate insights (uses profile for age-adjusted thresholds)
            val insights = generateInsights(
                morningChecks, trainingSessions, assessments,
                weekMornings, monthMornings, daysSinceFirst, profile
            )
            val latestMorning = morningChecks.lastOrNull()
            val latestTraining = trainingSessions.lastOrNull()
            val metricAssessments = if (profile.isComplete && (latestMorning != null || latestTraining != null)) {
                hrvNorms.assessAll(
                    profile = profile,
                    rmssd = latestMorning?.averageRmssd ?: 0.0,
                    sdnn = latestMorning?.averageSdnn ?: 0.0,
                    pnn50 = 0.0, // Not stored in summary, would need detail
                    restingHr = latestMorning?.averageHr ?: 0.0,
                    lfPower = latestMorning?.averageLfPower ?: latestTraining?.averageLfPower ?: 0.0,
                    hfPower = latestMorning?.averageHfPower ?: latestTraining?.averageHfPower ?: 0.0,
                    lfHfRatio = 0.0,
                    dfaAlpha1 = 0.0, // Would need detail
                    sampleEntropy = 0.0,
                    sd1 = 0.0,
                    sd2 = 0.0,
                    peakTrough = latestTraining?.averagePeakTrough ?: 0.0,
                    coherenceScore = latestTraining?.averageCoherence ?: 0.0
                )
            } else emptyList()

            _report.value = EvaluationReport(
                totalSessions = allSessions.size,
                totalTrainingMinutes = trainingSessions.sumOf { it.durationSeconds } / 60,
                totalMorningChecks = morningChecks.size,
                totalAssessments = assessments.size,
                daysSinceFirstSession = daysSinceFirst,

                currentRf = assessments.lastOrNull()?.rfResult,
                rfHistory = rfHistory,

                morningRmssdTrend = morningRmssd,
                morningHrTrend = morningHr,
                morningSdnnTrend = morningSdnn,

                currentRmssd = morningChecks.lastOrNull()?.averageRmssd,
                currentRestingHr = morningChecks.lastOrNull()?.averageHr,
                currentSdnn = morningChecks.lastOrNull()?.averageSdnn,

                weekRmssdAvg = weekMornings.takeIf { it.isNotEmpty() }?.map { it.averageRmssd }?.average(),
                monthRmssdAvg = monthMornings.takeIf { it.isNotEmpty() }?.map { it.averageRmssd }?.average(),
                allTimeRmssdAvg = morningChecks.takeIf { it.isNotEmpty() }?.map { it.averageRmssd }?.average(),
                weekHrAvg = weekMornings.takeIf { it.isNotEmpty() }?.map { it.averageHr }?.average(),
                monthHrAvg = monthMornings.takeIf { it.isNotEmpty() }?.map { it.averageHr }?.average(),

                trainingCoherenceTrend = trainingCoherence,
                trainingAmplitudeTrend = trainingAmplitude,
                avgTrainingCoherence = trainingSessions.takeIf { it.isNotEmpty() }?.map { it.averageCoherence }?.average(),
                bestTrainingCoherence = trainingSessions.takeIf { it.isNotEmpty() }?.maxOfOrNull { it.averageCoherence },
                avgTrainingAmplitude = trainingSessions.takeIf { it.isNotEmpty() }?.map { it.averagePeakTrough }?.average(),

                metricAssessments = metricAssessments,
                userAge = profile.age,
                userSex = profile.sex,

                insights = insights
            )

            _isLoading.value = false
        }
    }

    private fun generateInsights(
        morningChecks: List<SessionSummary>,
        trainingSessions: List<SessionSummary>,
        assessments: List<SessionSummary>,
        weekMornings: List<SessionSummary>,
        monthMornings: List<SessionSummary>,
        daysSinceFirst: Int,
        profile: com.hrv.biofeedback.data.local.preferences.UserProfile
    ): List<Insight> {
        val insights = mutableListOf<Insight>()

        // --- RMSSD Health Assessment (age-adjusted) ---
        morningChecks.lastOrNull()?.let { latest ->
            val rmssd = latest.averageRmssd
            if (profile.isComplete) {
                val norm = hrvNorms.rmssdNorm(profile.age, profile.sex)
                when {
                    rmssd >= norm.high -> insights.add(Insight(
                        "Excellent Parasympathetic Tone",
                        "Your resting RMSSD of %.1f ms is above the high range for your age group (%d, %s: >%.0f ms). This indicates strong vagal nerve function.".format(rmssd, profile.age, profile.sex, norm.high),
                        InsightType.POSITIVE
                    ))
                    rmssd >= norm.mean -> insights.add(Insight(
                        "Good Baseline HRV",
                        "Your resting RMSSD of %.1f ms is above average for age %d %s (norm: %.0f ms). Continue daily training to improve further.".format(rmssd, profile.age, profile.sex, norm.mean),
                        InsightType.POSITIVE
                    ))
                    rmssd >= norm.low -> insights.add(Insight(
                        "Average HRV — Room for Improvement",
                        "Your resting RMSSD of %.1f ms is in the lower-normal range for age %d %s (norm: %.0f-%.0f ms). Regular biofeedback training can increase parasympathetic tone (Lehrer & Gevirtz 2014).".format(rmssd, profile.age, profile.sex, norm.low, norm.high),
                        InsightType.WARNING
                    ))
                    else -> insights.add(Insight(
                        "Below-Average HRV for Your Age",
                        "Your resting RMSSD of %.1f ms is below the expected range for age %d %s (norm: >%.0f ms). Low HRV is associated with increased health risk. HRV biofeedback has been shown to improve RMSSD even in clinical populations.".format(rmssd, profile.age, profile.sex, norm.low),
                        InsightType.WARNING
                    ))
                }
            } else {
                // No profile — use population averages with caveat
                when {
                    rmssd >= 50 -> insights.add(Insight(
                        "Good Baseline HRV",
                        "Your resting RMSSD of %.1f ms is in the healthy range (population average ~42 ms, Nunan 2010). Set your age and sex in Settings for personalized norms.".format(rmssd),
                        InsightType.POSITIVE
                    ))
                    rmssd >= 20 -> insights.add(Insight(
                        "Moderate HRV",
                        "Your resting RMSSD of %.1f ms. Set your age and sex in Settings — norms vary significantly by age (RMSSD declines ~3-4 ms per decade after 30).".format(rmssd),
                        InsightType.NEUTRAL
                    ))
                    else -> insights.add(Insight(
                        "Low Baseline HRV",
                        "Your resting RMSSD of %.1f ms is low. Set your age and sex in Settings for accurate comparison — what's 'low' depends heavily on age.".format(rmssd),
                        InsightType.WARNING
                    ))
                }
            }
        }

        // --- RMSSD Trend ---
        if (morningChecks.size >= 5) {
            val firstHalf = morningChecks.take(morningChecks.size / 2).map { it.averageRmssd }.average()
            val secondHalf = morningChecks.takeLast(morningChecks.size / 2).map { it.averageRmssd }.average()
            val changePct = ((secondHalf - firstHalf) / firstHalf * 100)

            when {
                changePct > 10 -> insights.add(Insight(
                    "RMSSD Trending Upward (+%.0f%%)".format(changePct),
                    "Your resting RMSSD has increased significantly over your measurement history. This demonstrates genuine autonomic adaptation from your biofeedback training — your vagal tone is measurably improving.",
                    InsightType.POSITIVE
                ))
                changePct < -10 -> insights.add(Insight(
                    "RMSSD Trending Down (%.0f%%)".format(changePct),
                    "Your resting RMSSD has decreased. This could indicate accumulated stress, poor sleep, overtraining, or illness. Consider prioritizing rest and recovery. If persistent, consult a healthcare provider.",
                    InsightType.WARNING
                ))
                else -> insights.add(Insight(
                    "Stable Baseline",
                    "Your resting RMSSD is stable. Consistency is key — Lehrer's standard protocol recommends 10-20 weeks of regular practice for full adaptation.",
                    InsightType.NEUTRAL
                ))
            }
        }

        // --- Training Volume ---
        val totalMinutes = trainingSessions.sumOf { it.durationSeconds } / 60
        if (trainingSessions.isNotEmpty()) {
            val sessionsPerWeek = if (daysSinceFirst > 0) {
                trainingSessions.size.toDouble() / (daysSinceFirst / 7.0).coerceAtLeast(1.0)
            } else trainingSessions.size.toDouble()

            when {
                sessionsPerWeek >= 5 && totalMinutes >= 100 -> insights.add(Insight(
                    "Strong Training Consistency",
                    "You're averaging %.1f sessions/week with %d total minutes. The standard Lehrer protocol recommends daily 20-minute sessions. You're on track.".format(sessionsPerWeek, totalMinutes),
                    InsightType.POSITIVE
                ))
                sessionsPerWeek >= 2 -> insights.add(Insight(
                    "Good Training Frequency",
                    "%.1f sessions/week is a solid start. For maximum benefit, aim for daily practice (Lehrer & Gevirtz 2014). Home practice between sessions is where most adaptation occurs.".format(sessionsPerWeek),
                    InsightType.NEUTRAL
                ))
                else -> insights.add(Insight(
                    "More Practice Recommended",
                    "%.1f sessions/week is below the recommended frequency. The standard HRV biofeedback protocol calls for daily 20-minute sessions plus weekly guided sessions (Lehrer protocol). Try to establish a daily routine.".format(sessionsPerWeek),
                    InsightType.WARNING
                ))
            }
        }

        // --- Training Coherence Progression ---
        if (trainingSessions.size >= 3) {
            val recent = trainingSessions.takeLast(3).map { it.averageCoherence }.average()
            val earlier = trainingSessions.take(3).map { it.averageCoherence }.average()
            if (earlier > 0 && recent > earlier * 1.2) {
                insights.add(Insight(
                    "Coherence Improving",
                    "Your average training coherence has improved from %.2f to %.2f. This means your cardiovascular system is increasingly responding to your breathing — the baroreflex is strengthening.".format(earlier, recent),
                    InsightType.POSITIVE
                ))
            }
        }

        // --- RF Assessment ---
        if (assessments.isNotEmpty()) {
            val latestRf = assessments.last().rfResult
            if (latestRf != null) {
                insights.add(Insight(
                    "Your Resonance Frequency: %.1f bpm".format(latestRf),
                    "This is the breathing rate where your cardiovascular system naturally resonates — producing maximum HR oscillation amplitude and baroreflex stimulation (Vaschillo et al.). Individual RF is determined by body size and baroreflex loop delay. Adults typically range from 4.5-6.5 bpm.",
                    InsightType.SCIENCE
                ))
            }

            if (assessments.size >= 2) {
                val rfs = assessments.mapNotNull { it.rfResult }
                if (rfs.distinct().size > 1) {
                    insights.add(Insight(
                        "RF Variability Detected",
                        "Your RF has varied across assessments (${rfs.map { "%.1f".format(it) }.joinToString(", ")} bpm). Van Diest et al. (2021) found RF changes between sessions in 67%% of participants. This is normal — our adaptive training automatically adjusts to your current RF.",
                        InsightType.SCIENCE
                    ))
                }
            }
        } else {
            insights.add(Insight(
                "No RF Assessment Yet",
                "Run the RF Assessment to find your personal resonance frequency. This is the breathing rate that maximizes your HRV response and is the foundation of effective biofeedback training.",
                InsightType.WARNING
            ))
        }

        // --- Morning Check Consistency ---
        if (morningChecks.isEmpty()) {
            insights.add(Insight(
                "Start Daily Morning Checks",
                "Morning resting HRV is the gold standard for tracking autonomic health (Plews et al. 2013). Measure for 2 minutes every morning before getting out of bed. This is how you'll see the long-term impact of your training.",
                InsightType.WARNING
            ))
        } else if (morningChecks.size >= 14) {
            insights.add(Insight(
                "Strong Measurement Habit",
                "%d morning checks recorded. Consistent measurement is essential for reliable trend analysis. Your data is now providing meaningful insights.".format(morningChecks.size),
                InsightType.POSITIVE
            ))
        }

        // --- Resting HR (fitness-adjusted if profile available) ---
        morningChecks.lastOrNull()?.let { latest ->
            val hr = latest.averageHr
            if (profile.isComplete) {
                val norm = hrvNorms.restingHrNorm(profile.sex, profile.fitnessLevel)
                when {
                    hr <= norm.low -> insights.add(Insight(
                        "Excellent Resting Heart Rate",
                        "Resting HR of %.0f bpm is excellent for your profile. Lower resting HR indicates strong cardiovascular fitness and vagal tone.".format(hr),
                        InsightType.POSITIVE
                    ))
                    hr <= norm.mean -> insights.add(Insight(
                        "Good Resting Heart Rate",
                        "Resting HR of %.0f bpm is good (norm for %s, %s: ~%.0f bpm).".format(hr, profile.sex, profile.fitnessLevel.ifEmpty { "average" }, norm.mean),
                        InsightType.POSITIVE
                    ))
                    hr <= norm.high -> insights.add(Insight(
                        "Normal Resting Heart Rate",
                        "Resting HR of %.0f bpm is in the normal range. Regular biofeedback training and exercise can help lower it.".format(hr),
                        InsightType.NEUTRAL
                    ))
                    else -> insights.add(Insight(
                        "Elevated Resting Heart Rate",
                        "Resting HR of %.0f bpm is above expected (norm: <%.0f bpm). The Copenhagen Heart Study found HR >80 bpm associated with increased cardiovascular risk.".format(hr, norm.high),
                        InsightType.WARNING
                    ))
                }
            } else {
                when {
                    hr <= 60 -> insights.add(Insight("Excellent Resting HR", "%.0f bpm — strong cardiovascular fitness.".format(hr), InsightType.POSITIVE))
                    hr <= 75 -> insights.add(Insight("Normal Resting HR", "%.0f bpm — in the normal range.".format(hr), InsightType.NEUTRAL))
                    else -> insights.add(Insight("Elevated Resting HR", "%.0f bpm — above typical. Set your profile in Settings for personalized assessment.".format(hr), InsightType.WARNING))
                }
            }
        }

        // --- Science context ---
        insights.add(Insight(
            "The Science Behind HRV Biofeedback",
            "HRV biofeedback works by breathing at your resonance frequency (~0.1 Hz), which maximizes baroreflex stimulation. The large blood pressure oscillations this creates strengthen the baroreflex arc over time, improving autonomic regulation. Meta-analyses show medium effect sizes for depression (g=-0.41, Pizzoli 2021), PTSD (g=-0.56), and stress reduction (Goessl 2017).",
            InsightType.SCIENCE
        ))

        return insights
    }
}
