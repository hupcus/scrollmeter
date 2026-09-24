package com.scrollmeter.app.ui.components

import com.scrollmeter.app.format.TimeFormatter
import com.scrollmeter.app.insights.ChartScale
import com.scrollmeter.app.insights.HistoryBar
import com.scrollmeter.app.insights.HistoryPeriod
import com.scrollmeter.app.settings.UnitPreference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Axis and bar labels for the history charts: dates in the app's locale, 0 without a unit. */
class ChartLabels(locale: Locale) {
    private val weekday = DateTimeFormatter.ofPattern("EEE", locale)
    private val dayOfMonth = DateTimeFormatter.ofPattern("d.", locale)
    private val monthShort = DateTimeFormatter.ofPattern("LLL", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d. M.", locale)
    private val month = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    /** Under a bar: weekday for 7 days, day of month for 30, month for 12 months. */
    fun bar(period: HistoryPeriod, bar: HistoryBar): String = when (period) {
        HistoryPeriod.DAYS_7 -> weekday.format(bar.start)
        HistoryPeriod.DAYS_30 -> dayOfMonth.format(bar.start)
        HistoryPeriod.MONTHS_12 -> monthShort.format(bar.start)
    }

    /** Above the chart for a selected bar: "24. 9." or "září 2026". */
    fun selected(period: HistoryPeriod, bar: HistoryBar): String =
        if (period == HistoryPeriod.MONTHS_12) month.format(bar.start) else dayMonth.format(bar.start)

    /** A day in a statistic: "24. 9." */
    fun day(date: LocalDate): String = dayMonth.format(date)

    companion object {
        /** A distance axis in millimetres; [ChartScale.of]'s round steps are round in m and km too. */
        fun distanceScale(maxMm: Double): ChartScale = ChartScale.of(maxMm, emptyMax = 100_000.0)

        /**
         * Grid values are round, so without the fixed decimals of `DistanceFormatter`: "250 m", "2 km",
         * "2,5 km". The unit follows the preference; AUTOMATIC switches to km at 1 km.
         */
        fun distanceAxis(mm: Double, unit: UnitPreference, locale: Locale): String {
            if (mm == 0.0) return "0"
            val metres = mm / 1_000.0
            val km = unit == UnitPreference.KILOMETRES || (unit == UnitPreference.AUTOMATIC && metres >= 1_000)
            val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }
            return if (km) "${number.format(metres / 1_000.0)} km" else "${number.format(metres)} m"
        }

        /** Time axes run in minutes so the steps read as clock time. */
        fun minutesScale(maxMs: Double): ChartScale = ChartScale.ofMinutes(maxMs / 60_000.0, emptyMax = 10.0)

        fun minutesAxis(minutes: Double): String = if (minutes == 0.0) "0" else TimeFormatter.duration(Math.round(minutes * 60_000.0))
    }
}
