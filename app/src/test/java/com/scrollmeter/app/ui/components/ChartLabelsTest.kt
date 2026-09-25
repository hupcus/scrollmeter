package com.scrollmeter.app.ui.components

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.insights.ChartScale
import com.scrollmeter.app.insights.DayBar
import com.scrollmeter.app.insights.Period
import com.scrollmeter.app.insights.PeriodKind
import com.scrollmeter.app.settings.UnitPreference
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

/** D13: axis labels read as round distances and clock times; bars as days or months. */
class ChartLabelsTest {
    private val cs = Locale.forLanguageTag("cs")

    @Test
    fun distanceAxisIsRoundWithOneUnitPerAxis() {
        assertThat(ChartLabels.distanceScale(423_000.0)).isEqualTo(ChartScale(600_000.0, 200_000.0))
        assertThat(ChartLabels.distanceScale(0.0)).isEqualTo(ChartScale(100_000.0, 25_000.0))
        val metres = ChartLabels.distanceAxis(ChartScale(600_000.0, 200_000.0), UnitPreference.AUTOMATIC, cs)
        assertThat(metres(0.0)).isEqualTo("0")
        assertThat(metres(200_000.0)).isEqualTo("200 m")
        // An axis that reaches 1 km is in km all the way down — never "750 m · 1 km".
        val km = ChartLabels.distanceAxis(ChartScale(1_000_000.0, 250_000.0), UnitPreference.AUTOMATIC, cs)
        assertThat(km(250_000.0)).isEqualTo("0,25 km")
        assertThat(km(1_000_000.0)).isEqualTo("1 km")
        assertThat(ChartLabels.distanceAxis(ChartScale(100_000.0, 25_000.0), UnitPreference.KILOMETRES, cs)(25_000.0)).isEqualTo("0,025 km")
        assertThat(ChartLabels.distanceAxis(ChartScale(3_000_000.0, 1_000_000.0), UnitPreference.METRES, cs)(2_000_000.0)).isEqualTo("2\u00a0000 m")
    }

    @Test
    fun timeAxisRunsInMinutes() {
        assertThat(ChartLabels.minutesScale(47 * 60_000.0)).isEqualTo(ChartScale(60.0, 15.0))
        assertThat(ChartLabels.minutesAxis(0.0)).isEqualTo("0")
        assertThat(ChartLabels.minutesAxis(15.0)).isEqualTo("15 min")
        assertThat(ChartLabels.minutesAxis(120.0)).isEqualTo("2 h")
    }

    @Test
    fun barsAreWeekdaysInAWeekAndDaysInAMonth() {
        val labels = ChartLabels(cs)
        val bar = DayBar(LocalDate.parse("2026-09-24"), 1.0)
        assertThat(labels.bar(bar, 7)).isEqualTo("čt")
        assertThat(labels.bar(bar, 30)).isEqualTo("24.")
        assertThat(labels.day(LocalDate.parse("2026-01-05"))).isEqualTo("5. 1.")
    }

    @Test
    fun periodDates() {
        val labels = ChartLabels(cs)
        fun p(kind: PeriodKind, date: String) = labels.dates(Period(kind, LocalDate.parse(date)))
        assertThat(p(PeriodKind.DAY, "2026-09-25")).isEqualTo("pá 25. 9.")
        assertThat(p(PeriodKind.WEEK, "2026-09-24")).isEqualTo("21.–27. 9.")
        assertThat(p(PeriodKind.WEEK, "2026-10-01")).isEqualTo("28. 9. – 4. 10.")
        assertThat(p(PeriodKind.WEEK, "2026-01-01")).isEqualTo("29. 12. 2025 – 4. 1. 2026")
        assertThat(p(PeriodKind.MONTH, "2026-09-10")).isEqualTo("září 2026")
        assertThat(ChartLabels(Locale.ENGLISH).dates(Period(PeriodKind.MONTH, LocalDate.parse("2026-09-10")))).isEqualTo("September 2026")
    }

    @Test
    fun englishDatesAreWrittenTheEnglishWay() {
        val labels = ChartLabels(Locale.ENGLISH)
        fun p(kind: PeriodKind, date: String) = labels.dates(Period(kind, LocalDate.parse(date)))
        assertThat(p(PeriodKind.DAY, "2026-09-25")).isEqualTo("Fri 25 Sep")
        assertThat(p(PeriodKind.WEEK, "2026-09-24")).isEqualTo("21–27 Sep")
        assertThat(p(PeriodKind.WEEK, "2026-10-01")).isEqualTo("28 Sep – 4 Oct")
        assertThat(p(PeriodKind.WEEK, "2026-01-01")).isEqualTo("29 Dec 2025 – 4 Jan 2026")
        assertThat(labels.bar(DayBar(LocalDate.parse("2026-09-24"), 1.0), 30)).isEqualTo("24")
        assertThat(labels.day(LocalDate.parse("2026-01-05"))).isEqualTo("5 Jan")
    }
}
