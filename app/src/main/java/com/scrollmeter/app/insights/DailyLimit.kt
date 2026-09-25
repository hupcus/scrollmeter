package com.scrollmeter.app.insights

import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.measurement.MeasurementConfig
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How close a distance is to the daily limit (ADR-036). The app is not a competition: the limit is
 * what the user does not want to go over, and the colour says how close they are. NONE = no limit set.
 */
enum class LimitLevel { NONE, UNDER, NEAR, OVER }

/** [remainingMm] until the limit (0 when over), [overMm] past it (0 when under). */
data class LimitStatus(val level: LimitLevel, val limitMm: Double, val remainingMm: Double, val overMm: Double)

/** Pure Kotlin. */
object DailyLimit {
    /** UNDER below [MeasurementConfig.LIMIT_WARN_RATIO] of the limit, NEAR from there, OVER from the limit on. */
    fun level(distanceMm: Double?, limitMm: Double): LimitLevel {
        if (limitMm <= 0.0) return LimitLevel.NONE
        val ratio = (distanceMm ?: 0.0) / limitMm
        return when {
            ratio >= 1.0 -> LimitLevel.OVER
            ratio >= MeasurementConfig.LIMIT_WARN_RATIO -> LimitLevel.NEAR
            else -> LimitLevel.UNDER
        }
    }

    fun status(distanceMm: Double?, limitMm: Double): LimitStatus {
        val value = distanceMm ?: 0.0
        return LimitStatus(
            level = level(value, limitMm),
            limitMm = limitMm,
            remainingMm = (limitMm - value).coerceAtLeast(0.0),
            overMm = (value - limitMm).coerceAtLeast(0.0),
        )
    }

    /**
     * A week's or a month's distance per day, to hold against the daily limit. Only days from the
     * first measured day up to today count (as the history statistics did, ADR-029): the unmeasured
     * days before installing, or the rest of the current week, would dilute it. Null when no day counts.
     */
    fun averagePerDay(totalMm: Double?, range: DateRange, firstMeasuredDay: LocalDate?, today: LocalDate): Double? {
        if (firstMeasuredDay == null) return null
        val from = maxOf(range.from, minOf(firstMeasuredDay, today))
        val to = minOf(range.to, today)
        if (to.isBefore(from)) return null
        return (totalMm ?: 0.0) / (ChronoUnit.DAYS.between(from, to) + 1)
    }
}
