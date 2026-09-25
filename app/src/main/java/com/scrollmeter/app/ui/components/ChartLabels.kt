package com.scrollmeter.app.ui.components

import com.scrollmeter.app.format.TimeFormatter
import com.scrollmeter.app.insights.ChartScale
import com.scrollmeter.app.insights.DayBar
import com.scrollmeter.app.insights.Period
import com.scrollmeter.app.insights.PeriodKind
import com.scrollmeter.app.settings.UnitPreference
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Axis, bar and period labels: dates in the app's locale and its own way — "25. 9." in Czech, "25 Sep" in English — 0 without a unit. */
class ChartLabels(locale: Locale) {
    private val czech = locale.language == "cs"
    private val weekday = DateTimeFormatter.ofPattern("EEE", locale)
    private val weekdayDayMonth = DateTimeFormatter.ofPattern(if (czech) "EEE d. M." else "EEE d MMM", locale)
    private val dayOfMonth = DateTimeFormatter.ofPattern(if (czech) "d." else "d", locale)
    private val dayMonth = DateTimeFormatter.ofPattern(if (czech) "d. M." else "d MMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern(if (czech) "d. M. yyyy" else "d MMM yyyy", locale)
    private val month = DateTimeFormatter.ofPattern("LLLL yyyy", locale)

    /** Under a bar: the weekday in a week's chart (up to 7 bars), the day of the month otherwise. */
    fun bar(bar: DayBar, barCount: Int): String = if (barCount <= 7) weekday.format(bar.date) else dayOfMonth.format(bar.date)

    /** A day in a statistic: "24. 9." / "24 Sep" */
    fun day(date: LocalDate): String = dayMonth.format(date)

    /**
     * A period's dates (ADR-036): "pá 25. 9.", "21.–27. 9.", "28. 9. – 4. 10.", "září 2026"; in English
     * "Fri 25 Sep", "21–27 Sep", "28 Sep – 4 Oct", "September 2026". A week across a new year carries the years.
     */
    fun dates(period: Period): String {
        val range = period.range
        return when (period.kind) {
            PeriodKind.DAY -> weekdayDayMonth.format(range.from)
            PeriodKind.MONTH -> month.format(range.from)
            PeriodKind.WEEK -> when {
                range.from.month == range.to.month -> "${dayOfMonth.format(range.from)}–${dayMonth.format(range.to)}"
                range.from.year == range.to.year -> "${dayMonth.format(range.from)} – ${dayMonth.format(range.to)}"
                else -> "${dayMonthYear.format(range.from)} – ${dayMonthYear.format(range.to)}"
            }
        }
    }

    companion object {
        /** A distance axis in millimetres; [ChartScale.of]'s round steps are round in m and km too. */
        fun distanceScale(maxMm: Double): ChartScale = ChartScale.of(maxMm, emptyMax = 100_000.0)

        /**
         * Labels for a distance axis. Grid values are round, so without the fixed decimals of
         * `DistanceFormatter`: "250 m", "2 km", "2,5 km". One unit per axis — AUTOMATIC picks km when
         * the axis reaches 1 km, so it never reads "750 m · 1 km".
         */
        fun distanceAxis(scale: ChartScale, unit: UnitPreference, locale: Locale): (Double) -> String {
            val km = unit == UnitPreference.KILOMETRES || (unit == UnitPreference.AUTOMATIC && scale.max >= 1_000_000.0)
            val number = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 }
            return { mm ->
                when {
                    mm == 0.0 -> "0"
                    km -> "${number.format(mm / 1_000_000.0)} km"
                    else -> "${number.format(mm / 1_000.0)} m"
                }
            }
        }

        /** Time axes run in minutes so the steps read as clock time. */
        fun minutesScale(maxMs: Double): ChartScale = ChartScale.ofMinutes(maxMs / 60_000.0, emptyMax = 10.0)

        fun minutesAxis(minutes: Double): String = if (minutes == 0.0) "0" else TimeFormatter.duration(Math.round(minutes * 60_000.0))
    }
}
