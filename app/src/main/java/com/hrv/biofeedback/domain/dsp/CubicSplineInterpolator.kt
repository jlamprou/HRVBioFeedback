package com.hrv.biofeedback.domain.dsp

import javax.inject.Inject

/**
 * Natural cubic spline interpolation for resampling unevenly-spaced RR intervals
 * to a uniform time grid required by spectral analysis.
 */
class CubicSplineInterpolator @Inject constructor() {

    data class SplineCoefficients(
        val a: DoubleArray,
        val b: DoubleArray,
        val c: DoubleArray,
        val d: DoubleArray,
        val x: DoubleArray
    )

    fun buildSpline(x: DoubleArray, y: DoubleArray): SplineCoefficients {
        val n = x.size - 1
        require(n >= 2) { "Need at least 3 data points for cubic spline" }

        val h = DoubleArray(n) { i -> x[i + 1] - x[i] }
        val a = y.copyOf()

        // Solve tridiagonal system for c coefficients
        val alpha = DoubleArray(n + 1)
        for (i in 1 until n) {
            alpha[i] = (3.0 / h[i]) * (a[i + 1] - a[i]) - (3.0 / h[i - 1]) * (a[i] - a[i - 1])
        }

        val c = DoubleArray(n + 1)
        val l = DoubleArray(n + 1)
        val mu = DoubleArray(n + 1)
        val z = DoubleArray(n + 1)

        l[0] = 1.0
        mu[0] = 0.0
        z[0] = 0.0

        for (i in 1 until n) {
            l[i] = 2.0 * (x[i + 1] - x[i - 1]) - h[i - 1] * mu[i - 1]
            mu[i] = h[i] / l[i]
            z[i] = (alpha[i] - h[i - 1] * z[i - 1]) / l[i]
        }

        l[n] = 1.0
        z[n] = 0.0
        c[n] = 0.0

        for (j in n - 1 downTo 0) {
            c[j] = z[j] - mu[j] * c[j + 1]
        }

        val b = DoubleArray(n)
        val d = DoubleArray(n)
        for (i in 0 until n) {
            b[i] = (a[i + 1] - a[i]) / h[i] - h[i] * (c[i + 1] + 2.0 * c[i]) / 3.0
            d[i] = (c[i + 1] - c[i]) / (3.0 * h[i])
        }

        return SplineCoefficients(a, b, c, d, x)
    }

    fun evaluate(spline: SplineCoefficients, xTarget: Double): Double {
        val x = spline.x
        val n = x.size - 1

        // Find the interval using binary search
        var i = 0
        var j = n
        while (j - i > 1) {
            val mid = (i + j) / 2
            if (x[mid] > xTarget) j = mid else i = mid
        }

        val dx = xTarget - x[i]
        return spline.a[i] + spline.b[i] * dx + spline.c[i] * dx * dx + spline.d[i] * dx * dx * dx
    }

    /**
     * Resample unevenly-spaced data to a uniform time grid.
     * @param timestamps cumulative time in seconds for each RR interval
     * @param values RR interval values in ms
     * @param sampleRate target sample rate in Hz (default 4.0)
     * @return Pair of (uniformTimestamps, resampledValues)
     */
    fun resample(
        timestamps: DoubleArray,
        values: DoubleArray,
        sampleRate: Double = 4.0
    ): Pair<DoubleArray, DoubleArray> {
        require(timestamps.size == values.size) { "timestamps and values must have same size" }
        require(timestamps.size >= 3) { "Need at least 3 data points" }

        val spline = buildSpline(timestamps, values)

        val tStart = timestamps.first()
        val tEnd = timestamps.last()
        val dt = 1.0 / sampleRate
        val numSamples = ((tEnd - tStart) * sampleRate).toInt()

        if (numSamples < 1) return Pair(doubleArrayOf(), doubleArrayOf())

        val uniformTime = DoubleArray(numSamples) { i -> tStart + i * dt }
        val uniformValues = DoubleArray(numSamples) { i ->
            val v = evaluate(spline, uniformTime[i])
            // Clamp to physiological range to prevent spline overshoot
            // (RR intervals: 300-2000 ms = 30-200 bpm)
            v.coerceIn(250.0, 2500.0)
        }

        return Pair(uniformTime, uniformValues)
    }
}
