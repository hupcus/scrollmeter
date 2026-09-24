package com.scrollmeter.app.data.repository

import com.scrollmeter.app.aggregation.AggregateDelta
import com.scrollmeter.app.aggregation.AggregateStore
import com.scrollmeter.app.aggregation.ClosedSession
import com.scrollmeter.app.data.local.DailyAppAggregateEntity
import com.scrollmeter.app.data.local.DailyAppUsageEntity
import com.scrollmeter.app.data.local.ScrollDao
import com.scrollmeter.app.data.local.ScrollSessionEntity
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.data.model.DaySummary
import com.scrollmeter.app.usage.UsageDay
import com.scrollmeter.app.usage.UsageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * The one door to the stored data: the service writes flushes and usage syncs through it, the UI
 * reads Flows that Room re-emits after every write (spec §63). Service and UI share the process,
 * so they share this instance through `AppGraph`.
 *
 * Distances read here are **live**: [unflushed] — what the service's pipeline has counted but not
 * written yet, per (date, package) — is added to the stored rows, so a number never lags behind a
 * scroll by the 10 s flush interval.
 */
class ScrollRepository(
    private val dao: ScrollDao,
    private val unflushed: Flow<Map<Pair<String, String>, Double>> = flowOf(emptyMap()),
) : AggregateStore, UsageStore {
    override suspend fun write(deltas: List<AggregateDelta>, sessions: List<ClosedSession>) =
        dao.addFlush(deltas.map(::entity), sessions.map(::entity))

    override suspend fun replaceDays(fromDate: String, toDate: String, days: List<UsageDay>, syncedAtMs: Long) =
        dao.replaceUsage(fromDate, toDate, days.map { DailyAppUsageEntity(it.date, it.packageName, it.foregroundMs, it.launchCount, it.lastEventTimestamp, syncedAtMs) })

    fun distance(range: DateRange): Flow<Double> =
        combine(dao.distanceBetween(range.fromKey, range.toKey), unflushed) { stored, pending ->
            stored + pending.filterKeys { it.first in range }.values.sum()
        }

    fun lifetimeDistance(): Flow<Double> = combine(dao.lifetimeDistance(), unflushed) { stored, pending -> stored + pending.values.sum() }

    /** Per app, by distance then time in app; an app seen only by the pipeline so far is included. */
    fun apps(range: DateRange): Flow<List<AppSummary>> =
        combine(dao.appsBetween(range.fromKey, range.toKey), unflushed) { stored, pending ->
            val extra = HashMap<String, Double>()
            pending.forEach { (key, mm) -> if (key.first in range) extra.merge(key.second, mm, Double::plus) }
            if (extra.isEmpty()) return@combine stored
            val merged = stored.map { app -> extra.remove(app.packageName)?.let { app.copy(distanceMm = (app.distanceMm ?: 0.0) + it) } ?: app } +
                extra.map { (pkg, mm) -> AppSummary(pkg, mm, null, null, null, null, null, null) }
            merged.sortedWith(compareByDescending<AppSummary> { it.distanceMm ?: -1.0 }.thenByDescending { it.foregroundMs ?: -1L })
        }

    fun days(range: DateRange): Flow<List<DaySummary>> =
        combine(dao.daysBetween(range.fromKey, range.toKey), unflushed) { stored, pending ->
            val extra = HashMap<String, Double>()
            pending.forEach { (key, mm) -> if (key.first in range) extra.merge(key.first, mm, Double::plus) }
            if (extra.isEmpty()) return@combine stored
            val merged = stored.map { day -> extra.remove(day.date)?.let { day.copy(distanceMm = (day.distanceMm ?: 0.0) + it) } ?: day } +
                extra.map { (date, mm) -> DaySummary(date, mm, null, null) }
            merged.sortedBy { it.date }
        }

    private operator fun DateRange.contains(date: String): Boolean = date in fromKey..toKey

    private fun entity(d: AggregateDelta) = DailyAppAggregateEntity(
        d.date, d.packageName, d.distanceMm, d.horizontalDistanceMm, d.verticalDistanceMm, d.rawDeltaXPx, d.rawDeltaYPx,
        d.measuredEventCount, d.fallbackEventCount, d.unmeasurableEventCount, d.rejectedOutlierCount,
        d.firstEventTimestamp, d.lastEventTimestamp, d.calibrationVersion, d.activeScrollMs,
    )

    private fun entity(s: ClosedSession) =
        ScrollSessionEntity(packageName = s.packageName, startTimestamp = s.startTimestamp, endTimestamp = s.endTimestamp, distanceMm = s.distanceMm, eventCount = s.eventCount)
}
