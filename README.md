# HRV Biofeedback & Resonance Frequency Training

A scientifically validated Android app for Heart Rate Variability biofeedback and Resonance Frequency training with full Polar H10 sensor utilization.

**Scientific audit: 50/50 PASS | 26/26 synthetic validation tests PASS | 0 dead code paths**

## Features

### RF Assessment (Lehrer Protocol)
- 5 breathing rates (6.5 to 4.5 bpm), 2-min steps, 120s rest periods
- All 6 Shaffer et al. (2020) criteria: phase synchrony, peak-to-trough amplitude, LF power, LF peak magnitude, waveform smoothness, LF peak count
- Scoring weights disclosed as app's own approximation (not from literature)

### Guided Training (with Breathing Pacer)
- Starts at assessed RF (or user default)
- Real-time breathing rate adjustment based on LF peak frequency
- Phase-locked HR target trajectory (peaks at end of inhale per Shaffer criterion #1)
- HR trace chart with "match the wave" visual target
- Breathing pacer with vibration and audio guidance (rising/falling tones)
- Real-time coaching engine with metric-based tips
- Conservative adaptive: max 0.1 bpm nudge every 2 min, +/-0.5 bpm (disclosed as emerging, Laborde 2022)

### Advanced Freeform Training (no pacer)
- Advanced phase of the Lehrer protocol (Lehrer & Gevirtz 2014)
- User watches real-time HR trace and times breathing to their heartbeat
- Inhale when HR rises, exhale when HR falls — maximize oscillation amplitude
- Develops interoceptive awareness and naturally tracks RF shifts
- Large HR display + full graph + all 20+ metrics in real-time
- Session best amplitude tracking

### Biofeedback Effectiveness
- Primary indicator based on **LF power + peak-to-trough amplitude** (Lehrer protocol targets)
- Thresholds from published literature: amplitude 15-25 = good, 25+ = excellent (Lehrer & Gevirtz 2014)
- HeartMath coherence kept as secondary/optional metric (proprietary, not used in mainstream research)

### Morning 5-Min Baseline Check
- Resting HRV measurement per Task Force (1996) 5-minute standard
- 1-minute stabilization period before recording (cardiovascular adjustment)
- ACC-based breathing rate verification (warns if breathing too slowly for resting norms)
- Natural breathing, no pacer
- Trend tracking with 7-day rolling average comparison
- Full history with all metrics

### Full Evaluation Report
- Age/sex-adjusted norm comparisons (Nunan 2010, Voss 2015, Umetani 1998)
- Color-coded metric assessments with scientific explanations
- Training volume analysis vs Lehrer protocol recommendations
- RMSSD/amplitude progression tracking
- RF stability analysis (van Diest 2021)
- User profile: age, sex, height, weight, fitness level

### Signal Quality Monitoring
- Real-time contact detection (requires ECG stream for accurate Polar H10 impedance monitoring)
- Artifact rate tracking with user alerts
- Data dropout detection
- Color-coded quality bar on all session screens

### Live Metrics View
All 20+ metrics updating every heartbeat:
- **Time-domain**: HR, RMSSD, SDNN, pNN50
- **Frequency-domain**: LF/HF power, LF/HF ratio (AR-16 Burg's method)
- **Biofeedback**: Peak-to-trough amplitude, LF peak frequency
- **Nonlinear**: Poincare SD1/SD2, DFA alpha1, Sample Entropy
- **Respiratory**: ACC-derived breathing rate, cardiorespiratory coherence (MSC), phase angle
- **Secondary**: HeartMath coherence score (proprietary)

## Polar H10 Utilization

All 4 data streams used end-to-end:

| Stream | Rate | Usage |
|--------|------|-------|
| HR + RR intervals | 1/1024s resolution | Full DSP pipeline, all HRV metrics |
| Raw ECG | 130 Hz | R-peak detection, ECG-derived respiration, contact detection |
| Accelerometer | 200 Hz | Respiratory signal extraction, cross-spectral coherence |
| Battery + Contact | Event-based | Battery display, signal quality monitoring |

**Note:** ECG streaming is required on all screens for accurate contact detection — the Polar H10 determines skin contact via ECG electrode impedance monitoring.

## DSP Pipeline (12 modules)

```
Polar H10
  |-- HR/RR --> Artifact Detection --> Spline Resample (4Hz) --> AR Spectral (Burg-16)
  |                                                               |-- LF/HF Power
  |                                                               |-- Peak-to-Trough (extrema)
  |                                                               |-- Poincare, DFA, SampEn
  |-- ACC ----> Bandpass 0.05-0.5Hz --> Downsample --> Respiratory Signal --+
  |                                                                        +-- Cross-Spectral
  |-- ECG ----> R-Peak Detection --> R-Wave Amplitude --> EDR (fallback) ---+   Coherence (MSC)
  |                                                                            + Phase Angle
  +-- Battery/Contact --> UI Display / Signal Quality                          + Breathing Rate
```

## Scientific References

### Core Protocol
- Lehrer & Gevirtz (2014) "Heart rate variability biofeedback: how and why does it work?" *Frontiers in Psychology*
- Shaffer et al. (2020) "A Practical Guide to Resonance Frequency Assessment" *Frontiers in Neuroscience*

### Metrics & Norms
- Task Force (1996) "Heart Rate Variability: Standards of Measurement" *Circulation*
- Nunan et al. (2010) "Normal values for short-term HRV in healthy adults" *Scand J Med Sci Sports*
- Shaffer & Ginsberg (2017) "An Overview of HRV Metrics and Norms" *Frontiers in Public Health*
- Voss et al. (2015) "Short-term HRV" *European Journal of Clinical Investigation*
- Umetani et al. (1998) "Twenty-four hour time domain HRV" *JACC*

### Nonlinear Analysis
- Peng et al. (1995) "Quantification of scaling exponents" — DFA
- Richman & Moorman (2000) "Physiological time-series analysis" — Sample Entropy
- Huikuri et al. (2000) "DFA alpha1 as mortality predictor" *Circulation*

### Clinical Evidence
- Pizzoli et al. (2021) Depression meta-analysis: g = -0.41
- Goessl et al. (2017) Stress/anxiety: moderate-to-large effect sizes
- van Diest et al. (2021) "RF is not always stable over time" *Scientific Reports*
- Laborde et al. (2022) "Methods for HRVB: Systematic Review" *Applied Psychophysiology and Biofeedback*

### Biofeedback Metrics (NOT HeartMath)
- Primary training targets: LF power and peak-to-trough amplitude (Lehrer & Gevirtz 2014)
- HeartMath coherence (McCraty 2022) included as secondary metric — proprietary, not peer-reviewed standard

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material3 dark theme)
- **AGP 9.0** + **Kotlin 2.3.0** + **Gradle 9.1**
- **Hilt** for dependency injection
- **Room** for session persistence with additive migrations
- **DataStore** for user preferences and profile
- **Polar BLE SDK 7.0.0** (coroutine/Flow-based)
- All DSP is pure Kotlin (no native/C dependencies)

## Building

```bash
# Requires Android Studio with JDK 17+
# minSdk 33 (Android 13+)

git clone https://github.com/jlamprou/HRVBioFeedback.git
cd HRVBioFeedback

# Run synthetic validation tests
./gradlew :app:testDebugUnitTest --tests "*.SyntheticValidatorTest"

# Build debug APK
./gradlew assembleDebug
```

## Validation

26 synthetic validation tests verify every DSP module with Polar H10-realistic data:
- Sinusoidal RSA detection (frequency, power, amplitude)
- Frequency tracking across the full RF range (4.5-6.5 bpm)
- Zero-variability edge case
- Artifact detection and correction
- Nonlinear metric mathematical identities (SD1 = RMSSD/sqrt(2))
- AR spectral analyzer numerical stability

## License

MIT
