package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.data.model.DaySummary
import org.junit.Test

/** D19: pace and scroll share only over days that have both sides. */
class AppPeriodTotalsTest {
    @Test
    fun paceSidesArePairedOverDaysWithTimeInApp() {
        val days = listOf(
            DaySummary("2026-09-01", 100_000.0, 60_000L, null), // before Usage access: no time in app
            DaySummary("2026-09-20", 20_000.0, 30_000L, 600_000L),
            DaySummary("2026-09-21", null, null, 300_000L), // time only
        )
        val totals = AppPeriodTotals.of(days)
        assertThat(totals.distanceMm).isEqualTo(120_000.0)
        assertThat(totals.activeScrollMs).isEqualTo(90_000L)
        assertThat(totals.foregroundMs).isEqualTo(900_000L)
        assertThat(totals.pairedDistanceMm).isEqualTo(20_000.0)
        assertThat(totals.pairedScrollMs).isEqualTo(30_000L)
    }

    @Test
    fun noDataOfAKindIsNull() {
        val totals = AppPeriodTotals.of(listOf(DaySummary("2026-09-20", null, null, 300_000L)))
        assertThat(totals.distanceMm).isNull()
        assertThat(totals.activeScrollMs).isNull()
        assertThat(AppPeriodTotals.of(emptyList()).foregroundMs).isNull()
    }
}
