package com.scrollmeter.app.ui.components

import java.text.NumberFormat
import java.util.Locale

/** Number formatting in the user's locale (Czech: decimal comma, space as thousands separator). */
object Format {
    fun decimal(value: Double, digits: Int, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(value)

    fun integer(value: Long, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getIntegerInstance(locale).format(value)

    /** Millimetres shown as metres with two decimals — the POC's only distance display. */
    fun metres(mm: Double, locale: Locale = Locale.getDefault()): String = decimal(mm / 1000.0, 2, locale) + " m"
}
