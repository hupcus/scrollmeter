package com.scrollmeter.app.insights

import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.data.model.DaySummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Spec §23: the three history views. */
enum class HistoryPeriod { DAYS_7, DAYS_30, MONTHS_12 }

/** One bar: a day, or a month starting at [start]; [value] in the unit of the series (mm, ms). */
data class HistoryBar(val start: LocalDate, val value: Double)

data class DayValue(val date: LocalDate, val value: Double)

/**
 * Průměr / den, Nejvyšší den, Nejnižší den, Celkem (spec §23) — always per **day**, also in the
 * 12-month view. Only days since the first measured day count: before that nothing was measured,
 * and averaging those days in would understate everything for a new user.
 */
data class HistoryStats(val averagePerDay: Double, val maxDay: DayValue, val minDay: DayValue, val total: Double)

data class HistorySeries(val period: HistoryPeriod, val range: DateRange, val bars: List<HistoryBar>, val stats: HistoryStats?)

/**
 * Pure Kotlin: stored day summaries → bars (missing days are 0) and daily statistics. The series is
 * distance by default; [build]'s `value` picks another column (time in app, scroll time), null = 0.
 */
object HistorySeriesBuilder {
    fun range(period: HistoryPeriod, today: LocalDate): DateRange = when (period) {
        HistoryPeriod.DAYS_7 -> DateRange(today.minusDays(6), today)
        HistoryPeriod.DAYS_30 -> DateRange(today.minusDays(29), today)
        HistoryPeriod.MONTHS_12 -> DateRange(today.withDayOfMonth(1).minusMonths(11), today)
    }

    /** [firstMeasuredDay] = the earliest stored day of all time, or null when nothing was ever measured. */
    fun build(
        period: HistoryPeriod,
        today: LocalDate,
        days: List<DaySummary>,
        firstMeasuredDay: LocalDate?,
        value: (DaySummary) -> Double? = { it.distanceMm },
    ): HistorySeries {
        val range = range(period, today)
        val byDate = HashMap<LocalDate, Double>()
        days.forEach { d ->
            val date = runCatching { LocalDate.parse(d.date) }.getOrNull() ?: return@forEach
            if (!date.isBefore(range.from) && !date.isAfter(range.to)) byDate.merge(date, value(d) ?: 0.0, Double::plus)
        }
        val allDays = generateSequence(range.from) { it.plusDays(1) }.takeWhile { !it.isAfter(range.to) }.toList()
        val bars = when (period) {
            HistoryPeriod.MONTHS_12 -> allDays.groupBy { it.withDayOfMonth(1) }
                .map { (month, ds) -> HistoryBar(month, ds.sumOf { byDate[it] ?: 0.0 }) }
            else -> allDays.map { HistoryBar(it, byDate[it] ?: 0.0) }
        }
        return HistorySeries(period, range, bars, stats(allDays, byDate, firstMeasuredDay, today))
    }

    private fun stats(allDays: List<LocalDate>, byDate: Map<LocalDate, Double>, first: LocalDate?, today: LocalDate): HistoryStats? {
        if (first == null) return null
        // A first day "in the future" (the clock was moved back) still means something was measured.
        val from = minOf(first, today)
        val counted = allDays.filter { !it.isBefore(from) }.map { DayValue(it, byDate[it] ?: 0.0) }
        if (counted.isEmpty()) return null
        val total = counted.sumOf { it.value }
        return HistoryStats(
            averagePerDay = total / counted.size,
            // Ties go to the most recent day — the one the user remembers.
            maxDay = counted.maxWith(compareBy<DayValue> { it.value }.thenBy { it.date }),
            minDay = counted.minWith(compareBy<DayValue> { it.value }.thenByDescending { it.date }),
            total = total,
        )
    }

    /** Days between two dates inclusive — for labels such as "za 12 dní". */
    fun daysInclusive(range: DateRange): Long = ChronoUnit.DAYS.between(range.from, range.to) + 1
}
