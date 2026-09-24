package com.scrollmeter.app.insights

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The y axis of a bar chart (D13, spec §23): a round maximum and evenly spaced grid lines, so the
 * labels read "0 · 100 m · 200 m …" instead of "0 · 106 m · 212 m". Steps are 1, 2, 2,5 or 5 × 10ⁿ
 * with about four intervals. Pure Kotlin; values in any unit.
 */
data class ChartScale(val max: Double, val step: Double) {
    val gridLines: List<Double> get() = (0..Math.round(max / step).toInt()).map { it * step }

    /** 0..1 of the plot height; values above [max] are clipped. */
    fun fraction(value: Double): Float = if (max <= 0) 0f else (value / max).coerceIn(0.0, 1.0).toFloat()

    companion object {
        private val NICE = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)

        /** Minute steps that read as clock time ("15 min", "30 min", "1 h", "2 h"), never "2,5 min". */
        private val MINUTE_STEPS = doubleArrayOf(1.0, 2.0, 5.0, 10.0, 15.0, 20.0, 30.0, 60.0, 120.0, 180.0, 240.0, 360.0, 480.0, 720.0)

        /** A time axis in minutes; [emptyMax] as in [of]. Above the last step it keeps the 12 h step. */
        fun ofMinutes(maxMinutes: Double, emptyMax: Double, intervals: Int = 4): ChartScale {
            val top = if (maxMinutes.isFinite() && maxMinutes > 0) maxMinutes else emptyMax
            val raw = top / intervals
            val step = MINUTE_STEPS.firstOrNull { it >= raw - 1e-9 * raw } ?: MINUTE_STEPS.last()
            return ChartScale(ceil(top / step - 1e-9) * step, step)
        }

        /** [emptyMax] is the axis when every value is 0 (e.g. 1 m in millimetres). */
        fun of(maxValue: Double, emptyMax: Double, intervals: Int = 4): ChartScale {
            val top = if (maxValue.isFinite() && maxValue > 0) maxValue else emptyMax
            val raw = top / intervals
            val magnitude = 10.0.pow(floor(log10(raw)))
            val step = NICE.first { it * magnitude >= raw - 1e-9 * raw } * magnitude
            return ChartScale(ceil(top / step - 1e-9) * step, step)
        }
    }
}
