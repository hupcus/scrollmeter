package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.data.model.DateRange
import java.time.LocalDate
import org.junit.Test

/** ADR-036: green under 70 % of the limit, orange up to it, red from it; the average counts measured days only. */
class DailyLimitTest {
    private val limit = 500_000.0
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun levelsAtTheEdges() {
        assertThat(DailyLimit.level(0.0, limit)).isEqualTo(LimitLevel.UNDER)
        assertThat(DailyLimit.level(349_999.0, limit)).isEqualTo(LimitLevel.UNDER)
        assertThat(DailyLimit.level(350_000.0, limit)).isEqualTo(LimitLevel.NEAR)
        assertThat(DailyLimit.level(499_999.0, limit)).isEqualTo(LimitLevel.NEAR)
        assertThat(DailyLimit.level(500_000.0, limit)).isEqualTo(LimitLevel.OVER)
        assertThat(DailyLimit.level(null, limit)).isEqualTo(LimitLevel.UNDER)
        assertThat(DailyLimit.level(900_000.0, 0.0)).isEqualTo(LimitLevel.NONE)
    }

    @Test
    fun remainingAndOver() {
        assertThat(DailyLimit.status(428_000.0, limit)).isEqualTo(LimitStatus(LimitLevel.NEAR, limit, 72_000.0, 0.0))
        assertThat(DailyLimit.status(580_000.0, limit)).isEqualTo(LimitStatus(LimitLevel.OVER, limit, 0.0, 80_000.0))
        assertThat(DailyLimit.status(null, limit)).isEqualTo(LimitStatus(LimitLevel.UNDER, limit, limit, 0.0))
    }

    @Test
    fun averageOfTheCurrentWeekCountsDaysUpToToday() {
        val week = DateRange.week(d("2026-09-22")) // Mon 21 – Sun 27
        assertThat(DailyLimit.averagePerDay(1_000.0, week, d("2026-01-01"), d("2026-09-22"))).isEqualTo(500.0)
    }

    @Test
    fun averageOfAPastWeekCountsAllSevenDays() {
        assertThat(DailyLimit.averagePerDay(700.0, DateRange.week(d("2026-09-14")), d("2026-01-01"), d("2026-09-25"))).isEqualTo(100.0)
    }

    @Test
    fun averageStartsAtTheFirstMeasuredDay() {
        // Measuring since Wednesday, today is Friday: 3 days.
        assertThat(DailyLimit.averagePerDay(900.0, DateRange.week(d("2026-09-25")), d("2026-09-23"), d("2026-09-25"))).isEqualTo(300.0)
    }

    @Test
    fun noAverageWithoutCountedDays() {
        assertThat(DailyLimit.averagePerDay(0.0, DateRange.week(d("2026-09-25")), null, d("2026-09-25"))).isNull()
        assertThat(DailyLimit.averagePerDay(0.0, DateRange.week(d("2026-09-14")), d("2026-09-23"), d("2026-09-25"))).isNull()
        assertThat(DailyLimit.averagePerDay(null, DateRange.week(d("2026-09-25")), d("2026-09-23"), d("2026-09-25"))).isEqualTo(0.0)
    }
}
