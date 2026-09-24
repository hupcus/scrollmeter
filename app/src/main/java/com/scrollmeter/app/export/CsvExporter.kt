package com.scrollmeter.app.export

import com.scrollmeter.app.data.model.ExportAppDay
import com.scrollmeter.app.data.model.ExportDay
import java.util.Locale

/**
 * The two CSV files of spec §28 (D15, ADR-031). Pure Kotlin.
 *
 * - `per_app.csv`: spec §28's columns plus `foreground_ms` and `active_scroll_ms` (ADR-021); an empty
 *   field means "unknown", never 0. `event_count` is every scroll event of that app and day
 *   (measured + fallback + unmeasurable + outlier), so the three counts after it are parts of it.
 * - `daily_summary.csv`: `date,total_distance_m,events`.
 *
 * RFC 4180: comma separator, CRLF line ends, fields with a comma, quote or line break quoted. Numbers
 * use a dot whatever the phone's language. The file starts with a UTF-8 byte-order mark so Excel
 * reads Czech app names correctly. App names come from other apps (their labels), so one that
 * starts like a spreadsheet formula (`=`, `+`, `-`, `@`, tab, CR) gets a leading apostrophe.
 */
object CsvExporter {
    const val PER_APP_FILE = "per_app.csv"
    const val DAILY_FILE = "daily_summary.csv"
    /** U+FEFF, built from its code point so the source file itself carries no byte-order mark. */
    val BOM: String = Char(0xFEFF).toString()

    val PER_APP_HEADER = listOf(
        "date", "package_name", "app_name", "distance_mm", "distance_m", "event_count", "fallback_count",
        "unmeasurable_count", "outlier_count", "foreground_ms", "active_scroll_ms",
    )
    val DAILY_HEADER = listOf("date", "total_distance_m", "events")

    fun perApp(rows: List<ExportAppDay>, appName: (String) -> String): String = csv(PER_APP_HEADER, rows.map { r ->
        val counts = listOfNotNull(r.measuredEventCount, r.fallbackEventCount, r.unmeasurableEventCount, r.rejectedOutlierCount)
        listOf(
            r.date,
            r.packageName,
            appName(r.packageName),
            r.distanceMm?.let { Math.round(it).toString() }.orEmpty(),
            r.distanceMm?.let(::metres).orEmpty(),
            if (counts.isEmpty()) "" else counts.sum().toString(),
            r.fallbackEventCount?.toString().orEmpty(),
            r.unmeasurableEventCount?.toString().orEmpty(),
            r.rejectedOutlierCount?.toString().orEmpty(),
            r.foregroundMs?.toString().orEmpty(),
            r.activeScrollMs?.toString().orEmpty(),
        )
    })

    fun daily(rows: List<ExportDay>): String = csv(DAILY_HEADER, rows.map { r ->
        listOf(r.date, r.distanceMm?.let(::metres).orEmpty(), r.events?.toString().orEmpty())
    })

    private fun metres(mm: Double) = String.format(Locale.ROOT, "%.2f", mm / 1_000.0)

    private fun csv(header: List<String>, rows: List<List<String>>): String = buildString {
        append(BOM)
        (listOf(header) + rows).forEach { row ->
            append(row.joinToString(",", transform = ::field))
            append("\r\n")
        }
    }

    internal fun field(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_START) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private val FORMULA_START = charArrayOf('=', '+', '-', '@', '\t', '\r')
}
