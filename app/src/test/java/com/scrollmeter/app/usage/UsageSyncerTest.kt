package com.scrollmeter.app.usage

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.measurement.MeasurementConfig
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** ADR-021, PLAN Phase 3: the sync window, idempotence and the no-access path. */
class UsageSyncerTest {
    private val zone = ZoneId.of("Europe/Prague")
    private fun at(local: String): Long = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private var now = at("2026-09-21T12:00")
    private var access = true
    private val reads = mutableListOf<Pair<Long, Long>>()
    private val writes = mutableListOf<Triple<String, String, List<UsageDay>>>()
    private var events = listOf(
        UsageEventSample(at("2026-09-21T10:00"), "a", "Main", UsageEventKind.RESUMED),
        UsageEventSample(at("2026-09-21T10:30"), "a", "Main", UsageEventKind.PAUSED),
        UsageEventSample(at("2026-09-21T10:30"), OWN, "Main", UsageEventKind.RESUMED),
        UsageEventSample(at("2026-09-21T10:40"), OWN, "Main", UsageEventKind.PAUSED),
        UsageEventSample(at("2026-09-21T11:00"), "hidden", "Main", UsageEventKind.RESUMED),
        UsageEventSample(at("2026-09-21T11:10"), "hidden", "Main", UsageEventKind.PAUSED),
    )
    private val state = object : UsageSyncState {
        var last: Long? = null
        var floor: Long? = null
        override suspend fun lastSyncMs() = last
        override suspend fun setLastSyncMs(ms: Long) {
            last = ms
        }
        override suspend fun dataFloorMs() = floor
    }
    private val syncer = UsageSyncer(
        reader = { begin, end -> reads += begin to end; events.filter { it.timestampMs in begin..end } },
        store = { from, to, days, _ -> writes += Triple(from, to, days) },
        state = state,
        hasAccess = { access },
        ownPackage = OWN,
        excludedPackages = { setOf("hidden") },
        nowMs = { now },
        zone = { zone },
    )

    @Test
    fun withoutAccessNothingIsReadOrWritten() = runBlocking {
        access = false
        assertThat(syncer.sync()).isEqualTo(UsageSyncResult.NoAccess)
        assertThat(reads).isEmpty()
        assertThat(writes).isEmpty()
        assertThat(state.last).isNull()
    }

    @Test
    fun theFirstSyncCoversNineDaysReadWithTwelveHoursOfLeadIn() = runBlocking {
        assertThat(syncer.sync()).isEqualTo(UsageSyncResult.Synced("2026-09-12", "2026-09-21", 1))
        assertThat(reads.single()).isEqualTo(at("2026-09-12T00:00") - MeasurementConfig.USAGE_SYNC_LOOKBACK_MS to now)
        assertThat(writes.single().third.single()).isEqualTo(UsageDay("2026-09-21", "a", 30 * 60_000L, 1, at("2026-09-21T10:30")))
        assertThat(state.last).isEqualTo(now)
    }

    @Test
    fun theNextSyncStartsADayBeforeTheLastOne() = runBlocking {
        syncer.sync()
        now = at("2026-09-21T13:00")
        syncer.sync()
        assertThat(writes.last().first).isEqualTo("2026-09-20")
        assertThat(reads.last().first).isEqualTo(at("2026-09-20T00:00") - MeasurementConfig.USAGE_SYNC_LOOKBACK_MS)
    }

    @Test
    fun anOldLastSyncIsCappedAtNineDays() = runBlocking {
        state.last = at("2026-08-01T00:00")
        syncer.sync()
        assertThat(writes.single().first).isEqualTo("2026-09-12")
    }

    @Test
    fun syncingTwiceWritesTheSameDays() = runBlocking {
        syncer.sync()
        state.last = null
        syncer.sync()
        assertThat(writes[0]).isEqualTo(writes[1])
    }

    @Test
    fun syncIfStaleSyncsOnlyAfterSixHoursOrAClockJump() = runBlocking {
        assertThat(syncer.syncIfStale()).isNotNull()
        now += MeasurementConfig.USAGE_SYNC_STALE_MS - 1
        assertThat(syncer.syncIfStale()).isNull()
        now += 1
        assertThat(syncer.syncIfStale()).isNotNull()
        now -= 60 * 60_000L // the clock was set back an hour
        assertThat(syncer.syncIfStale()).isNotNull()
        assertThat(writes).hasSize(3)
    }


    @Test
    fun timeBeforeTheEraseFloorIsNeverImportedAgain() = runBlocking {
        state.floor = at("2026-09-21T10:15") // "Smazat všechna data" in the middle of the 10:00–10:30 visit
        syncer.sync()
        assertThat(writes.single().first).isEqualTo("2026-09-21")
        assertThat(writes.single().third.single().foregroundMs).isEqualTo(15 * 60_000L)
        // Even a last sync from before the erase cannot reach back past the floor.
        state.last = at("2026-09-19T08:00")
        syncer.sync()
        assertThat(writes.last().first).isEqualTo("2026-09-21")
        assertThat(writes.last().third.single().foregroundMs).isEqualTo(15 * 60_000L)
    }

    @Test
    fun exclusiveKeepsASyncOut() = runBlocking {
        var inside = false
        syncer.exclusive { inside = true }
        assertThat(inside).isTrue()
    }

    private companion object {
        const val OWN = "com.scrollmeter.app.debug"
    }
}
