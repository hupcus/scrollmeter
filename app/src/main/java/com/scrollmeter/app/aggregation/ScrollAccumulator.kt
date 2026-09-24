package com.scrollmeter.app.aggregation

import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.MeasurementResult
import com.scrollmeter.app.measurement.MeasurementSource
import java.time.Instant
import java.time.ZoneId

/**
 * Sums engine results in memory per (local date, package) so the database sees one write per
 * flush, not one per event (spec §16). Pure Kotlin; the service calls it from its single pipeline
 * thread and decides when to flush: [add] says when 50 events or a new day make a flush due, a
 * 10 s ticker covers quiet periods, and unbind / interrupt / destroy flush what is left.
 *
 * Active scroll time (ADR-022): a gap of at most [MeasurementConfig.ACTIVE_SCROLL_GAP_MS] between
 * two counted events of the same package is added to the later event's row; a longer pause is
 * not scrolling. Gaps use the events' uptime, so a wall-clock change cannot fake time.
 */
class ScrollAccumulator(private val zone: () -> ZoneId = ZoneId::systemDefault) {
    private val pending = LinkedHashMap<Pair<String, String>, AggregateDelta>()
    private val lastCountedUptime = HashMap<String, Long>()
    private var pendingEvents = 0
    private var lastDate: String? = null

    val hasPending: Boolean get() = pending.isNotEmpty()

    /** Adds one result; true when a flush is due now. EXCLUDED and superseded events are not stored. */
    fun add(result: MeasurementResult): Boolean {
        if (result.source == MeasurementSource.EXCLUDED || result.source == MeasurementSource.SUPERSEDED_BY_DIRECT) return false
        val sample = result.sample
        val packageName = sample.packageName ?: return false
        val date = localDate(sample.wallTimeMs)
        val counted = result.accepted
        var activeMs = 0L
        if (counted) {
            val previous = lastCountedUptime[packageName]
            val gap = if (previous == null) -1L else sample.uptimeMs - previous
            if (gap in 1..MeasurementConfig.ACTIVE_SCROLL_GAP_MS) activeMs = gap
            lastCountedUptime[packageName] = sample.uptimeMs
        }
        val delta = AggregateDelta(
            date = date,
            packageName = packageName,
            distanceMm = if (counted) result.distance.totalMm else 0.0,
            horizontalDistanceMm = if (counted) result.distance.horizontalMm else 0.0,
            verticalDistanceMm = if (counted) result.distance.verticalMm else 0.0,
            rawDeltaXPx = if (counted) Math.abs(result.dxPx).toLong() else 0,
            rawDeltaYPx = if (counted) Math.abs(result.dyPx).toLong() else 0,
            measuredEventCount = if (result.source == MeasurementSource.DIRECT_DELTA) 1 else 0,
            fallbackEventCount = if (result.source == MeasurementSource.FALLBACK_POSITION) 1 else 0,
            unmeasurableEventCount = if (result.source == MeasurementSource.UNMEASURABLE) 1 else 0,
            rejectedOutlierCount = if (result.source == MeasurementSource.OUTLIER_REJECTED) 1 else 0,
            firstEventTimestamp = if (counted) sample.wallTimeMs else null,
            lastEventTimestamp = if (counted) sample.wallTimeMs else null,
            calibrationVersion = result.calibrationVersion,
            activeScrollMs = activeMs,
        )
        merge(delta)
        pendingEvents++
        val newDay = lastDate != null && lastDate != date
        lastDate = date
        return newDay || pendingEvents >= MeasurementConfig.FLUSH_EVENT_COUNT
    }

    /** Everything summed since the last drain; the accumulator starts empty again. */
    fun drain(): List<AggregateDelta> {
        val out = pending.values.toList()
        pending.clear()
        pendingEvents = 0
        return out
    }

    /** A failed write: put the deltas back so the next flush retries them (spec §61). */
    fun restore(deltas: List<AggregateDelta>) = deltas.forEach(::merge)

    private fun merge(delta: AggregateDelta) {
        val key = delta.date to delta.packageName
        pending[key] = pending[key]?.plus(delta) ?: delta
    }

    private fun localDate(wallMs: Long): String = Instant.ofEpochMilli(wallMs).atZone(zone()).toLocalDate().toString()
}
