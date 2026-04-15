package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject

/**
 * Monitors real-time signal quality from the Polar H10 and generates
 * user-facing alerts when data reliability is compromised.
 *
 * Tracks:
 * - Sensor contact stability (from Polar contactStatus)
 * - Artifact rate (percentage of rejected RR intervals)
 * - RR interval dropout (gaps in data stream)
 * - Motion contamination (excessive ACC variance during resting measurements)
 */
class SignalQualityMonitor @Inject constructor() {

    data class QualityReport(
        val overall: Quality,
        val alerts: List<QualityAlert>
    )

    data class QualityAlert(
        val message: String,
        val severity: Severity
    )

    enum class Quality { GOOD, FAIR, POOR }
    enum class Severity { INFO, WARNING, CRITICAL }

    private var totalBeats = 0
    private var artifactBeats = 0
    private var contactLossCount = 0
    private var lastBeatTimestamp = 0L
    private var dropoutCount = 0 // Gaps > 3 seconds
    private val recentContactStatus = mutableListOf<Boolean>() // Last 30 samples
    private val recentAccVariance = mutableListOf<Double>()

    companion object {
        private const val ARTIFACT_RATE_WARNING = 0.05  // 5%
        private const val ARTIFACT_RATE_CRITICAL = 0.15 // 15%
        private const val DROPOUT_GAP_MS = 3000L        // 3 seconds
        private const val CONTACT_WINDOW = 30
        private const val CONTACT_LOSS_WARNING = 0.10   // 10% of recent samples
        private const val MOTION_VARIANCE_THRESHOLD = 5000.0 // mg² — high motion
    }

    fun reset() {
        totalBeats = 0
        artifactBeats = 0
        contactLossCount = 0
        lastBeatTimestamp = 0L
        dropoutCount = 0
        recentContactStatus.clear()
        recentAccVariance.clear()
    }

    /**
     * Record a new RR interval processing result.
     */
    fun recordBeat(timestamp: Long, isArtifact: Boolean, contactDetected: Boolean) {
        totalBeats++
        if (isArtifact) artifactBeats++

        // Track contact status
        recentContactStatus.add(contactDetected)
        if (recentContactStatus.size > CONTACT_WINDOW) recentContactStatus.removeAt(0)
        if (!contactDetected) contactLossCount++

        // Check for data dropout
        if (lastBeatTimestamp > 0 && timestamp - lastBeatTimestamp > DROPOUT_GAP_MS) {
            dropoutCount++
        }
        lastBeatTimestamp = timestamp
    }

    /**
     * Record accelerometer variance for motion detection.
     * High variance during a resting measurement indicates the user is moving.
     */
    fun recordAccVariance(variance: Double) {
        recentAccVariance.add(variance)
        if (recentAccVariance.size > 10) recentAccVariance.removeAt(0)
    }

    /**
     * Generate the current quality report with all active alerts.
     */
    fun getReport(): QualityReport {
        val alerts = mutableListOf<QualityAlert>()

        // --- Contact quality ---
        if (recentContactStatus.size >= 5) {
            val contactLossRate = recentContactStatus.count { !it }.toDouble() / recentContactStatus.size
            if (contactLossRate >= 0.5) {
                alerts.add(QualityAlert(
                    "Sensor contact lost — adjust chest strap position",
                    Severity.CRITICAL
                ))
            } else if (contactLossRate >= CONTACT_LOSS_WARNING) {
                alerts.add(QualityAlert(
                    "Intermittent contact — check strap is snug and moistened",
                    Severity.WARNING
                ))
            }
        }

        // --- Artifact rate ---
        if (totalBeats >= 20) {
            val artifactRate = artifactBeats.toDouble() / totalBeats
            if (artifactRate >= ARTIFACT_RATE_CRITICAL) {
                alerts.add(QualityAlert(
                    "High artifact rate (%.0f%%) — metrics may be unreliable. Stay still and check strap.".format(artifactRate * 100),
                    Severity.CRITICAL
                ))
            } else if (artifactRate >= ARTIFACT_RATE_WARNING) {
                alerts.add(QualityAlert(
                    "Moderate artifacts (%.0f%%) — some metric accuracy may be reduced".format(artifactRate * 100),
                    Severity.WARNING
                ))
            }
        }

        // --- Data dropouts ---
        if (dropoutCount >= 3) {
            alerts.add(QualityAlert(
                "Multiple data dropouts detected — Bluetooth connection may be unstable",
                Severity.WARNING
            ))
        } else if (dropoutCount >= 1) {
            alerts.add(QualityAlert(
                "Brief data gap detected — keep phone close to chest strap",
                Severity.INFO
            ))
        }

        // --- Motion during measurement ---
        if (recentAccVariance.size >= 3) {
            val avgVariance = recentAccVariance.average()
            if (avgVariance > MOTION_VARIANCE_THRESHOLD) {
                alerts.add(QualityAlert(
                    "Motion detected — stay still for accurate HRV measurement",
                    Severity.WARNING
                ))
            }
        }

        // --- Overall quality ---
        val overall = when {
            alerts.any { it.severity == Severity.CRITICAL } -> Quality.POOR
            alerts.any { it.severity == Severity.WARNING } -> Quality.FAIR
            else -> Quality.GOOD
        }

        return QualityReport(overall, alerts)
    }

    /** Current artifact rate as percentage (0-100). */
    val artifactRatePercent: Double
        get() = if (totalBeats > 0) (artifactBeats.toDouble() / totalBeats * 100) else 0.0
}
