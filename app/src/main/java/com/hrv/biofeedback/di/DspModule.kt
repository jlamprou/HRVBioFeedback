package com.hrv.biofeedback.di

import com.hrv.biofeedback.domain.dsp.ArSpectralAnalyzer
import com.hrv.biofeedback.domain.dsp.ArtifactDetector
import com.hrv.biofeedback.domain.dsp.CoherenceCalculator
import com.hrv.biofeedback.domain.dsp.CrossSpectralAnalyzer
import com.hrv.biofeedback.domain.dsp.CubicSplineInterpolator
import com.hrv.biofeedback.domain.dsp.HrvMetricsCalculator
import com.hrv.biofeedback.domain.dsp.HrvProcessor
import com.hrv.biofeedback.domain.dsp.NonlinearAnalyzer
import com.hrv.biofeedback.domain.dsp.PeakTroughAnalyzer
import com.hrv.biofeedback.domain.dsp.PhaseAnalyzer
import com.hrv.biofeedback.domain.dsp.RespiratorySignalExtractor
import com.hrv.biofeedback.domain.dsp.SignalQualityMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DspModule {

    @Provides
    fun provideHrvProcessor(
        artifactDetector: ArtifactDetector,
        splineInterpolator: CubicSplineInterpolator,
        spectralAnalyzer: ArSpectralAnalyzer,
        metricsCalculator: HrvMetricsCalculator,
        coherenceCalculator: CoherenceCalculator,
        peakTroughAnalyzer: PeakTroughAnalyzer,
        nonlinearAnalyzer: NonlinearAnalyzer,
        crossSpectralAnalyzer: CrossSpectralAnalyzer,
        respiratoryExtractor: RespiratorySignalExtractor,
        signalQualityMonitor: SignalQualityMonitor
    ): HrvProcessor = HrvProcessor(
        artifactDetector,
        splineInterpolator,
        spectralAnalyzer,
        metricsCalculator,
        coherenceCalculator,
        peakTroughAnalyzer,
        nonlinearAnalyzer,
        crossSpectralAnalyzer,
        respiratoryExtractor,
        signalQualityMonitor
    )
}
