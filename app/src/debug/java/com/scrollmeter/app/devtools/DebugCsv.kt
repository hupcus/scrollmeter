package com.scrollmeter.app.devtools

import com.scrollmeter.app.calibration.CalibrationState
import com.scrollmeter.app.calibration.DisplaySnapshot
import com.scrollmeter.app.measurement.MeasurementResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Debug CSV (spec §34 plus the §70 dedupe fields). A `#` header line carries the device
 * geometry and the scale in force at export (calibration method and version), so
 * `tools/analyze_debug_csv.py` can check the outlier limit on its own. Each row's `distance_mm`
 * keeps the scale it was measured with (spec §65).
 */
object DebugCsv {
    const val COLUMNS = "timestamp,uptime_ms,package,window_id,class_name,dx_px,dy_px," +
        "scroll_x,scroll_y,max_scroll_x,max_scroll_y,used_dx_px,used_dy_px,distance_mm,source,status"

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT)

    fun build(
        results: List<MeasurementResult>,
        display: DisplaySnapshot,
        calibration: CalibrationState,
        device: String,
        appVersion: String,
        overflowed: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = document(results.map { row(it, zone) }, display, calibration, device, appVersion, overflowed)

    /** Header line, column names and [rows] as produced by [row] — from RAM or from the recording file. */
    fun document(
        rows: List<String>,
        display: DisplaySnapshot,
        calibration: CalibrationState,
        device: String,
        appVersion: String,
        overflowed: Long,
    ): String = buildString {
        val scale = display.toDisplayScale(calibration)
        appendLine(
            String.format(
                Locale.ROOT,
                "# scrollmeter-debug app=%s device=%s width_px=%d height_px=%d xdpi=%.3f ydpi=%.3f " +
                    "density_dpi=%d mm_per_px_x=%.6f mm_per_px_y=%.6f scale_method=%s calibration_version=%d " +
                    "diagonal_px=%.1f max_event_px=%.1f rows=%d overflowed=%d",
                appVersion, device.replace(' ', '_'), display.widthPx, display.heightPx, display.xdpi,
                display.ydpi, display.densityDpi, scale.scale.mmPerPxX, scale.scale.mmPerPxY,
                scale.scale.method.name, scale.scale.calibrationVersion, scale.geometry.diagonalPx,
                scale.geometry.maxEventDistancePx, rows.size, overflowed,
            ),
        )
        appendLine(COLUMNS)
        rows.forEach(::appendLine)
    }

    /** One event as one CSV line (no line break), in [COLUMNS] order. */
    fun row(r: MeasurementResult, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        val s = r.sample
        append(timestampFormat.format(Instant.ofEpochMilli(s.wallTimeMs).atZone(zone))).append(',')
        append(s.uptimeMs).append(',')
        append(field(s.packageName)).append(',')
        append(s.windowId).append(',')
        append(field(s.className)).append(',')
        append(s.deltaX).append(',').append(s.deltaY).append(',')
        append(s.scrollX).append(',').append(s.scrollY).append(',')
        append(s.maxScrollX).append(',').append(s.maxScrollY).append(',')
        append(r.dxPx).append(',').append(r.dyPx).append(',')
        append(String.format(Locale.ROOT, "%.4f", r.distance.totalMm)).append(',')
        append(r.source.name).append(',')
        append(if (r.accepted) "accepted" else "rejected")
    }

    /**
     * Package names are verified by the system, but `className` is whatever the other app set —
     * so quote separators and defuse spreadsheet formulas (`= + - @` at the start → leading `'`).
     */
    internal fun field(value: String?): String {
        var v = value.orEmpty()
        if (v.isNotEmpty() && v[0] in "=+-@\t\r") v = "'$v"
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == '\'' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    /** The package with the most events — names the export file after the app just tested. */
    fun dominantPackage(results: List<MeasurementResult>, ownPackage: String): String =
        dominantOf(results.mapNotNull { it.sample.packageName }, ownPackage)

    /** Same for [row] lines: the package is the third field, and neither field before it can hold a comma. */
    fun dominantPackageOfRows(rows: List<String>, ownPackage: String): String =
        dominantOf(rows.mapNotNull { it.split(',').getOrNull(2) }, ownPackage)

    private fun dominantOf(packages: List<String>, ownPackage: String): String =
        packages
            .filter { it.isNotEmpty() && it != ownPackage }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
            ?: "none"
}
