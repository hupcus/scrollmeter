package com.scrollmeter.app.format

import com.scrollmeter.app.settings.Settings
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

/**
 * A custom daily limit typed by the user (spec §25, ADR-036): a number in metres or kilometres, with
 * the locale's decimal separator or a dot. Null when it is not a number or falls outside
 * [Settings.LIMIT_RANGE_MM] — never silently clamped to something the user did not type. Pure Kotlin.
 */
object LimitInput {
    fun parseMm(text: String, kilometres: Boolean, locale: Locale): Double? {
        val trimmed = text.trim().replace(' ', ' ').replace(" ", "")
        if (trimmed.isEmpty()) return null
        val value = parse(trimmed, locale) ?: parse(trimmed.replace(',', '.'), Locale.ROOT) ?: return null
        val mm = value * if (kilometres) 1_000_000.0 else 1_000.0
        return mm.takeIf { it.isFinite() && it in Settings.LIMIT_RANGE_MM }
    }

    /** The whole text must be the number: "5 km" or "1,2,3" is not a limit. */
    private fun parse(text: String, locale: Locale): Double? {
        val position = ParsePosition(0)
        val number = NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false }.parse(text, position)
        return if (number != null && position.index == text.length) number.toDouble() else null
    }
}
