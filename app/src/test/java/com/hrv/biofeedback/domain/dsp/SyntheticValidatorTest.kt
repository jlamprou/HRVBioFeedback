package com.hrv.biofeedback.domain.dsp

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Synthetic validation: feeds known mathematical signals through the entire
 * DSP pipeline and verifies every metric against expected values.
 *
 * Simulates realistic Polar H10 output (integer ms RR intervals).
 * Run with: ./gradlew :app:testDebugUnitTest --tests "*.SyntheticValidatorTest"
 */
class SyntheticValidatorTest {

    private lateinit var artifactDetector: ArtifactDetector
    private lateinit var splineInterpolator: CubicSplineInterpolator
    private lateinit var spectralAnalyzer: ArSpectralAnalyzer
    private lateinit var metricsCalculator: HrvMetricsCalculator
    private lateinit var coherenceCalculator: CoherenceCalculator
    private lateinit var peakTroughAnalyzer: PeakTroughAnalyzer
    private lateinit var nonlinearAnalyzer: NonlinearAnalyzer

    @Before
    fun setup() {
        artifactDetector = ArtifactDetector()
        splineInterpolator = CubicSplineInterpolator()
        spectralAnalyzer = ArSpectralAnalyzer()
        metricsCalculator = HrvMetricsCalculator()
        coherenceCalculator = CoherenceCalculator()
        peakTroughAnalyzer = PeakTroughAnalyzer()
        nonlinearAnalyzer = NonlinearAnalyzer()
    }

    // === Sinusoidal RSA at 0.1 Hz (simulates 6 bpm RF breathing with Polar H10) ===

    @Test
    fun `peak frequency detected at 0_1 Hz for 6 bpm breathing`() {
        val psd = analyzePsd(sinRR())
        val peak = metricsCalculator.peakLfFrequency(psd)
        assertTrue("Peak $peak should be ~0.1 Hz", abs(peak - 0.1) < 0.015)
    }

    @Test
    fun `LF power dominates HF for breathing at 0_1 Hz`() {
        val psd = analyzePsd(sinRR())
        val lf = metricsCalculator.calculateLfPower(psd)
        val hf = metricsCalculator.calculateHfPower(psd)
        assertTrue("LF ($lf) should >> HF ($hf)", hf == 0.0 || lf / hf > 5)
    }

    @Test
    fun `coherence high for pure sinusoidal RR`() {
        val psd = analyzePsd(sinRR())
        val coh = coherenceCalculator.calculate(psd)
        assertTrue("Coherence ${coh.coherenceScore} should be > 1.0", coh.coherenceScore > 1.0)
    }

    @Test
    fun `mean HR correct from 857ms base RR`() {
        val hr = metricsCalculator.calculateMeanHr(sinRR())
        assertTrue("Mean HR $hr should be ~70 bpm", hr in 68.0..72.0)
    }

    @Test
    fun `RMSSD positive for sinusoidal RR`() {
        assertTrue(metricsCalculator.calculateRmssd(sinRR()) > 10)
    }

    @Test
    fun `SDNN reflects sinusoidal modulation amplitude`() {
        val sdnn = metricsCalculator.calculateSdnn(sinRR())
        assertTrue("SDNN $sdnn should be ~35 ms", sdnn in 20.0..55.0)
    }

    @Test
    fun `peak-to-trough amplitude correct for known modulation`() {
        val (_, resampled) = resample(sinRR())
        val hr = resampled.map { 60000.0 / it }.toDoubleArray()
        val amp = peakTroughAnalyzer.analyze(hr, 4.0, 6.0)
        assertTrue("Amplitude $amp should be ~8 bpm", amp in 4.0..14.0)
    }

    // === Frequency detection tracks breathing rate across the RF range ===

    @Test fun `detects 4_5 bpm`() = assertFreq(4.5)
    @Test fun `detects 5_5 bpm`() = assertFreq(5.5)
    @Test fun `detects 6_0 bpm`() = assertFreq(6.0)
    @Test fun `detects 6_5 bpm`() = assertFreq(6.5)

    // === Zero variability (constant RR) produces zero metrics ===

    @Test fun `RMSSD zero for constant RR`() = assertEquals(0.0, metricsCalculator.calculateRmssd(constRR()), 0.001)
    @Test fun `SDNN zero for constant RR`() = assertEquals(0.0, metricsCalculator.calculateSdnn(constRR()), 0.001)
    @Test fun `pNN50 zero for constant RR`() = assertEquals(0.0, metricsCalculator.calculatePnn50(constRR()), 0.001)
    @Test fun `SD1 zero for constant RR`() = assertEquals(0.0, nonlinearAnalyzer.poincare(constRR()).first, 0.001)

    // === Artifact detection ===

    @Test fun `rejects RR below 300ms`()  = assertTrue(artifactDetector.detect(200.0, ctx()).isArtifact)
    @Test fun `rejects RR above 2000ms`() = assertTrue(artifactDetector.detect(2500.0, ctx()).isArtifact)
    @Test fun `accepts normal 860ms RR`()  = assertFalse(artifactDetector.detect(860.0, ctx()).isArtifact)
    @Test fun `rejects 28pct deviation`()  = assertTrue(artifactDetector.detect(1100.0, ctx()).isArtifact)

    @Test
    fun `corrected artifact value near context`() {
        val r = artifactDetector.detect(1100.0, ctx())
        assertTrue("Corrected ${r.correctedRr} should be ~857", r.correctedRr in 800.0..920.0)
    }

    // === Nonlinear metrics ===

    @Test
    fun `SD1 equals RMSSD div sqrt2`() {
        val rr = sinRR()
        val expected = metricsCalculator.calculateRmssd(rr) / sqrt(2.0)
        val (sd1, _) = nonlinearAnalyzer.poincare(rr)
        assertTrue("SD1 $sd1 ≈ $expected", abs(sd1 - expected) < 1.0)
    }

    @Test
    fun `SD2 greater than SD1`() {
        val (sd1, sd2) = nonlinearAnalyzer.poincare(sinRR())
        assertTrue("SD2 $sd2 > SD1 $sd1", sd2 > sd1)
    }

    @Test
    fun `DFA alpha1 above 0_8 for periodic signal`() {
        assertTrue(nonlinearAnalyzer.dfaAlpha1(sinRR()) > 0.8)
    }

    @Test
    fun `SampEn low for periodic signal`() {
        assertTrue(nonlinearAnalyzer.sampleEntropy(sinRR()) < 1.5)
    }

    // === AR spectral analyzer stability ===

    @Test
    fun `Burg coefficients are all finite`() {
        val (_, resampled) = resample(sinRR())
        val mean = resampled.average()
        val centered = DoubleArray(resampled.size) { resampled[it] - mean }
        val (coeffs, err) = spectralAnalyzer.burgMethod(centered, 16)
        assertTrue(err.isFinite() && err > 0)
        assertTrue(coeffs.all { it.isFinite() })
    }

    @Test
    fun `PSD contains no NaN or Inf`() {
        val psd = analyzePsd(sinRR())
        assertTrue(psd.power.none { it.isNaN() })
        assertTrue(psd.power.none { it.isInfinite() })
    }

    // === Helpers ===

    /** Standard sinusoidal test signal: 857ms base, ±50ms at 0.1Hz, 120s, integer ms */
    private fun sinRR() = genSin(857.0, 50.0, 0.1, 120)
    private fun constRR() = List(200) { 857.0 }
    private fun ctx() = List(20) { 857.0 }

    private fun assertFreq(bpm: Double) {
        val f = bpm / 60.0
        val peak = metricsCalculator.peakLfFrequency(analyzePsd(genSin(857.0, 40.0, f, 120)))
        assertTrue("Peak $peak should be ~$f Hz", abs(peak - f) < 0.015)
    }

    private fun genSin(base: Double, amp: Double, freq: Double, dur: Int): List<Double> {
        val rrs = mutableListOf<Double>()
        var t = 0.0
        while (t < dur) {
            val rr = (base + amp * sin(2.0 * PI * freq * t)).toInt().toDouble()
            rrs.add(rr)
            t += rr / 1000.0
        }
        return rrs
    }

    private fun resample(rr: List<Double>): Pair<DoubleArray, DoubleArray> {
        var cum = 0.0
        val ts = rr.map { cum += it / 1000.0; cum }.toDoubleArray()
        return splineInterpolator.resample(ts, rr.toDoubleArray(), 4.0)
    }

    private fun analyzePsd(rr: List<Double>): PowerSpectralDensity {
        val (_, resampled) = resample(rr)
        return spectralAnalyzer.analyze(resampled, 4.0)
    }
}
