package com.scrollmeter.app.data.model

/**
 * One app over a date range. Nullable = no data of that kind (spec §20 "—", never 0): an app with
 * time but no scroll (YouTube) has no distance; without Usage access nothing has foreground time.
 */
data class AppSummary(
    val packageName: String,
    val distanceMm: Double?,
    val activeScrollMs: Long?,
    val foregroundMs: Long?,
    val measuredEventCount: Long?,
    val fallbackEventCount: Long?,
    val unmeasurableEventCount: Long?,
    val rejectedOutlierCount: Long?,
)

/** One local day over all apps. */
data class DaySummary(
    val date: String,
    val distanceMm: Double?,
    val activeScrollMs: Long?,
    val foregroundMs: Long?,
)
