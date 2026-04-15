package com.hrv.biofeedback.domain.model

/**
 * Raw ECG sample from Polar H10 at 130 Hz.
 * Voltage in microvolts (uV).
 */
data class EcgSample(
    val timestamp: Long,
    val voltage: Int // microvolts
)

/**
 * 3-axis accelerometer sample from Polar H10.
 * Values in millig (mg). The z-axis (anterior-posterior) is most
 * informative for chest-mounted respiratory detection.
 */
data class AccSample(
    val timestamp: Long,
    val x: Int, // mg - lateral
    val y: Int, // mg - vertical
    val z: Int  // mg - anterior-posterior (breathing axis)
)
