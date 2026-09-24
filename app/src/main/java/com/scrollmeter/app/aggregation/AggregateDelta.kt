package com.scrollmeter.app.aggregation

/**
 * What one flush adds to one `daily_app_aggregate` row (spec §16, §17, D8). Deltas are summed —
 * in memory before a flush and by the DAO in the database — never written over a row.
 * Timestamps are wall-clock ms of the first / last *counted* event; the version is the newest
 * calibration any of its distances was computed under (spec §65).
 */
data class AggregateDelta(
    val date: String,
    val packageName: String,
    val distanceMm: Double = 0.0,
    val horizontalDistanceMm: Double = 0.0,
    val verticalDistanceMm: Double = 0.0,
    val rawDeltaXPx: Long = 0,
    val rawDeltaYPx: Long = 0,
    val measuredEventCount: Long = 0,
    val fallbackEventCount: Long = 0,
    val unmeasurableEventCount: Long = 0,
    val rejectedOutlierCount: Long = 0,
    val firstEventTimestamp: Long? = null,
    val lastEventTimestamp: Long? = null,
    val calibrationVersion: Int = 0,
    val activeScrollMs: Long = 0,
) {
    operator fun plus(other: AggregateDelta): AggregateDelta {
        require(date == other.date && packageName == other.packageName) { "different rows" }
        return AggregateDelta(
            date = date,
            packageName = packageName,
            distanceMm = distanceMm + other.distanceMm,
            horizontalDistanceMm = horizontalDistanceMm + other.horizontalDistanceMm,
            verticalDistanceMm = verticalDistanceMm + other.verticalDistanceMm,
            rawDeltaXPx = rawDeltaXPx + other.rawDeltaXPx,
            rawDeltaYPx = rawDeltaYPx + other.rawDeltaYPx,
            measuredEventCount = measuredEventCount + other.measuredEventCount,
            fallbackEventCount = fallbackEventCount + other.fallbackEventCount,
            unmeasurableEventCount = unmeasurableEventCount + other.unmeasurableEventCount,
            rejectedOutlierCount = rejectedOutlierCount + other.rejectedOutlierCount,
            firstEventTimestamp = minOfNullable(firstEventTimestamp, other.firstEventTimestamp),
            lastEventTimestamp = maxOfNullable(lastEventTimestamp, other.lastEventTimestamp),
            calibrationVersion = maxOf(calibrationVersion, other.calibrationVersion),
            activeScrollMs = activeScrollMs + other.activeScrollMs,
        )
    }

    private companion object {
        fun minOfNullable(a: Long?, b: Long?): Long? = if (a == null) b else if (b == null) a else minOf(a, b)
        fun maxOfNullable(a: Long?, b: Long?): Long? = if (a == null) b else if (b == null) a else maxOf(a, b)
    }
}

/** A scroll session that has ended (spec §18, D9) — one `scroll_session` row. */
data class ClosedSession(
    val packageName: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val distanceMm: Double,
    val eventCount: Long,
)
