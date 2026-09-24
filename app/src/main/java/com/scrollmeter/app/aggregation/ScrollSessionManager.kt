package com.scrollmeter.app.aggregation

import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.MeasurementResult

/**
 * Scroll sessions per package (spec §18, D9): counted events of one package belong to the same
 * session while they are at most [MeasurementConfig.SCROLL_SESSION_GAP_MS] apart; a longer pause
 * closes it. Closed sessions wait here until the next flush writes them. Pure Kotlin, single thread.
 * The pause is judged on uptime and wall clock ([pauseMs]), so an hour of deep sleep between two
 * scrolls ends the session; start and end are wall-clock ms for display.
 */
class ScrollSessionManager {
    private class Open(val start: Long, var end: Long, var lastUptime: Long, var distanceMm: Double, var events: Long)

    private val open = HashMap<String, Open>()
    private val closed = ArrayList<ClosedSession>()

    fun add(result: MeasurementResult) {
        if (!result.accepted) return
        val sample = result.sample
        val packageName = sample.packageName ?: return
        val current = open[packageName]
        val uptimeGap = if (current == null) -1L else sample.uptimeMs - current.lastUptime
        if (current != null && uptimeGap >= 0 && pauseMs(uptimeGap, sample.wallTimeMs - current.end) <= MeasurementConfig.SCROLL_SESSION_GAP_MS) {
            current.end = sample.wallTimeMs
            current.lastUptime = sample.uptimeMs
            current.distanceMm += result.distance.totalMm
            current.events++
        } else {
            current?.let { close(packageName, it) }
            open[packageName] = Open(sample.wallTimeMs, sample.wallTimeMs, sample.uptimeMs, result.distance.totalMm, 1)
        }
    }

    /** Closes every session idle for longer than the session gap at [nowUptimeMs] / [nowWallMs]. */
    fun closeIdle(nowUptimeMs: Long, nowWallMs: Long) {
        open.entries.filter { pauseMs(nowUptimeMs - it.value.lastUptime, nowWallMs - it.value.end) > MeasurementConfig.SCROLL_SESSION_GAP_MS }
            .forEach { (packageName, session) -> close(packageName, session) }
    }

    /** The service is stopping: what is open ends now. */
    fun closeAll() {
        open.entries.toList().forEach { (packageName, session) -> close(packageName, session) }
    }

    val hasClosed: Boolean get() = closed.isNotEmpty()

    fun drainClosed(): List<ClosedSession> = closed.toList().also { closed.clear() }

    /** A failed write: keep the sessions for the next flush. */
    fun restore(sessions: List<ClosedSession>) {
        closed.addAll(0, sessions)
    }

    private fun close(packageName: String, session: Open) {
        open.remove(packageName)
        closed += ClosedSession(packageName, session.start, session.end, session.distanceMm, session.events)
    }
}
