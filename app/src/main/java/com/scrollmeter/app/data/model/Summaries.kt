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

/** Days before a date: the best total and how many had a distance (ADR-031). */
data class PriorDays(val bestMm: Double, val days: Int)

/** One `per_app.csv` row (spec §28 + ADR-021 columns); null = no data of that kind. */
data class ExportAppDay(
    val date: String,
    val packageName: String,
    val distanceMm: Double?,
    val measuredEventCount: Long?,
    val fallbackEventCount: Long?,
    val unmeasurableEventCount: Long?,
    val rejectedOutlierCount: Long?,
    val foregroundMs: Long?,
    val activeScrollMs: Long?,
)

/** One `daily_summary.csv` row (spec §28). */
data class ExportDay(val date: String, val distanceMm: Double?, val events: Long?)
