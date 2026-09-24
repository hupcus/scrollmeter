package com.scrollmeter.app.usage

import com.scrollmeter.app.measurement.MeasurementConfig
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where synced days go: rows of the recomputed dates are replaced, never added to (ADR-021). */
fun interface UsageStore {
    suspend fun replaceDays(fromDate: String, toDate: String, days: List<UsageDay>, syncedAtMs: Long)
}

/** When the last successful sync ran (DataStore in the app, a variable in tests). */
interface UsageSyncState {
    suspend fun lastSyncMs(): Long?
    suspend fun setLastSyncMs(ms: Long)
}

sealed interface UsageSyncResult {
    /** Usage access is not granted — nothing read, nothing written; time in app shows "—". */
    data object NoAccess : UsageSyncResult

    data class Synced(val fromDate: String, val toDate: String, val days: Int) : UsageSyncResult
}

/**
 * Snapshots time in app into our own table before Android prunes its events (~10 days, ADR-021).
 * Window: whole local days from `max(lastSync − 1 day, now − USAGE_SYNC_MAX_DAYS)` to now, read with
 * [MeasurementConfig.USAGE_SYNC_LOOKBACK_MS] of lead-in; every day in it is recomputed and replaced,
 * so running it twice changes nothing. One sync at a time (the app and the service both call it).
 * The own package and the user's excluded apps are left out (D18, spec §14). [reader] blocks —
 * call [sync] from an I/O dispatcher.
 */
class UsageSyncer(
    private val reader: UsageEventsReader,
    private val store: UsageStore,
    private val state: UsageSyncState,
    private val hasAccess: () -> Boolean,
    private val ownPackage: String,
    private val excludedPackages: suspend () -> Set<String> = { emptySet() },
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutex = Mutex()

    suspend fun sync(): UsageSyncResult = mutex.withLock {
        if (!hasAccess()) return UsageSyncResult.NoAccess
        val now = nowMs()
        val zone = zone()
        val earliest = now - MeasurementConfig.USAGE_SYNC_MAX_DAYS * DAY_MS
        val since = state.lastSyncMs()?.let { maxOf(it - DAY_MS, earliest) } ?: earliest
        val fromDay = Instant.ofEpochMilli(minOf(since, now)).atZone(zone).toLocalDate()
        val fromMs = fromDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val toDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString()

        val events = reader.read(fromMs - MeasurementConfig.USAGE_SYNC_LOOKBACK_MS, now)
        val days = ForegroundTimeAggregator(zone, excludedPackages() + ownPackage).aggregate(events, fromMs, now)
        store.replaceDays(fromDay.toString(), toDate, days, syncedAtMs = now)
        state.setLastSyncMs(now)
        UsageSyncResult.Synced(fromDay.toString(), toDate, days.size)
    }

    /** The service's door: sync only when the last one is older than [maxAgeMs] (PLAN Phase 3). */
    suspend fun syncIfStale(maxAgeMs: Long = MeasurementConfig.USAGE_SYNC_STALE_MS): UsageSyncResult? {
        val last = state.lastSyncMs()
        return if (last == null || nowMs() - last >= maxAgeMs || last > nowMs()) sync() else null
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
