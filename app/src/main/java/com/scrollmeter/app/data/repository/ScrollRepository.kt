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

/**
 * The one door to the stored data: the service writes flushes and usage syncs through it, the UI
 * reads Flows that Room re-emits after every write (spec §63). Service and UI share the process,
 * so they share this instance through `AppGraph`.
 */
class ScrollRepository(private val dao: ScrollDao) : AggregateStore, UsageStore {
    override suspend fun write(deltas: List<AggregateDelta>, sessions: List<ClosedSession>) =
        dao.addFlush(deltas.map(::entity), sessions.map(::entity))

    override suspend fun replaceDays(fromDate: String, toDate: String, days: List<UsageDay>, syncedAtMs: Long) =
        dao.replaceUsage(fromDate, toDate, days.map { DailyAppUsageEntity(it.date, it.packageName, it.foregroundMs, it.launchCount, it.lastEventTimestamp, syncedAtMs) })

    fun distance(range: DateRange): Flow<Double> = dao.distanceBetween(range.fromKey, range.toKey)

    fun lifetimeDistance(): Flow<Double> = dao.lifetimeDistance()

    fun apps(range: DateRange): Flow<List<AppSummary>> = dao.appsBetween(range.fromKey, range.toKey)

    fun days(range: DateRange): Flow<List<DaySummary>> = dao.daysBetween(range.fromKey, range.toKey)

    private fun entity(d: AggregateDelta) = DailyAppAggregateEntity(
        d.date, d.packageName, d.distanceMm, d.horizontalDistanceMm, d.verticalDistanceMm, d.rawDeltaXPx, d.rawDeltaYPx,
        d.measuredEventCount, d.fallbackEventCount, d.unmeasurableEventCount, d.rejectedOutlierCount,
        d.firstEventTimestamp, d.lastEventTimestamp, d.calibrationVersion, d.activeScrollMs,
    )

    private fun entity(s: ClosedSession) =
        ScrollSessionEntity(packageName = s.packageName, startTimestamp = s.startTimestamp, endTimestamp = s.endTimestamp, distanceMm = s.distanceMm, eventCount = s.eventCount)
}
