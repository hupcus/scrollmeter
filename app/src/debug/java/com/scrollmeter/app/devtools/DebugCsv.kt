package com.scrollmeter.app.devtools

import com.scrollmeter.app.calibration.DisplaySnapshot
import com.scrollmeter.app.measurement.MeasurementResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Debug CSV (spec §34 plus the §70 dedupe fields). A `#` header line carries the device
 * geometry and scale, so `tools/analyze_debug_csv.py` can check the outlier limit on its own.
 */
object DebugCsv {
    const val COLUMNS = "timestamp,uptime_ms,package,window_id,class_name,dx_px,dy_px," +
        "scroll_x,scroll_y,max_scroll_x,max_scroll_y,used_dx_px,used_dy_px,distance_mm,source,status"

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT)

    fun build(
        results: List<MeasurementResult>,
        display: DisplaySnapshot,
        device: String,
        appVersion: String,
        overflowed: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        val scale = display.toDisplayScale()
        appendLine(
            String.format(
                Locale.ROOT,
                "# scrollmeter-debug app=%s device=%s width_px=%d height_px=%d xdpi=%.3f ydpi=%.3f " +
                    "density_dpi=%d mm_per_px_x=%.6f mm_per_px_y=%.6f scale_method=%s " +
                    "diagonal_px=%.1f max_event_px=%.1f rows=%d overflowed=%d",
                appVersion, device.replace(' ', '_'), display.widthPx, display.heightPx, display.xdpi,
                display.ydpi, display.densityDpi, scale.scale.mmPerPxX, scale.scale.mmPerPxY,
                scale.scale.method.name, scale.geometry.diagonalPx, scale.geometry.maxEventDistancePx,
                results.size, overflowed,
            ),
        )
        appendLine(COLUMNS)
        for (r in results) {
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
            append(if (r.accepted) "accepted" else "rejected").append('\n')
        }
    }

    /** Package and class names never contain commas or quotes, but a CSV must not trust that. */
    private fun field(value: String?): String {
        val v = value.orEmpty()
        return if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    /** The package with the most events — names the export file after the app just tested. */
    fun dominantPackage(results: List<MeasurementResult>, ownPackage: String): String =
        results.mapNotNull { it.sample.packageName }
            .filter { it != ownPackage }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
            ?: "none"
}
