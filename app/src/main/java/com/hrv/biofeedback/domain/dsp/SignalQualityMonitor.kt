package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject

/**
 * Monitors real-time signal quality from the Polar H10 and generates
 * user-facing alerts when data reliability is compromised.
 *
 * Uses both the SDK's contactStatus flag (reliable when ECG streaming is active)
 * and actual data flow as a fallback.
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
    private var lastBeatTimestamp = 0L
    private var dropoutCount = 0
    private val recentContactStatus = mutableListOf<Boolean>()

    companion object {
        private const val ARTIFACT_RATE_WARNING = 0.05
        private const val ARTIFACT_RATE_CRITICAL = 0.15
        private const val DROPOUT_GAP_MS = 3000L
        private const val CONTACT_WINDOW = 30
        private const val CONTACT_LOSS_WARNING = 0.10
        private const val NO_DATA_TIMEOUT_MS = 5000L
    }

    fun reset() {
        totalBeats = 0
        artifactBeats = 0
        lastBeatTimestamp = 0L
        dropoutCount = 0
        recentContactStatus.clear()
    }

    fun recordBeat(timestamp: Long, isArtifact: Boolean, contactDetected: Boolean) {
        totalBeats++
        if (isArtifact) artifactBeats++

        recentContactStatus.add(contactDetected)
        if (recentContactStatus.size > CONTACT_WINDOW) recentContactStatus.removeAt(0)

        if (lastBeatTimestamp > 0 && timestamp - lastBeatTimestamp > DROPOUT_GAP_MS) {
            dropoutCount++
        }
        lastBeatTimestamp = timestamp
    }

    fun getReport(): QualityReport {
        val alerts = mutableListOf<QualityAlert>()
        val now = System.currentTimeMillis()

        // --- Contact: use SDK flag when available, data flow as fallback ---
        val hasRecentData = lastBeatTimestamp > 0 && (now - lastBeatTimestamp) < NO_DATA_TIMEOUT_MS

        if (!hasRecentData && totalBeats > 0) {
            // No data arriving — genuine contact loss
            alerts.add(QualityAlert(
                "No data received — check chest strap contact",
                Severity.CRITICAL
            ))
        } else if (hasRecentData && recentContactStatus.size >= 5) {
            // Data is arriving — check SDK contact flag
            val contactLossRate = recentContactStatus.count { !it }.toDouble() / recentContactStatus.size
            if (contactLossRate >= 0.5) {
                // SDK says no contact but data IS arriving — warn but don't alarm
                alerts.add(QualityAlert(
                    "Weak electrode contact — moisten chest strap for better signal",
                    Severity.INFO
                ))
            } else if (contactLossRate >= CONTACT_LOSS_WARNING) {
                alerts.add(QualityAlert(
                    "Intermittent contact — check strap is snug",
                    Severity.INFO
                ))
            }
        } else if (totalBeats == 0) {
            alerts.add(QualityAlert(
                "Waiting for sensor data...",
                Severity.INFO
            ))
        }

        // --- Artifact rate ---
        if (totalBeats >= 20) {
            val artifactRate = artifactBeats.toDouble() / totalBeats
            if (artifactRate >= ARTIFACT_RATE_CRITICAL) {
                alerts.add(QualityAlert(
                    "High artifact rate (%.0f%%) — metrics may be unreliable".format(artifactRate * 100),
                    Severity.CRITICAL
                ))
            } else if (artifactRate >= ARTIFACT_RATE_WARNING) {
                alerts.add(QualityAlert(
                    "Moderate artifacts (%.0f%%)".format(artifactRate * 100),
                    Severity.WARNING
                ))
            }
        }

        // --- Dropouts ---
        if (dropoutCount >= 3) {
            alerts.add(QualityAlert(
                "Multiple data dropouts — keep phone close to strap",
                Severity.WARNING
            ))
        }

        val overall = when {
            alerts.any { it.severity == Severity.CRITICAL } -> Quality.POOR
            alerts.any { it.severity == Severity.WARNING } -> Quality.FAIR
            else -> Quality.GOOD
        }

        return QualityReport(overall, alerts)
    }

    val artifactRatePercent: Double
        get() = if (totalBeats > 0) (artifactBeats.toDouble() / totalBeats * 100) else 0.0
}
