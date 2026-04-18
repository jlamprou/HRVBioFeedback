package com.hrv.biofeedback.domain.usecase.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.hrv.biofeedback.domain.repository.SessionDetail
import com.hrv.biofeedback.domain.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Comprehensive session data export to ZIP containing three CSV files:
 *
 * 1. rr_intervals.csv — timestamp_ms, rr_ms
 *    All raw RR intervals captured during the session. Compatible with Kubios HRV
 *    and other research tools (import as "RR intervals" format).
 *
 * 2. metrics_timeline.csv — timestamp_ms, hr, rmssd, sdnn, pnn50, lf_power, hf_power,
 *    lf_hf_ratio, coherence_score, peak_frequency, peak_trough_amp, sd1, sd2,
 *    dfa_alpha1, sample_entropy, cardioresp_coherence, breathing_rate, cardioresp_phase
 *    Periodic snapshots of all metrics throughout the session (sliding-window values).
 *
 * 3. session_summary.csv — single row with session-level metadata and definitive
 *    full-recording metrics (Task Force compliant).
 *
 * 4. rf_assessment_steps.csv (only for ASSESSMENT sessions) — per-step RF assessment
 *    results with all 6 Shaffer criteria scores.
 */
class SessionExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository
) {

    suspend fun exportSession(sessionId: Long): Intent? {
        val detail = sessionRepository.getSessionDetail(sessionId) ?: return null

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(detail.summary.startTime))
        val typeLabel = detail.summary.type.name.lowercase()
        val zipFile = File(exportsDir, "hrv_${typeLabel}_${timestamp}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            writeSessionSummary(zip, detail)
            writeRrIntervals(zip, detail)
            writeMetricsTimeline(zip, detail)
            if (detail.rfStepResults?.isNotEmpty() == true) {
                writeRfAssessmentSteps(zip, detail)
            }
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "HRV Session Export — ${detail.summary.type.name} ${timestamp}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeSessionSummary(zip: ZipOutputStream, detail: SessionDetail) {
        zip.putNextEntry(ZipEntry("session_summary.csv"))
        val s = detail.summary
        val header = listOf(
            "session_id", "type", "start_time_ms", "end_time_ms", "duration_seconds",
            "breathing_rate_bpm", "rf_result_bpm",
            "avg_hr_bpm", "avg_rmssd_ms", "avg_sdnn_ms", "avg_pnn50_pct",
            "avg_lf_power_ms2", "avg_hf_power_ms2", "avg_lf_hf_ratio",
            "avg_coherence_score", "avg_peak_trough_amp_bpm",
            "avg_sd1_ms", "avg_sd2_ms", "avg_dfa_alpha1", "avg_sample_entropy",
            "avg_cardioresp_coherence", "detected_breathing_rate_bpm",
            "artifact_rate_pct", "notes"
        )
        val row = listOf(
            s.id.toString(), s.type.name, s.startTime.toString(), s.endTime.toString(),
            s.durationSeconds.toString(), s.breathingRate.toString(),
            s.rfResult?.toString() ?: "",
            s.averageHr.toString(), s.averageRmssd.toString(), s.averageSdnn.toString(),
            s.averagePnn50.toString(),
            s.averageLfPower.toString(), s.averageHfPower.toString(), s.averageLfHfRatio.toString(),
            s.averageCoherence.toString(), s.averagePeakTrough.toString(),
            s.averageSd1.toString(), s.averageSd2.toString(),
            s.averageDfaAlpha1.toString(), s.averageSampleEntropy.toString(),
            s.averageCardiorespCoherence.toString(), s.detectedBreathingRate.toString(),
            s.artifactRatePercent.toString(), csvEscape(s.notes)
        )
        val writer = zip.bufferedWriter(Charsets.UTF_8)
        writer.write(header.joinToString(","))
        writer.newLine()
        writer.write(row.joinToString(","))
        writer.newLine()
        writer.flush()
        zip.closeEntry()
    }

    private fun writeRrIntervals(zip: ZipOutputStream, detail: SessionDetail) {
        zip.putNextEntry(ZipEntry("rr_intervals.csv"))
        val writer = zip.bufferedWriter(Charsets.UTF_8)
        writer.write("timestamp_ms,rr_ms")
        writer.newLine()
        for ((ts, rr) in detail.rrIntervals) {
            writer.write("$ts,$rr")
            writer.newLine()
        }
        writer.flush()
        zip.closeEntry()
    }

    private fun writeMetricsTimeline(zip: ZipOutputStream, detail: SessionDetail) {
        zip.putNextEntry(ZipEntry("metrics_timeline.csv"))
        val writer = zip.bufferedWriter(Charsets.UTF_8)
        writer.write(
            "timestamp_ms,hr,rmssd,sdnn,pnn50,lf_power,hf_power,lf_hf_ratio," +
            "total_power,coherence_score,peak_frequency_hz,peak_trough_amp_bpm," +
            "sd1,sd2,dfa_alpha1,sample_entropy," +
            "breathing_rate_bpm,cardioresp_coherence,cardioresp_phase_deg"
        )
        writer.newLine()
        for (m in detail.metricsTimeline) {
            writer.write(
                "${m.timestamp},${m.hr},${m.rmssd},${m.sdnn},${m.pnn50}," +
                "${m.lfPower},${m.hfPower},${m.lfHfRatio},${m.totalPower}," +
                "${m.coherenceScore},${m.peakFrequency},${m.peakTroughAmplitude}," +
                "${m.sd1},${m.sd2},${m.dfaAlpha1},${m.sampleEntropy}," +
                "${m.breathingRate},${m.cardiorespCoherence},${m.cardiorespPhase}"
            )
            writer.newLine()
        }
        writer.flush()
        zip.closeEntry()
    }

    private fun writeRfAssessmentSteps(zip: ZipOutputStream, detail: SessionDetail) {
        val steps = detail.rfStepResults ?: return
        zip.putNextEntry(ZipEntry("rf_assessment_steps.csv"))
        val writer = zip.bufferedWriter(Charsets.UTF_8)
        writer.write(
            "breathing_rate_bpm,lf_power,hf_power,coherence_score,peak_trough_amp_bpm," +
            "phase_sync,curve_smoothness,lf_peak_count,combined_score"
        )
        writer.newLine()
        for (s in steps) {
            writer.write(
                "${s.breathingRate},${s.lfPower},${s.hfPower},${s.coherenceScore}," +
                "${s.peakTroughAmplitude},${s.phaseSync},${s.curveSmoothness}," +
                "${s.lfPeakCount},${s.combinedScore}"
            )
            writer.newLine()
        }
        writer.flush()
        zip.closeEntry()
    }

    private fun csvEscape(value: String): String {
        if (value.isEmpty()) return ""
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            return "\"${value.replace("\"", "\"\"")}\""
        }
        return value
    }
}
