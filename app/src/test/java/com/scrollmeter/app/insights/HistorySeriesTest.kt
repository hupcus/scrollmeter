package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.data.model.DaySummary
import java.time.LocalDate
import org.junit.Test

/** Spec §23: 7 / 30 days and 12 months, statistics per day since the first measured day. */
class HistorySeriesTest {
    private val today = LocalDate.parse("2026-09-24")
    private fun day(date: String, mm: Double?) = DaySummary(date, mm, null, null)
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun sevenDaysFillMissingDaysWithZero() {
        val series = HistorySeriesBuilder.build(HistoryPeriod.DAYS_7, today, listOf(day("2026-09-20", 100.0), day("2026-09-24", 50.0)), d("2026-09-01"))
        assertThat(series.bars.map { it.start }).containsExactlyElementsIn((18..24).map { d("2026-09-%02d".format(it)) }).inOrder()
        assertThat(series.bars.map { it.value }).containsExactly(0.0, 0.0, 100.0, 0.0, 0.0, 0.0, 50.0).inOrder()
        val stats = series.stats!!
        assertThat(stats.total).isEqualTo(150.0)
        assertThat(stats.averagePerDay).isWithin(1e-9).of(150.0 / 7)
        assertThat(stats.maxDay).isEqualTo(DayValue(d("2026-09-20"), 100.0))
        assertThat(stats.minDay).isEqualTo(DayValue(d("2026-09-23"), 0.0)) // ties: the most recent zero day
    }

    @Test
    fun statisticsStartAtTheFirstMeasuredDay() {
        val series = HistorySeriesBuilder.build(HistoryPeriod.DAYS_30, today, listOf(day("2026-09-23", 30.0), day("2026-09-24", 10.0)), d("2026-09-23"))
        assertThat(series.bars).hasSize(30)
        val stats = series.stats!!
        assertThat(stats.averagePerDay).isEqualTo(20.0)
        assertThat(stats.minDay).isEqualTo(DayValue(d("2026-09-24"), 10.0))
    }

    @Test
    fun twelveMonthsSumPerMonthButStatisticsStayPerDay() {
        val days = listOf(day("2025-10-01", 1.0), day("2025-10-31", 2.0), day("2026-02-28", 4.0), day("2026-09-24", 8.0), day("2025-09-30", 99.0))
        val series = HistorySeriesBuilder.build(HistoryPeriod.MONTHS_12, today, days, d("2025-06-01"))
        assertThat(series.range.from).isEqualTo(d("2025-10-01"))
        assertThat(series.bars).hasSize(12)
        assertThat(series.bars.first()).isEqualTo(HistoryBar(d("2025-10-01"), 3.0))
        assertThat(series.bars.map { it.value }.sum()).isEqualTo(15.0) // the September 2025 row is outside
        assertThat(series.bars.last()).isEqualTo(HistoryBar(d("2026-09-01"), 8.0))
        val stats = series.stats!!
        assertThat(stats.maxDay.date).isEqualTo(d("2026-09-24"))
        assertThat(stats.total).isEqualTo(15.0)
    }

    @Test
    fun nothingMeasuredHasNoStatistics() {
        val series = HistorySeriesBuilder.build(HistoryPeriod.DAYS_7, today, emptyList(), null)
        assertThat(series.stats).isNull()
        assertThat(series.bars.all { it.value == 0.0 }).isTrue()
    }

    @Test
    fun anotherColumnCanBeCharted() {
        val days = listOf(DaySummary("2026-09-24", 10.0, 1_000L, 60_000L), DaySummary("2026-09-23", null, null, 30_000L))
        val series = HistorySeriesBuilder.build(HistoryPeriod.DAYS_7, today, days, d("2026-09-23")) { it.foregroundMs?.toDouble() }
        assertThat(series.bars.takeLast(2).map { it.value }).containsExactly(30_000.0, 60_000.0).inOrder()
        assertThat(series.stats!!.total).isEqualTo(90_000.0)
    }
}
