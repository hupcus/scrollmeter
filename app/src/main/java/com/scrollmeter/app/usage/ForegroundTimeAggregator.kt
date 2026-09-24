package com.scrollmeter.app.usage

import com.scrollmeter.app.measurement.MeasurementConfig
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns usage events into foreground time per local day and package (ADR-021, ADR-025). Pure Kotlin.
 *
 * An app is in the foreground while at least one of its activities is RESUMED. It leaves when its
 * last activity pauses or stops — unless it resumes again within [MeasurementConfig.USAGE_SAME_APP_GRACE_MS]
 * and no other app came up meanwhile (moving between its own activities pauses one before resuming
 * the next). Screen off and shutdown close every app; a startup without a shutdown discards what was
 * still open, because its end is unknown. A PAUSED without a RESUMED is ignored; what is still open
 * at [aggregate]'s `toMs` ends there. Intervals are cut to the window and split at local midnight,
 * so a DST day has 23 or 25 hours. Split screen counts both apps (their sum can exceed wall time).
 */
class ForegroundTimeAggregator(private val zone: ZoneId, private val excludedPackages: Set<String>) {
    private class Foreground(val startMs: Long) {
        val activities = HashSet<String?>()
        var leftAtMs: Long? = null
    }

    private class Day(var foregroundMs: Long = 0, var launches: Int = 0, var lastEventMs: Long? = null)

    fun aggregate(events: List<UsageEventSample>, fromMs: Long, toMs: Long): List<UsageDay> {
        val open = HashMap<String, Foreground>()
        val days = LinkedHashMap<Pair<String, String>, Day>()

        fun day(atMs: Long, packageName: String) = days.getOrPut(date(atMs) to packageName) { Day() }

        fun close(packageName: String, endMs: Long) {
            val fg = open.remove(packageName) ?: return
            addInterval(packageName, fg.startMs, fg.leftAtMs ?: endMs, fromMs, toMs, ::day)
        }

        fun closeLeft(nowMs: Long, keep: String? = null) {
            open.entries.filter { (pkg, fg) ->
                val left = fg.leftAtMs ?: return@filter false
                pkg != keep && (keep != null || nowMs - left > MeasurementConfig.USAGE_SAME_APP_GRACE_MS)
            }.map { it.key }.forEach { close(it, nowMs) }
        }

        for (event in events.sortedBy { it.timestampMs }) {
            val t = event.timestampMs
            closeLeft(t)
            val pkg = event.packageName
            when (event.kind) {
                UsageEventKind.RESUMED -> {
                    closeLeft(t, keep = pkg) // another app is up: whoever was leaving has left
                    if (pkg in excludedPackages) continue
                    val fg = open[pkg] ?: Foreground(t).also {
                        open[pkg] = it
                        if (t in fromMs..toMs) day(t, pkg).launches++
                    }
                    fg.leftAtMs = null
                    fg.activities += event.activity
                    noteEvent(t, pkg, fromMs, toMs, ::day)
                }
                UsageEventKind.PAUSED, UsageEventKind.STOPPED -> {
                    val fg = open[pkg] ?: continue
                    if (!fg.activities.remove(event.activity)) continue
                    if (fg.activities.isEmpty()) fg.leftAtMs = t
                    noteEvent(t, pkg, fromMs, toMs, ::day)
                }
                UsageEventKind.SCREEN_OFF, UsageEventKind.SHUTDOWN -> open.keys.toList().forEach { close(it, t) }
                UsageEventKind.STARTUP -> {
                    // What left before the restart keeps its end; what was still open has none.
                    open.entries.filter { it.value.leftAtMs != null }.map { it.key }.forEach { close(it, t) }
                    open.clear()
                }
            }
        }
        open.keys.toList().forEach { close(it, toMs) }

        return days.map { (key, d) -> UsageDay(key.first, key.second, d.foregroundMs, d.launches, d.lastEventMs) }
            .filter { it.foregroundMs > 0 || it.launchCount > 0 }
    }

    private fun addInterval(pkg: String, startMs: Long, endMs: Long, fromMs: Long, toMs: Long, day: (Long, String) -> Day) {
        var cursor = maxOf(startMs, fromMs)
        val end = minOf(endMs, toMs)
        while (cursor < end) {
            val midnight = LocalDate.parse(date(cursor)).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val sliceEnd = minOf(end, midnight)
            day(cursor, pkg).foregroundMs += sliceEnd - cursor
            cursor = sliceEnd
        }
    }

    private fun noteEvent(t: Long, pkg: String, fromMs: Long, toMs: Long, day: (Long, String) -> Day) {
        if (t !in fromMs..toMs) return
        val d = day(t, pkg)
        d.lastEventMs = maxOf(d.lastEventMs ?: t, t)
    }

    private fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
}
