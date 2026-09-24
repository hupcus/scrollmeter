package com.scrollmeter.app.insights

import kotlin.math.roundToInt

/**
 * "Scrolluješ X min z Y min v aplikaci (Z %)" (D19). The two times come from different sources —
 * scroll time from our events (ADR-022), time in app from UsageStats (ADR-021) — so the share is
 * clamped to 0–100. Null when either is unknown or the time in app is under a minute. Pure Kotlin.
 */
object ScrollShare {
    private const val MIN_FOREGROUND_MS = 60_000L

    fun percent(activeScrollMs: Long?, foregroundMs: Long?): Int? {
        if (activeScrollMs == null || foregroundMs == null || foregroundMs < MIN_FOREGROUND_MS || activeScrollMs < 0) return null
        return (activeScrollMs.toDouble() / foregroundMs * 100).roundToInt().coerceIn(0, 100)
    }
}
