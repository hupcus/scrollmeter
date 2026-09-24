package com.scrollmeter.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per local day and app (spec §17, D8). Every flush **adds** to it; distances keep the
 * calibration they were measured with and [calibrationVersion] is the newest one used (spec §65).
 * [activeScrollMs] is time spent scrolling (ADR-022). `date` is an ISO local date (D10).
 */
@Entity(tableName = "daily_app_aggregate", primaryKeys = ["date", "packageName"])
data class DailyAppAggregateEntity(
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
)

/** A closed scroll session (spec §18, D9) — stored now, shown from Phase 1.1 (ADR-011). */
@Entity(tableName = "scroll_session", indices = [Index("startTimestamp")])
data class ScrollSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val distanceMm: Double,
    val eventCount: Long,
)

/** Time in app per local day (ADR-021) — replaced as a whole by every usage sync. */
@Entity(tableName = "daily_app_usage", primaryKeys = ["date", "packageName"])
data class DailyAppUsageEntity(
    val date: String,
    val packageName: String,
    val foregroundMs: Long,
    val launchCount: Int,
    val lastEventTimestamp: Long?,
    val syncedAt: Long,
)
