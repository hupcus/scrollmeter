package com.scrollmeter.app.data

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.aggregation.AggregateDelta
import com.scrollmeter.app.aggregation.ClosedSession
import com.scrollmeter.app.data.local.ScrollDao
import com.scrollmeter.app.data.local.ScrollDatabase
import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.data.repository.ScrollRepository
import com.scrollmeter.app.usage.UsageDay
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Room v1 in memory (D8, spec §16, §17, ADR-021): insert-or-add, usage replace, range sums, the per-app union. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrollRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ScrollDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao: ScrollDao = db.scrollDao()
    private val unflushed = MutableStateFlow<Map<Pair<String, String>, Double>>(emptyMap())
    private val repository = ScrollRepository(dao, unflushed)

    @After
    fun tearDown() = db.close()

    private fun delta(
        date: String = "2026-09-21",
        pkg: String = "a",
        mm: Double = 10.0,
        first: Long? = 1_000,
        last: Long? = 2_000,
        cv: Int = 0,
        activeMs: Long = 0,
    ) = AggregateDelta(
        date = date, packageName = pkg, distanceMm = mm, horizontalDistanceMm = 0.0, verticalDistanceMm = mm,
        rawDeltaXPx = 0, rawDeltaYPx = 100, measuredEventCount = 1, fallbackEventCount = 0,
        unmeasurableEventCount = 0, rejectedOutlierCount = 0, firstEventTimestamp = first,
        lastEventTimestamp = last, calibrationVersion = cv, activeScrollMs = activeMs,
    )

    @Test
    fun repeatedFlushesAddUp() = runBlocking {
        repository.write(listOf(delta(mm = 10.0, first = 5_000, last = 6_000, cv = 1, activeMs = 300)), emptyList())
        repository.write(listOf(delta(mm = 2.5, first = 1_000, last = 9_000, cv = 0, activeMs = 200)), emptyList())
        val row = dao.aggregate("2026-09-21", "a")!!
        assertThat(row.distanceMm).isEqualTo(12.5)
        assertThat(row.verticalDistanceMm).isEqualTo(12.5)
        assertThat(row.rawDeltaYPx).isEqualTo(200)
        assertThat(row.measuredEventCount).isEqualTo(2)
        assertThat(row.firstEventTimestamp).isEqualTo(1_000)
        assertThat(row.lastEventTimestamp).isEqualTo(9_000)
        assertThat(row.calibrationVersion).isEqualTo(1)
        assertThat(row.activeScrollMs).isEqualTo(500)
    }

    @Test
    fun aFlushWithoutTimestampsKeepsTheStoredOnesAndViceVersa() = runBlocking {
        repository.write(listOf(delta(first = null, last = null)), emptyList())
        assertThat(dao.aggregate("2026-09-21", "a")!!.firstEventTimestamp).isNull()
        repository.write(listOf(delta(first = 3_000, last = 4_000)), emptyList())
        repository.write(listOf(delta(first = null, last = null)), emptyList())
        val row = dao.aggregate("2026-09-21", "a")!!
        assertThat(row.firstEventTimestamp).isEqualTo(3_000)
        assertThat(row.lastEventTimestamp).isEqualTo(4_000)
    }

    @Test
    fun appsAndDaysAreSeparateRows() = runBlocking {
        repository.write(listOf(delta(pkg = "a"), delta(pkg = "b", mm = 1.0), delta(date = "2026-09-22", pkg = "a", mm = 4.0)), emptyList())
        assertThat(dao.aggregate("2026-09-21", "a")!!.distanceMm).isEqualTo(10.0)
        assertThat(dao.aggregate("2026-09-21", "b")!!.distanceMm).isEqualTo(1.0)
        assertThat(dao.aggregate("2026-09-22", "a")!!.distanceMm).isEqualTo(4.0)
    }

    @Test
    fun sessionsAreStored() = runBlocking {
        repository.write(emptyList(), listOf(ClosedSession("a", 1_000, 9_000, 12.0, 4)))
        val session = dao.sessions().single()
        assertThat(session.packageName).isEqualTo("a")
        assertThat(session.endTimestamp).isEqualTo(9_000)
        assertThat(session.eventCount).isEqualTo(4)
    }

    @Test
    fun rangesSumDayWeekMonthAndLifetime() = runBlocking {
        // Monday 2026-09-21 … Sunday 2026-09-27 is one ISO week.
        repository.write(
            listOf(
                delta(date = "2026-09-20", mm = 1.0), // previous week (Sunday)
                delta(date = "2026-09-21", mm = 10.0),
                delta(date = "2026-09-27", mm = 100.0, pkg = "b"),
                delta(date = "2026-10-01", mm = 1_000.0), // next month
            ),
            emptyList(),
        )
        val day = LocalDate.parse("2026-09-24")
        assertThat(repository.distance(DateRange.day(LocalDate.parse("2026-09-21"))).first()).isEqualTo(10.0)
        assertThat(repository.distance(DateRange.week(day)).first()).isEqualTo(110.0)
        assertThat(repository.distance(DateRange.month(day)).first()).isEqualTo(111.0)
        assertThat(repository.lifetimeDistance().first()).isEqualTo(1_111.0)
        assertThat(repository.distance(DateRange.day(LocalDate.parse("2026-09-25"))).first()).isEqualTo(0.0)
    }

    @Test
    fun usageSyncReplacesItsDaysAndLeavesOthers() = runBlocking {
        repository.replaceDays("2026-09-20", "2026-09-21", listOf(usage("2026-09-20", "a", 60_000), usage("2026-09-21", "a", 120_000)), 1)
        repository.replaceDays("2026-09-21", "2026-09-21", listOf(usage("2026-09-21", "b", 30_000)), 2)
        assertThat(dao.usage("2026-09-20", "a")!!.foregroundMs).isEqualTo(60_000)
        assertThat(dao.usage("2026-09-21", "a")).isNull()
        assertThat(dao.usage("2026-09-21", "b")!!.syncedAt).isEqualTo(2)
    }

    @Test
    fun perAppSummaryJoinsScrollAndTimeAndKeepsUnknownAsNull() = runBlocking {
        repository.write(listOf(delta(pkg = "feed", mm = 50.0, activeMs = 4_000), delta(pkg = "scrollOnly", mm = 5.0)), emptyList())
        repository.replaceDays("2026-09-21", "2026-09-21", listOf(usage("2026-09-21", "feed", 600_000), usage("2026-09-21", "video", 900_000)), 1)
        val apps = repository.apps(DateRange.day(LocalDate.parse("2026-09-21"))).first().associateBy { it.packageName }
        assertThat(apps.keys).containsExactly("feed", "scrollOnly", "video")
        assertThat(apps.getValue("feed").distanceMm).isEqualTo(50.0)
        assertThat(apps.getValue("feed").activeScrollMs).isEqualTo(4_000)
        assertThat(apps.getValue("feed").foregroundMs).isEqualTo(600_000)
        assertThat(apps.getValue("scrollOnly").foregroundMs).isNull()
        assertThat(apps.getValue("video").distanceMm).isNull()
        assertThat(apps.getValue("video").measuredEventCount).isNull()
        assertThat(apps.getValue("video").foregroundMs).isEqualTo(900_000)
    }

    @Test
    fun perDaySummaryCoversDaysWithOnlyOneKindOfData() = runBlocking {
        repository.write(listOf(delta(date = "2026-09-21", mm = 3.0), delta(date = "2026-09-21", pkg = "b", mm = 4.0)), emptyList())
        repository.replaceDays("2026-09-22", "2026-09-22", listOf(usage("2026-09-22", "a", 1_000)), 1)
        val days = repository.days(DateRange(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-23"))).first()
        assertThat(days.map { it.date }).containsExactly("2026-09-21", "2026-09-22").inOrder()
        assertThat(days[0].distanceMm).isEqualTo(7.0)
        assertThat(days[0].foregroundMs).isNull()
        assertThat(days[1].distanceMm).isNull()
        assertThat(days[1].foregroundMs).isEqualTo(1_000)
    }

    @Test
    fun unflushedDistanceIsAddedEverywhereItBelongs() = runBlocking {
        repository.write(listOf(delta(pkg = "a", mm = 10.0), delta(date = "2026-09-20", pkg = "a", mm = 1.0)), emptyList())
        unflushed.value = mapOf(("2026-09-21" to "a") to 2.0, ("2026-09-21" to "new") to 30.0, ("2026-09-22" to "a") to 5.0)
        val day = DateRange.day(LocalDate.parse("2026-09-21"))
        assertThat(repository.distance(day).first()).isEqualTo(42.0)
        assertThat(repository.lifetimeDistance().first()).isEqualTo(48.0)
        val apps = repository.apps(day).first()
        assertThat(apps.map { it.packageName to it.distanceMm }).containsExactly("new" to 30.0, "a" to 12.0).inOrder()
        val days = repository.days(DateRange(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-22"))).first()
        assertThat(days.map { it.date to it.distanceMm }).containsExactly("2026-09-20" to 1.0, "2026-09-21" to 42.0, "2026-09-22" to 5.0).inOrder()
    }

    @Test
    fun oneAppPerDayJoinsDistanceAndTimeAndTheFirstMeasuredDay() = runBlocking {
        assertThat(repository.firstMeasuredDay().first()).isNull()
        repository.write(listOf(delta(date = "2026-09-20", pkg = "a", mm = 3.0, activeMs = 100), delta(date = "2026-09-21", pkg = "b", mm = 9.0)), emptyList())
        repository.replaceDays("2026-09-20", "2026-09-22", listOf(usage("2026-09-22", "a", 60_000)), 1)
        unflushed.value = mapOf(("2026-09-21" to "a") to 1.5, ("2026-09-19" to "b") to 1.0)
        val days = repository.appDays("a", DateRange(LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-22"))).first()
        assertThat(days.map { Triple(it.date, it.distanceMm, it.foregroundMs) })
            .containsExactly(Triple("2026-09-20", 3.0, null), Triple("2026-09-21", 1.5, null), Triple("2026-09-22", null, 60_000L)).inOrder()
        assertThat(days[0].activeScrollMs).isEqualTo(100)
        assertThat(repository.firstMeasuredDay().first()).isEqualTo(LocalDate.parse("2026-09-19")) // only in memory so far
    }

    private fun usage(date: String, pkg: String, ms: Long) = UsageDay(date, pkg, ms, 1, null)

    @Test
    fun anExcludedAppDisappearsFromEveryReadAndComesBack() = runBlocking {
        val excluded = MutableStateFlow(emptySet<String>())
        val repo = ScrollRepository(dao, unflushed, excluded)
        repo.write(listOf(delta(pkg = "a", mm = 10.0), delta(pkg = "launcher", mm = 90.0)), emptyList())
        repo.replaceDays("2026-09-21", "2026-09-21", listOf(usage("2026-09-21", "launcher", 60_000)), syncedAtMs = 1)
        unflushed.value = mapOf(("2026-09-21" to "launcher") to 5.0)
        val day = DateRange.day(LocalDate.parse("2026-09-21"))
        assertThat(repo.distance(day).first()).isEqualTo(105.0)

        excluded.value = setOf("launcher")
        assertThat(repo.distance(day).first()).isEqualTo(10.0)
        assertThat(repo.lifetimeDistance().first()).isEqualTo(10.0)
        assertThat(repo.apps(day).first().map { it.packageName }).containsExactly("a")
        assertThat(repo.days(day).first().single().foregroundMs).isNull()
        assertThat(repo.exportAppDays().map { it.packageName }).containsExactly("a")
        // The exclusion list itself still sees it, and nothing was deleted.
        assertThat(repo.seenPackages().first()).containsExactly("a", "launcher")

        excluded.value = emptySet()
        assertThat(repo.distance(day).first()).isEqualTo(105.0)
    }

    /** Unreadable settings: the screens fall back to "no exclusions", the export fails instead of leaking. */
    @Test
    fun theExportFailsWhenTheExclusionsCannotBeRead() = runBlocking {
        val repo = ScrollRepository(dao, unflushed, MutableStateFlow(emptySet()), exportExcluded = { throw java.io.IOException("settings") })
        repo.write(listOf(delta(pkg = "excluded-before")), emptyList())
        val appDays = runCatching { repo.exportAppDays() }
        val days = runCatching { repo.exportDays() }
        assertThat(appDays.exceptionOrNull()).isInstanceOf(java.io.IOException::class.java)
        assertThat(days.exceptionOrNull()).isInstanceOf(java.io.IOException::class.java)
    }

    @Test
    fun exportRowsJoinScrollAndTimeAndDaysSumEvents() = runBlocking {
        repository.write(listOf(delta(pkg = "a", mm = 10.0, activeMs = 4_000), delta(date = "2026-09-22", pkg = "a", mm = 2.0)), emptyList())
        repository.replaceDays("2026-09-21", "2026-09-21", listOf(usage("2026-09-21", "a", 60_000), usage("2026-09-21", "yt", 30_000)), syncedAtMs = 1)
        val rows = repository.exportAppDays()
        assertThat(rows.map { "${it.date}/${it.packageName}" }).containsExactly("2026-09-21/a", "2026-09-21/yt", "2026-09-22/a").inOrder()
        assertThat(rows[0].foregroundMs).isEqualTo(60_000)
        assertThat(rows[0].activeScrollMs).isEqualTo(4_000)
        assertThat(rows[1].distanceMm).isNull()
        assertThat(rows[1].measuredEventCount).isNull()
        val days = repository.exportDays()
        assertThat(days.map { it.date to it.events }).containsExactly("2026-09-21" to 1L, "2026-09-22" to 1L).inOrder()
    }

    @Test
    fun notificationFactsSeeTodayLiveAndYesterday() = runBlocking {
        repository.write(
            listOf(delta(date = "2026-09-20", mm = 300.0), delta(date = "2026-09-21", mm = 100.0), delta(date = "2026-09-22", mm = 50.0), delta(date = "2026-09-23", mm = 20.0)),
            emptyList(),
        )
        unflushed.value = mapOf(("2026-09-23" to "a") to 5.0)
        val facts = repository.notificationFacts(LocalDate.parse("2026-09-23"), limitMm = 500.0)
        assertThat(facts.limitMm).isEqualTo(500.0)
        assertThat(facts.todayMm).isEqualTo(25.0)
        assertThat(facts.yesterdayMm).isEqualTo(50.0)
    }

    @Test
    fun oldSessionsArePruned() = runBlocking {
        repository.write(emptyList(), listOf(ClosedSession("a", 1_000, 2_000, 1.0, 1), ClosedSession("a", 50_000, 60_000, 1.0, 1)))
        assertThat(repository.pruneSessions(beforeMs = 10_000)).isEqualTo(1)
        assertThat(dao.sessions().map { it.startTimestamp }).containsExactly(50_000L)
        Unit
    }
}
