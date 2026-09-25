package com.scrollmeter.app.insights

import com.scrollmeter.app.data.model.DateRange
import java.time.LocalDate

/** Den / Týden / Měsíc (ADR-036): calendar periods only — a week runs Monday–Sunday (spec §19). */
enum class PeriodKind { DAY, WEEK, MONTH }

/** How a period relates to today, for its name: "Dnes", "Minulý týden", or just its dates. */
enum class PeriodRelation { CURRENT, PREVIOUS, OTHER }

/**
 * One calendar period, identified by any day inside it ([anchor]); switching Den / Týden / Měsíc
 * keeps the anchor, so a day opened from a week's bar switches back to that week. Routes and saved
 * state carry it as [key], e.g. "WEEK:2026-09-21". Pure Kotlin.
 */
data class Period(val kind: PeriodKind, val anchor: LocalDate) {
    val range: DateRange
        get() = when (kind) {
            PeriodKind.DAY -> DateRange.day(anchor)
            PeriodKind.WEEK -> DateRange.week(anchor)
            PeriodKind.MONTH -> DateRange.month(anchor)
        }

    val key: String get() = "${kind.name}:$anchor"

    fun previous(): Period = shift(-1)

    fun next(): Period = shift(1)

    fun withKind(kind: PeriodKind): Period = copy(kind = kind)

    /** Forward only while the next period has begun — never into the future. */
    fun canGoForward(today: LocalDate): Boolean = range.to.isBefore(today)

    /** Back only while the previous period reaches the first measured day; with nothing measured, never. */
    fun canGoBack(firstMeasuredDay: LocalDate?): Boolean =
        firstMeasuredDay != null && !previous().range.to.isBefore(firstMeasuredDay)

    fun relation(today: LocalDate): PeriodRelation = when {
        today in this -> PeriodRelation.CURRENT
        today in next() -> PeriodRelation.PREVIOUS
        else -> PeriodRelation.OTHER
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(range.from) && !date.isAfter(range.to)

    private fun shift(by: Long): Period = copy(
        anchor = when (kind) {
            PeriodKind.DAY -> anchor.plusDays(by)
            PeriodKind.WEEK -> anchor.plusWeeks(by)
            PeriodKind.MONTH -> anchor.plusMonths(by)
        },
    )

    companion object {
        fun today(kind: PeriodKind, today: LocalDate) = Period(kind, today)

        /** From route arguments; null for anything that is not a kind and an ISO date. */
        fun parse(kind: String, anchor: String): Period? {
            val k = PeriodKind.entries.firstOrNull { it.name == kind } ?: return null
            val date = runCatching { LocalDate.parse(anchor) }.getOrNull() ?: return null
            return Period(k, date)
        }

        fun fromKey(key: String): Period? = key.split(':', limit = 2).takeIf { it.size == 2 }?.let { parse(it[0], it[1]) }
    }
}
