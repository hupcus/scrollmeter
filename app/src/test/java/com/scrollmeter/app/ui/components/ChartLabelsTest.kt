package com.scrollmeter.app.ui.components

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.insights.ChartScale
import com.scrollmeter.app.insights.HistoryBar
import com.scrollmeter.app.insights.HistoryPeriod
import com.scrollmeter.app.settings.UnitPreference
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

/** D13: axis labels read as round distances and clock times; bars as days or months. */
class ChartLabelsTest {
    private val cs = Locale.forLanguageTag("cs")

    @Test
    fun distanceAxisIsRoundInMetresAndKilometres() {
        assertThat(ChartLabels.distanceScale(423_000.0)).isEqualTo(ChartScale(600_000.0, 200_000.0))
        assertThat(ChartLabels.distanceScale(0.0)).isEqualTo(ChartScale(100_000.0, 25_000.0))
        assertThat(ChartLabels.distanceAxis(0.0, UnitPreference.AUTOMATIC, cs)).isEqualTo("0")
        assertThat(ChartLabels.distanceAxis(200_000.0, UnitPreference.AUTOMATIC, cs)).isEqualTo("200 m")
        assertThat(ChartLabels.distanceAxis(2_000_000.0, UnitPreference.AUTOMATIC, cs)).isEqualTo("2 km")
        assertThat(ChartLabels.distanceAxis(2_500_000.0, UnitPreference.AUTOMATIC, cs)).isEqualTo("2,5 km")
        assertThat(ChartLabels.distanceAxis(25_000.0, UnitPreference.KILOMETRES, cs)).isEqualTo("0,025 km")
        assertThat(ChartLabels.distanceAxis(2_000_000.0, UnitPreference.METRES, cs)).isEqualTo("2\u00a0000 m")
    }

    @Test
    fun timeAxisRunsInMinutes() {
        assertThat(ChartLabels.minutesScale(47 * 60_000.0)).isEqualTo(ChartScale(60.0, 15.0))
        assertThat(ChartLabels.minutesAxis(0.0)).isEqualTo("0")
        assertThat(ChartLabels.minutesAxis(15.0)).isEqualTo("15 min")
        assertThat(ChartLabels.minutesAxis(120.0)).isEqualTo("2 h")
    }

    @Test
    fun barsAreDaysOrMonths() {
        val labels = ChartLabels(cs)
        val bar = HistoryBar(LocalDate.parse("2026-09-24"), 1.0)
        assertThat(labels.bar(HistoryPeriod.DAYS_30, bar)).isEqualTo("24.")
        assertThat(labels.selected(HistoryPeriod.DAYS_7, bar)).isEqualTo("24. 9.")
        assertThat(labels.selected(HistoryPeriod.MONTHS_12, HistoryBar(LocalDate.parse("2026-09-01"), 1.0))).contains("2026")
        assertThat(labels.day(LocalDate.parse("2026-01-05"))).isEqualTo("5. 1.")
    }
}
