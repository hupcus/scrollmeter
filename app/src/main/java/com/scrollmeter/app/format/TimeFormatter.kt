package com.scrollmeter.app.format

import java.text.NumberFormat
import java.util.Locale

/**
 * Durations and pace for display. An unknown value is "—", never "0 min" (PLAN Phase 4 DoD); a
 * known duration under a minute is "< 1 min". Pure Kotlin.
 */
object TimeFormatter {
    const val UNKNOWN = "—"
    private const val MINUTE_MS = 60_000L

    /**
     * "< 1 min", "12 min", "1 h 5 min", "2 h", "1 d 3 h 5 min" (ADR-036) — minutes are floored, so 59,9 min
     * never reads "60 min"; from 24 h on the days come first, and parts that are zero are left out
     * ("1 d 5 min"). The units read the same in Czech and English.
     */
    fun duration(ms: Long?): String {
        if (ms == null || ms < 0) return UNKNOWN
        if (ms < MINUTE_MS) return "< 1 min"
        val totalMinutes = ms / MINUTE_MS
        val days = totalMinutes / (24 * 60)
        val hours = totalMinutes / 60 % 24
        val minutes = totalMinutes % 60
        return listOfNotNull(
            days.takeIf { it > 0 }?.let { "$it d" },
            hours.takeIf { it > 0 }?.let { "$it h" },
            minutes.takeIf { it > 0 }?.let { "$it min" },
        ).joinToString(" ")
    }

    /**
     * Metres per minute (ADR-022). "—" when the time is unknown or under a minute — a pace over a few
     * seconds says nothing. Under 10 m/min with one decimal.
     */
    fun pace(distanceMm: Double?, timeMs: Long?, locale: Locale): String {
        if (distanceMm == null || timeMs == null || timeMs < MINUTE_MS || !distanceMm.isFinite() || distanceMm < 0) return UNKNOWN
        val perMinute = (distanceMm / 1_000.0) / (timeMs / MINUTE_MS.toDouble())
        val digits = if (Math.round(perMinute * 10) / 10.0 < 10) 1 else 0
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(perMinute)
        return "$number m/min"
    }
}
