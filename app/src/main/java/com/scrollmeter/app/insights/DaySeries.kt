package com.scrollmeter.app.insights

import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.data.model.DaySummary
import java.time.LocalDate

/** One bar of a day chart; [value] in the unit of the series (mm, ms). */
data class DayBar(val date: LocalDate, val value: Double)

/**
 * Pure Kotlin: stored day summaries → one bar per day of a range, missing days 0 — a calendar week or
 * month (ADR-036) or the app detail's last 30 days. The series is distance by default; `value` picks
 * another column (time in app), null = 0.
 */
object DaySeries {
    fun bars(range: DateRange, days: List<DaySummary>, value: (DaySummary) -> Double? = { it.distanceMm }): List<DayBar> {
        val byDate = HashMap<LocalDate, Double>()
        days.forEach { d ->
            val date = runCatching { LocalDate.parse(d.date) }.getOrNull() ?: return@forEach
            if (!date.isBefore(range.from) && !date.isAfter(range.to)) byDate.merge(date, value(d) ?: 0.0, Double::plus)
        }
        return generateSequence(range.from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(range.to) }
            .map { DayBar(it, byDate[it] ?: 0.0) }
            .toList()
    }
}
