package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.data.model.DaySummary
import java.time.LocalDate
import org.junit.Test

class DaySeriesTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun day(date: String, mm: Double?, fg: Long? = null) = DaySummary(date, mm, null, fg)

    @Test
    fun aCalendarWeekHasSevenBarsMissingDaysZero() {
        val bars = DaySeries.bars(DateRange.week(d("2026-09-24")), listOf(day("2026-09-22", 100.0), day("2026-09-27", 5.0)))
        assertThat(bars.map { it.date.toString() }).containsExactly(
            "2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24", "2026-09-25", "2026-09-26", "2026-09-27",
        ).inOrder()
        assertThat(bars.map { it.value }).containsExactly(0.0, 100.0, 0.0, 0.0, 0.0, 0.0, 5.0).inOrder()
    }

    @Test
    fun monthsHaveTheirOwnLength() {
        assertThat(DaySeries.bars(DateRange.month(d("2026-02-10")), emptyList())).hasSize(28)
        assertThat(DaySeries.bars(DateRange.month(d("2028-02-10")), emptyList())).hasSize(29)
        assertThat(DaySeries.bars(DateRange.month(d("2026-09-10")), emptyList())).hasSize(30)
        assertThat(DaySeries.bars(DateRange.month(d("2026-10-10")), emptyList())).hasSize(31)
    }

    @Test
    fun daysOutsideTheRangeAndUnreadableDatesAreIgnored() {
        val bars = DaySeries.bars(DateRange.day(d("2026-09-24")), listOf(day("2026-09-23", 9.0), day("x", 9.0), day("2026-09-24", 3.0)))
        assertThat(bars).containsExactly(DayBar(d("2026-09-24"), 3.0))
    }

    @Test
    fun anotherColumnWithNullAsZero() {
        val bars = DaySeries.bars(DateRange(d("2026-09-23"), d("2026-09-24")), listOf(day("2026-09-23", 1.0, fg = 60_000), day("2026-09-24", 2.0))) {
            it.foregroundMs?.toDouble()
        }
        assertThat(bars.map { it.value }).containsExactly(60_000.0, 0.0).inOrder()
    }
}
