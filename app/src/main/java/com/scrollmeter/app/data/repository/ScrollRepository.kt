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
import java.time.LocalDate
import com.scrollmeter.app.data.model.ExportAppDay
import com.scrollmeter.app.data.model.ExportDay
import com.scrollmeter.app.notifications.NotificationFacts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The one door to the stored data: the service writes flushes and usage syncs through it, the UI
 * reads Flows that Room re-emits after every write (spec §63). Service and UI share the process,
 * so they share this instance through `AppGraph`.
 *
 * Distances read here are **live**: [unflushed] — what the service's pipeline has counted but not
 * written yet, per (date, package) — is added to the stored rows, so a number never lags behind a
 * scroll by the 10 s flush interval.
 *
 * Apps in [excluded] are left out of every total, list and export while they are excluded; their
 * rows stay and come back when the app is included again (ADR-031).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollRepository(
    private val dao: ScrollDao,
    private val unflushed: Flow<Map<Pair<String, String>, Double>> = flowOf(emptyMap()),
    private val excluded: Flow<Set<String>> = flowOf(emptySet()),
    /**
     * The exclusions for the export, read so that an unreadable settings file fails the export rather
     * than put excluded apps into the files (the screens' [excluded] falls back to "none").
     */
    private val exportExcluded: suspend () -> Set<String> = { excluded.first() },
) : AggregateStore, UsageStore {
    /** Pending distance without excluded apps (one may have been excluded while still pending). */
    private fun <T> withExcluded(block: (List<String>, Flow<Map<Pair<String, String>, Double>>) -> Flow<T>): Flow<T> =
        excluded.distinctUntilChanged().flatMapLatest { set ->
            block(set.toList(), unflushed.map { pending -> if (set.isEmpty()) pending else pending.filterKeys { it.second !in set } })
        }

    override suspend fun write(deltas: List<AggregateDelta>, sessions: List<ClosedSession>) =
        dao.addFlush(deltas.map(::entity), sessions.map(::entity))

    override suspend fun replaceDays(fromDate: String, toDate: String, days: List<UsageDay>, syncedAtMs: Long) =
        dao.replaceUsage(fromDate, toDate, days.map { DailyAppUsageEntity(it.date, it.packageName, it.foregroundMs, it.launchCount, it.lastEventTimestamp, syncedAtMs) })

    fun distance(range: DateRange): Flow<Double> = withExcluded { ex, unflushed ->
        combine(dao.distanceBetween(range.fromKey, range.toKey, ex), unflushed) { stored, pending ->
            stored + pending.filterKeys { it.first in range }.values.sum()
        }
    }

    fun lifetimeDistance(): Flow<Double> = withExcluded { ex, unflushed ->
        combine(dao.lifetimeDistance(ex), unflushed) { stored, pending -> stored + pending.values.sum() }
    }

    /** Per app, by distance then time in app; an app seen only by the pipeline so far is included. */
    fun apps(range: DateRange): Flow<List<AppSummary>> = withExcluded { ex, unflushed ->
        combine(dao.appsBetween(range.fromKey, range.toKey, ex), unflushed) { stored, pending ->
            val extra = HashMap<String, Double>()
            pending.forEach { (key, mm) -> if (key.first in range) extra.merge(key.second, mm, Double::plus) }
            if (extra.isEmpty()) return@combine stored
            val merged = stored.map { app -> extra.remove(app.packageName)?.let { app.copy(distanceMm = (app.distanceMm ?: 0.0) + it) } ?: app } +
                extra.map { (pkg, mm) -> AppSummary(pkg, mm, null, null, null, null, null, null) }
            merged.sortedWith(compareByDescending<AppSummary> { it.distanceMm ?: -1.0 }.thenByDescending { it.foregroundMs ?: -1L })
        }
    }

    fun days(range: DateRange): Flow<List<DaySummary>> = withExcluded { ex, unflushed ->
        combine(dao.daysBetween(range.fromKey, range.toKey, ex), unflushed) { stored, pending ->
            val extra = HashMap<String, Double>()
            pending.forEach { (key, mm) -> if (key.first in range) extra.merge(key.first, mm, Double::plus) }
            if (extra.isEmpty()) return@combine stored
            val merged = stored.map { day -> extra.remove(day.date)?.let { day.copy(distanceMm = (day.distanceMm ?: 0.0) + it) } ?: day } +
                extra.map { (date, mm) -> DaySummary(date, mm, null, null) }
            merged.sortedBy { it.date }
        }
    }

    /** One app per day in [range] (app detail), unflushed distance of that app included. */
    fun appDays(packageName: String, range: DateRange): Flow<List<DaySummary>> =
        combine(dao.appDaysBetween(packageName, range.fromKey, range.toKey), unflushed) { stored, pending ->
            val extra = HashMap<String, Double>()
            pending.forEach { (key, mm) -> if (key.second == packageName && key.first in range) extra.merge(key.first, mm, Double::plus) }
            if (extra.isEmpty()) return@combine stored
            val merged = stored.map { day -> extra.remove(day.date)?.let { day.copy(distanceMm = (day.distanceMm ?: 0.0) + it) } ?: day } +
                extra.map { (date, mm) -> DaySummary(date, mm, null, null) }
            merged.sortedBy { it.date }
        }

    /** The first measured day, or null before anything was stored; a day only in memory counts too. */
    fun firstMeasuredDay(): Flow<LocalDate?> = withExcluded { ex, unflushed ->
        combine(dao.firstMeasuredDate(ex), unflushed) { stored, pending ->
            (listOfNotNull(stored) + pending.keys.map { it.first }).minOrNull()?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        }
    }

    /** Every package with stored data, excluded ones included (the exclusion list). */
    fun seenPackages(): Flow<List<String>> = dao.seenPackages()

    /** Facts for the notification check (ADR-031): today live, earlier days stored. */
    suspend fun notificationFacts(today: LocalDate, goalMm: Double): NotificationFacts {
        val ex = excluded.first().toList()
        val prior = dao.daysBefore(today.toString(), ex)
        return NotificationFacts(
            today = today,
            todayMm = distance(DateRange.day(today)).first(),
            goalMm = goalMm,
            previousBestMm = prior.bestMm,
            priorMeasuredDays = prior.days,
            yesterdayMm = distance(DateRange.day(today.minusDays(1))).first(),
        )
    }

    suspend fun exportAppDays(): List<ExportAppDay> = dao.exportAppDays(exportExcluded().toList())

    suspend fun exportDays(): List<ExportDay> = dao.exportDays(exportExcluded().toList())

    suspend fun pruneSessions(beforeMs: Long): Int = dao.deleteSessionsBefore(beforeMs)

    private operator fun DateRange.contains(date: String): Boolean = date in fromKey..toKey

    private fun entity(d: AggregateDelta) = DailyAppAggregateEntity(
        d.date, d.packageName, d.distanceMm, d.horizontalDistanceMm, d.verticalDistanceMm, d.rawDeltaXPx, d.rawDeltaYPx,
        d.measuredEventCount, d.fallbackEventCount, d.unmeasurableEventCount, d.rejectedOutlierCount,
        d.firstEventTimestamp, d.lastEventTimestamp, d.calibrationVersion, d.activeScrollMs,
    )

    private fun entity(s: ClosedSession) =
        ScrollSessionEntity(packageName = s.packageName, startTimestamp = s.startTimestamp, endTimestamp = s.endTimestamp, distanceMm = s.distanceMm, eventCount = s.eventCount)
}
