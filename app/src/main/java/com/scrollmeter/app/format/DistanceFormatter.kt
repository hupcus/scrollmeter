package com.scrollmeter.app.format

import com.scrollmeter.app.settings.UnitPreference
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.round

/**
 * Millimetres for display (spec §1, §26 Units): "428 m", "2,84 km". The number of decimals follows
 * the size of the value, and is decided on the rounded value, so 999,6 m never shows as "1 000 m"
 * and 9,996 km never as "10,00 km". AUTOMATIC switches to km at 1 km. Pure Kotlin.
 */
object DistanceFormatter {
    fun format(mm: Double, unit: UnitPreference, locale: Locale): String {
        val metres = if (mm.isFinite() && mm > 0) mm / 1_000.0 else 0.0
        return when (unit) {
            UnitPreference.METRES -> metres(metres, locale)
            UnitPreference.KILOMETRES -> kilometres(metres / 1_000.0, locale)
            UnitPreference.AUTOMATIC -> if (round(metres) < 1_000) metres(metres, locale) else kilometres(metres / 1_000.0, locale)
        }
    }

    private fun metres(m: Double, locale: Locale): String {
        val digits = if (roundTo(m, 1) < 10) 1 else 0
        return number(m, digits, locale) + " m"
    }

    private fun kilometres(km: Double, locale: Locale): String {
        val digits = when {
            roundTo(km, 2) < 10 -> 2
            roundTo(km, 1) < 100 -> 1
            else -> 0
        }
        return number(km, digits, locale) + " km"
    }

    private fun roundTo(value: Double, digits: Int): Double {
        val factor = Math.pow(10.0, digits.toDouble())
        return round(value * factor) / factor
    }

    private fun number(value: Double, digits: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)
}
