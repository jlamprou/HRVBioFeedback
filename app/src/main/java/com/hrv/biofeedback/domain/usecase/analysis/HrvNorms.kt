package com.hrv.biofeedback.domain.usecase.analysis

import com.hrv.biofeedback.data.local.preferences.UserProfile
import javax.inject.Inject

/**
 * Age- and sex-adjusted HRV normative reference values.
 *
 * Sources:
 * - Nunan et al. (2010) "A quantitative systematic review of normal values for
 *   short-term HRV" — 5-min supine norms (overall: RMSSD 42±15, SDNN 50±16)
 * - Shaffer & Ginsberg (2017) "An Overview of HRV Metrics and Norms"
 * - Umetani et al. (1998) "Twenty-four hour time domain HRV" — age norms
 * - Voss et al. (2015) — large population (n=1906), 5-min, age/sex stratified
 * - Huikuri et al. (2000) — DFA alpha1 as mortality predictor
 * - Kleiger et al. (1987) — SDNN < 50 ms 24h mortality cutoff
 *
 * These norms are for 5-minute supine recordings, which matches our
 * 5-minute morning check protocol per the 1996 Task Force standard.
 */
class HrvNorms @Inject constructor() {

    data class NormRange(
        val low: Double,     // Below this = concerning
        val mean: Double,    // Population average for age/sex
        val high: Double,    // Above this = excellent
        val unit: String
    )

    data class MetricAssessment(
        val metric: String,
        val value: Double,
        val normRange: NormRange,
        val percentile: String,  // "below average", "average", "above average", "excellent"
        val status: Status,
        val explanation: String
    )

    enum class Status { EXCELLENT, GOOD, AVERAGE, BELOW_AVERAGE, CONCERNING }

    // --- RMSSD norms by age (5-min, from Nunan 2010, Voss 2015) ---
    fun rmssdNorm(age: Int, sex: String): NormRange = when {
        age < 30 -> NormRange(25.0, if (sex == "female") 45.0 else 42.0, 65.0, "ms")
        age < 40 -> NormRange(20.0, if (sex == "female") 38.0 else 35.0, 55.0, "ms")
        age < 50 -> NormRange(15.0, if (sex == "female") 30.0 else 28.0, 45.0, "ms")
        age < 60 -> NormRange(12.0, 22.0, 35.0, "ms")
        age < 70 -> NormRange(10.0, 18.0, 28.0, "ms")
        else     -> NormRange(8.0, 15.0, 24.0, "ms")
    }

    // --- SDNN norms by age (5-min) ---
    fun sdnnNorm(age: Int, sex: String): NormRange = when {
        age < 30 -> NormRange(35.0, if (sex == "male") 58.0 else 53.0, 80.0, "ms")
        age < 40 -> NormRange(30.0, if (sex == "male") 50.0 else 46.0, 70.0, "ms")
        age < 50 -> NormRange(25.0, if (sex == "male") 44.0 else 40.0, 60.0, "ms")
        age < 60 -> NormRange(20.0, 36.0, 50.0, "ms")
        age < 70 -> NormRange(18.0, 30.0, 42.0, "ms")
        else     -> NormRange(15.0, 25.0, 36.0, "ms")
    }

    // --- pNN50 norms by age (5-min) ---
    fun pnn50Norm(age: Int): NormRange = when {
        age < 30 -> NormRange(5.0, 20.0, 35.0, "%")
        age < 40 -> NormRange(3.0, 15.0, 28.0, "%")
        age < 50 -> NormRange(2.0, 10.0, 20.0, "%")
        age < 60 -> NormRange(1.0, 6.0, 14.0, "%")
        age < 70 -> NormRange(0.5, 4.0, 10.0, "%")
        else     -> NormRange(0.0, 2.0, 7.0, "%")
    }

    // --- Resting HR norms by sex and fitness ---
    fun restingHrNorm(sex: String, fitnessLevel: String): NormRange {
        val baseMean = if (sex == "female") 74.0 else 70.0
        return when (fitnessLevel) {
            "athlete" -> NormRange(35.0, 48.0, 58.0, "bpm")
            "active"  -> NormRange(50.0, 62.0, 70.0, "bpm")
            "moderate" -> NormRange(58.0, 68.0, 76.0, "bpm")
            else -> NormRange(62.0, baseMean, 82.0, "bpm") // sedentary/unknown
        }
    }

    // --- LF Power norms by age (5-min, ms²) ---
    fun lfPowerNorm(age: Int): NormRange = when {
        age < 30 -> NormRange(300.0, 1000.0, 1500.0, "ms²")
        age < 40 -> NormRange(200.0, 700.0, 1100.0, "ms²")
        age < 50 -> NormRange(120.0, 475.0, 750.0, "ms²")
        age < 60 -> NormRange(80.0, 300.0, 500.0, "ms²")
        age < 70 -> NormRange(50.0, 185.0, 320.0, "ms²")
        else     -> NormRange(30.0, 105.0, 200.0, "ms²")
    }

    // --- HF Power norms by age (5-min, ms²) ---
    fun hfPowerNorm(age: Int): NormRange = when {
        age < 30 -> NormRange(250.0, 1200.0, 2000.0, "ms²")
        age < 40 -> NormRange(150.0, 700.0, 1200.0, "ms²")
        age < 50 -> NormRange(80.0, 375.0, 650.0, "ms²")
        age < 60 -> NormRange(40.0, 210.0, 400.0, "ms²")
        age < 70 -> NormRange(20.0, 120.0, 240.0, "ms²")
        else     -> NormRange(10.0, 65.0, 140.0, "ms²")
    }

    // --- DFA alpha1 (age-independent) ---
    fun dfaAlpha1Norm(): NormRange = NormRange(0.75, 1.0, 1.2, "")

    // --- Sample Entropy (age-approximate) ---
    fun sampleEntropyNorm(age: Int): NormRange = when {
        age < 40 -> NormRange(1.0, 1.7, 2.2, "")
        age < 60 -> NormRange(0.9, 1.5, 2.0, "")
        else     -> NormRange(0.8, 1.2, 1.7, "")
    }

    // --- SD1/SD2 ratio ---
    fun sd1sd2Norm(): NormRange = NormRange(0.20, 0.35, 0.50, "")

    // --- Peak-to-trough amplitude during RF breathing ---
    fun peakTroughNorm(age: Int): NormRange = when {
        age < 30 -> NormRange(8.0, 20.0, 35.0, "bpm")
        age < 40 -> NormRange(7.0, 17.0, 30.0, "bpm")
        age < 50 -> NormRange(5.0, 14.0, 25.0, "bpm")
        age < 60 -> NormRange(4.0, 11.0, 20.0, "bpm")
        age < 70 -> NormRange(3.0, 8.0, 15.0, "bpm")
        else     -> NormRange(2.0, 6.0, 12.0, "bpm")
    }

    // --- Assess a single metric against norms ---
    fun assess(name: String, value: Double, norm: NormRange, explanation: String): MetricAssessment {
        val (percentile, status) = when {
            value >= norm.high -> "excellent" to Status.EXCELLENT
            value >= norm.mean -> "above average" to Status.GOOD
            value >= norm.low  -> "average" to Status.AVERAGE
            value >= norm.low * 0.7 -> "below average" to Status.BELOW_AVERAGE
            else -> "concerning" to Status.CONCERNING
        }
        return MetricAssessment(name, value, norm, percentile, status, explanation)
    }

    // --- For metrics where lower is better (HR) ---
    fun assessLowerBetter(name: String, value: Double, norm: NormRange, explanation: String): MetricAssessment {
        val (percentile, status) = when {
            value <= norm.low -> "excellent" to Status.EXCELLENT
            value <= norm.mean -> "above average" to Status.GOOD
            value <= norm.high -> "average" to Status.AVERAGE
            value <= norm.high * 1.1 -> "below average" to Status.BELOW_AVERAGE
            else -> "concerning" to Status.CONCERNING
        }
        return MetricAssessment(name, value, norm, percentile, status, explanation)
    }

    /**
     * Generate a full assessment of all metrics against age/sex-adjusted norms.
     */
    fun assessAll(
        profile: UserProfile,
        rmssd: Double, sdnn: Double, pnn50: Double, restingHr: Double,
        lfPower: Double, hfPower: Double, lfHfRatio: Double,
        dfaAlpha1: Double, sampleEntropy: Double,
        sd1: Double, sd2: Double,
        peakTrough: Double, coherenceScore: Double
    ): List<MetricAssessment> {
        val age = profile.age.coerceIn(18, 100)
        val sex = profile.sex
        val fitness = profile.fitnessLevel
        val assessments = mutableListOf<MetricAssessment>()

        if (rmssd > 0) assessments.add(assess(
            "RMSSD", rmssd, rmssdNorm(age, sex),
            "RMSSD measures beat-to-beat variability driven by your vagus nerve (parasympathetic). " +
            "It's the most reliable short-term index of cardiac vagal tone. Higher values indicate " +
            "stronger parasympathetic activity and better stress resilience. " +
            "RMSSD declines ~3-4 ms per decade after age 30 (Umetani 1998)."
        ))

        if (sdnn > 0) assessments.add(assess(
            "SDNN", sdnn, sdnnNorm(age, sex),
            "SDNN reflects overall HRV from all regulatory mechanisms — both sympathetic and " +
            "parasympathetic. In 24-hour recordings, SDNN < 50 ms predicts increased mortality " +
            "post-MI (Kleiger 1987). For short recordings, it captures total autonomic variability."
        ))

        if (pnn50 >= 0) assessments.add(assess(
            "pNN50", pnn50, pnn50Norm(age),
            "pNN50 is the percentage of consecutive heartbeats differing by more than 50 ms. " +
            "It's a coarser measure of vagal tone that correlates with RMSSD. Values below 3% " +
            "consistently indicate poor parasympathetic modulation."
        ))

        if (restingHr > 0) assessments.add(assessLowerBetter(
            "Resting HR", restingHr, restingHrNorm(sex, fitness),
            "Lower resting heart rate reflects better cardiovascular fitness and higher vagal tone. " +
            "The Copenhagen Heart Study found resting HR > 80 bpm is associated with increased " +
            "cardiovascular mortality. Regular exercise and HRV biofeedback can lower resting HR."
        ))

        if (lfPower > 0) assessments.add(assess(
            "LF Power", lfPower, lfPowerNorm(age),
            "LF power (0.04-0.15 Hz) primarily reflects baroreflex activity — NOT purely sympathetic " +
            "tone as once believed (Billman 2013). During RF breathing, LF power increases dramatically " +
            "as breathing-driven oscillations concentrate in this band. Higher LF at rest indicates " +
            "stronger baroreflex function."
        ))

        if (hfPower > 0) assessments.add(assess(
            "HF Power", hfPower, hfPowerNorm(age),
            "HF power (0.15-0.40 Hz) is the most accepted proxy for cardiac vagal tone at rest " +
            "(when breathing at normal rates). It directly reflects respiratory sinus arrhythmia. " +
            "Women typically have higher HF than men in younger age groups."
        ))

        if (dfaAlpha1 > 0) {
            val dfaNorm = dfaAlpha1Norm()
            val explanation = "DFA alpha1 measures the fractal scaling of your heartbeat intervals. " +
                "A value near 1.0 indicates healthy complex dynamics — your heart adapts flexibly. " +
                "Values below 0.75 predict mortality post-MI (Huikuri 2000). Values above 1.3 " +
                "indicate overly rigid dynamics. During RF breathing, alpha1 typically decreases " +
                "as the periodic pattern overrides natural fractal behavior."
            val (pct, st) = when {
                dfaAlpha1 in 0.85..1.15 -> "optimal" to Status.EXCELLENT
                dfaAlpha1 in 0.75..1.25 -> "acceptable" to Status.GOOD
                dfaAlpha1 < 0.75 -> "concerning — reduced complexity" to Status.CONCERNING
                else -> "elevated — overly rigid dynamics" to Status.BELOW_AVERAGE
            }
            assessments.add(MetricAssessment("DFA alpha1", dfaAlpha1, dfaNorm, pct, st, explanation))
        }

        if (sampleEntropy > 0) assessments.add(assess(
            "Sample Entropy", sampleEntropy, sampleEntropyNorm(age),
            "Sample Entropy quantifies the unpredictability of your heart rhythm. Higher values " +
            "mean more complex, adaptive dynamics. Low SampEn (< 0.8) suggests overly regular " +
            "heart rate patterns which may indicate reduced autonomic flexibility (Costa 2005)."
        ))

        if (sd1 > 0 && sd2 > 0) {
            val ratio = sd1 / sd2
            val ratioNorm = sd1sd2Norm()
            assessments.add(assess(
                "SD1/SD2 Ratio", ratio, ratioNorm,
                "The Poincaré SD1/SD2 ratio measures the balance between short-term (vagal) and " +
                "long-term (total autonomic) variability. SD1 reflects beat-to-beat vagal modulation " +
                "(= RMSSD/sqrt(2)), while SD2 captures broader regulatory dynamics. A healthy ratio " +
                "of 0.25-0.50 indicates balanced autonomic regulation."
            ))
        }

        if (peakTrough > 0) assessments.add(assess(
            "Peak-to-Trough Amplitude", peakTrough, peakTroughNorm(age),
            "This measures how much your heart rate oscillates with each breath during RF training — " +
            "the direct measure of respiratory sinus arrhythmia at resonance. It's the most sensitive " +
            "indicator of your biofeedback effectiveness. Beginners typically achieve 5-15 bpm; " +
            "experienced practitioners reach 25-50 bpm (Lehrer & Gevirtz 2014). " +
            "Training can improve amplitude by 30-100% over 10+ sessions."
        ))

        if (coherenceScore > 0) {
            val cohNorm = NormRange(0.5, 1.5, 3.0, "")
            val explanation = "HeartMath coherence is a proprietary metric (not used in mainstream research) " +
                "that measures spectral concentration at a single frequency. The standard research metrics " +
                "for biofeedback effectiveness are LF power and peak-to-trough amplitude (Lehrer & Gevirtz 2014). " +
                "Coherence is included for users familiar with HeartMath products."
            assessments.add(assess("Coherence Score", coherenceScore, cohNorm, explanation))
        }

        return assessments
    }
}
