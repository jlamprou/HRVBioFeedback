package com.hrv.biofeedback.domain.model

data class HrSample(
    val hr: Int,
    val rrsMs: List<Int>,
    val timestamp: Long = System.currentTimeMillis(),
    val contactDetected: Boolean = true
)
