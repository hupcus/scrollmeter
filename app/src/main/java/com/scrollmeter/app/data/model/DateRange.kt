package com.scrollmeter.app.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Inclusive range of local dates; rows are keyed by ISO date strings, which sort like dates (D10). */
data class DateRange(val from: LocalDate, val to: LocalDate) {
    val fromKey: String get() = from.toString()
    val toKey: String get() = to.toString()

    companion object {
        fun day(date: LocalDate) = DateRange(date, date)

        /** Monday–Sunday (spec §19, `WeekFields.ISO`). */
        fun week(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): DateRange {
            val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
            return DateRange(start, start.plusDays(6))
        }

        /** Calendar month (spec §19). */
        fun month(date: LocalDate) = DateRange(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()))
    }
}
