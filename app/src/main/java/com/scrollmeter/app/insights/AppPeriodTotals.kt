package com.scrollmeter.app.insights

import com.scrollmeter.app.data.model.DaySummary

/**
 * One app over a period, summed from its day rows (spec §24, D19). Null = no data of that kind.
 *
 * Distance and scroll time go back to the first measured day, but time in app exists only for days
 * the usage sync covered (it looks back at most 9 days when access is granted — ADR-025). Pace and
 * the scroll share therefore pair both sides over the same days: [pairedDistanceMm] and
 * [pairedScrollMs] count only days that have a time in app. Pure Kotlin.
 */
data class AppPeriodTotals(
    val distanceMm: Double?,
    val activeScrollMs: Long?,
    val foregroundMs: Long?,
    val pairedDistanceMm: Double,
    val pairedScrollMs: Long,
) {
    companion object {
        fun of(days: List<DaySummary>): AppPeriodTotals {
            val paired = days.filter { it.foregroundMs != null }
            return AppPeriodTotals(
                distanceMm = days.mapNotNull { it.distanceMm }.takeIf { it.isNotEmpty() }?.sum(),
                activeScrollMs = days.mapNotNull { it.activeScrollMs }.takeIf { it.isNotEmpty() }?.sum(),
                foregroundMs = paired.takeIf { it.isNotEmpty() }?.sumOf { it.foregroundMs ?: 0L },
                pairedDistanceMm = paired.sumOf { it.distanceMm ?: 0.0 },
                pairedScrollMs = paired.sumOf { it.activeScrollMs ?: 0L },
            )
        }
    }
}
