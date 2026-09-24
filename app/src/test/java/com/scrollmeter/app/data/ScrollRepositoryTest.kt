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
    private val repository = ScrollRepository(dao)

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

    private fun usage(date: String, pkg: String, ms: Long) = UsageDay(date, pkg, ms, 1, null)
}
