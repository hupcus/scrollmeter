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

    /** "< 1 min", "12 min", "1 h 5 min", "2 h" — minutes are floored, so 59,9 min never reads "60 min". */
    fun duration(ms: Long?): String {
        if (ms == null || ms < 0) return UNKNOWN
        if (ms < MINUTE_MS) return "< 1 min"
        val minutes = ms / MINUTE_MS
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0L -> "$minutes min"
            rest == 0L -> "$hours h"
            else -> "$hours h $rest min"
        }
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
