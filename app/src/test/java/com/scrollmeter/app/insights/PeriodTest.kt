package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** ADR-036: calendar day / week (Mon–Sun) / month, ‹ › bounded by the first measured day and today. */
class PeriodTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun rangesAreCalendarPeriods() {
        assertThat(Period(PeriodKind.WEEK, d("2026-09-24")).range.let { it.fromKey to it.toKey }).isEqualTo("2026-09-21" to "2026-09-27")
        assertThat(Period(PeriodKind.MONTH, d("2028-02-10")).range.let { it.fromKey to it.toKey }).isEqualTo("2028-02-01" to "2028-02-29")
        assertThat(Period(PeriodKind.DAY, d("2026-09-24")).range.let { it.fromKey to it.toKey }).isEqualTo("2026-09-24" to "2026-09-24")
    }

    @Test
    fun steppingCrossesMonthAndYearEnds() {
        assertThat(Period(PeriodKind.WEEK, d("2026-01-01")).previous().range.fromKey).isEqualTo("2025-12-22")
        assertThat(Period(PeriodKind.MONTH, d("2026-01-15")).previous().range.fromKey).isEqualTo("2025-12-01")
        assertThat(Period(PeriodKind.MONTH, d("2026-03-31")).previous().range.fromKey).isEqualTo("2026-02-01")
        assertThat(Period(PeriodKind.DAY, d("2026-12-31")).next().anchor).isEqualTo(d("2027-01-01"))
    }

    @Test
    fun forwardStopsAtTheCurrentPeriod() {
        val today = d("2026-09-25")
        assertThat(Period(PeriodKind.WEEK, today).canGoForward(today)).isFalse()
        assertThat(Period(PeriodKind.WEEK, d("2026-09-18")).canGoForward(today)).isTrue()
        assertThat(Period(PeriodKind.DAY, d("2026-09-24")).canGoForward(today)).isTrue()
        assertThat(Period(PeriodKind.MONTH, d("2026-09-01")).canGoForward(today)).isFalse()
    }

    @Test
    fun backStopsAtThePeriodOfTheFirstMeasuredDay() {
        val first = d("2026-09-23")
        assertThat(Period(PeriodKind.WEEK, d("2026-09-25")).canGoBack(first)).isFalse()
        assertThat(Period(PeriodKind.DAY, d("2026-09-24")).canGoBack(first)).isTrue()
        assertThat(Period(PeriodKind.DAY, d("2026-09-23")).canGoBack(first)).isFalse()
        assertThat(Period(PeriodKind.WEEK, d("2026-09-30")).canGoBack(first)).isTrue()
        assertThat(Period(PeriodKind.DAY, d("2026-09-25")).canGoBack(null)).isFalse()
    }

    @Test
    fun switchingTheKindKeepsTheDay() {
        val day = Period(PeriodKind.DAY, d("2026-09-17"))
        assertThat(day.withKind(PeriodKind.WEEK).range.fromKey).isEqualTo("2026-09-14")
        assertThat(day.withKind(PeriodKind.MONTH).range.fromKey).isEqualTo("2026-09-01")
    }

    @Test
    fun relationToToday() {
        val today = d("2026-09-25")
        assertThat(Period(PeriodKind.DAY, today).relation(today)).isEqualTo(PeriodRelation.CURRENT)
        assertThat(Period(PeriodKind.DAY, d("2026-09-24")).relation(today)).isEqualTo(PeriodRelation.PREVIOUS)
        assertThat(Period(PeriodKind.WEEK, d("2026-09-15")).relation(today)).isEqualTo(PeriodRelation.PREVIOUS)
        assertThat(Period(PeriodKind.MONTH, d("2026-07-15")).relation(today)).isEqualTo(PeriodRelation.OTHER)
    }

    @Test
    fun routeArgumentsAndKeysRoundTrip() {
        val week = Period(PeriodKind.WEEK, d("2026-09-21"))
        assertThat(week.key).isEqualTo("WEEK:2026-09-21")
        assertThat(Period.fromKey(week.key)).isEqualTo(week)
        assertThat(Period.parse("MONTH", "2026-09-01")).isEqualTo(Period(PeriodKind.MONTH, d("2026-09-01")))
        assertThat(Period.parse("YEAR", "2026-09-01")).isNull()
        assertThat(Period.parse("DAY", "bad")).isNull()
        assertThat(Period.fromKey("DAY")).isNull()
    }
}
