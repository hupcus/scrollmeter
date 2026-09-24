package com.scrollmeter.app.aggregation

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.measurement.APP
import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.MeasurementSource
import org.junit.Test

/** Spec §16, §17, ADR-022: what one flush carries per (local date, package). */
class ScrollAccumulatorTest {
    private val accumulator = ScrollAccumulator { UTC }

    @Test
    fun eventsOfOneAppAndDaySumIntoOneRow() {
        accumulator.add(result(uptimeMs = 1_000, mm = 10.0))
        accumulator.add(result(uptimeMs = 2_000, mm = 5.0, source = MeasurementSource.FALLBACK_POSITION))
        val rows = accumulator.drain()
        assertThat(rows).hasSize(1)
        val row = rows.single()
        assertThat(row.date).isEqualTo("2026-09-21")
        assertThat(row.packageName).isEqualTo(APP)
        assertThat(row.distanceMm).isEqualTo(15.0)
        assertThat(row.verticalDistanceMm).isEqualTo(15.0)
        assertThat(row.rawDeltaYPx).isEqualTo(200)
        assertThat(row.measuredEventCount).isEqualTo(1)
        assertThat(row.fallbackEventCount).isEqualTo(1)
        assertThat(row.firstEventTimestamp).isEqualTo(BASE_WALL_MS + 1_000)
        assertThat(row.lastEventTimestamp).isEqualTo(BASE_WALL_MS + 2_000)
        assertThat(accumulator.hasPending).isFalse()
    }

    @Test
    fun twoAppsAreTwoRows() {
        accumulator.add(result(packageName = "a"))
        accumulator.add(result(packageName = "b"))
        assertThat(accumulator.drain().map { it.packageName }).containsExactly("a", "b")
    }

    @Test
    fun uncountedEventsAreCountedButAddNoDistanceOrTimestamps() {
        accumulator.add(result(source = MeasurementSource.UNMEASURABLE, uptimeMs = 1_000))
        accumulator.add(result(source = MeasurementSource.OUTLIER_REJECTED, uptimeMs = 2_000, mm = 9_999.0))
        val row = accumulator.drain().single()
        assertThat(row.distanceMm).isEqualTo(0.0)
        assertThat(row.rawDeltaYPx).isEqualTo(0)
        assertThat(row.unmeasurableEventCount).isEqualTo(1)
        assertThat(row.rejectedOutlierCount).isEqualTo(1)
        assertThat(row.firstEventTimestamp).isNull()
        assertThat(row.lastEventTimestamp).isNull()
    }

    @Test
    fun excludedAndSupersededEventsAreNotStoredAtAll() {
        assertThat(accumulator.add(result(source = MeasurementSource.EXCLUDED))).isFalse()
        assertThat(accumulator.add(result(source = MeasurementSource.SUPERSEDED_BY_DIRECT))).isFalse()
        assertThat(accumulator.add(result(packageName = null))).isFalse()
        assertThat(accumulator.hasPending).isFalse()
    }

    @Test
    fun theFiftiethEventMakesAFlushDue() {
        repeat(MeasurementConfig.FLUSH_EVENT_COUNT - 1) { assertThat(accumulator.add(result(uptimeMs = 1_000L + it))).isFalse() }
        assertThat(accumulator.add(result(uptimeMs = 5_000))).isTrue()
        accumulator.drain()
        assertThat(accumulator.add(result(uptimeMs = 6_000))).isFalse()
    }

    @Test
    fun anEventOfANewLocalDayMakesAFlushDueAndGoesToItsOwnRow() {
        accumulator.add(result(uptimeMs = 1_000, wallMs = NEXT_MIDNIGHT_UTC_MS - 1_000))
        assertThat(accumulator.add(result(uptimeMs = 2_000, wallMs = NEXT_MIDNIGHT_UTC_MS + 1_000))).isTrue()
        assertThat(accumulator.drain().map { it.date }).containsExactly("2026-09-21", "2026-09-22").inOrder()
    }

    @Test
    fun theLocalDateFollowsTheZone() {
        val prague = ScrollAccumulator { java.time.ZoneId.of("Europe/Prague") }
        prague.add(result(wallMs = NEXT_MIDNIGHT_UTC_MS - 60 * 60_000L)) // 23:00Z = 01:00 CEST
        assertThat(prague.drain().single().date).isEqualTo("2026-09-22")
    }

    @Test
    fun activeScrollTimeCountsGapsUpToFiveSeconds() {
        accumulator.add(result(uptimeMs = 10_000))
        accumulator.add(result(uptimeMs = 15_000)) // 5 s: scrolling
        accumulator.add(result(uptimeMs = 21_000)) // 6 s: a pause
        accumulator.add(result(uptimeMs = 21_500)) // 0.5 s
        assertThat(accumulator.drain().single().activeScrollMs).isEqualTo(5_500)
    }

    @Test
    fun activeScrollTimeIsPerAppAndIgnoresUncountedEvents() {
        accumulator.add(result(uptimeMs = 10_000, packageName = "a"))
        accumulator.add(result(uptimeMs = 11_000, packageName = "b"))
        accumulator.add(result(uptimeMs = 12_000, packageName = "a", source = MeasurementSource.UNMEASURABLE))
        accumulator.add(result(uptimeMs = 13_000, packageName = "a"))
        val rows = accumulator.drain().associateBy { it.packageName }
        assertThat(rows.getValue("a").activeScrollMs).isEqualTo(3_000)
        assertThat(rows.getValue("b").activeScrollMs).isEqualTo(0)
    }

    @Test
    fun activeScrollTimeContinuesAcrossAFlush() {
        accumulator.add(result(uptimeMs = 10_000))
        accumulator.drain()
        accumulator.add(result(uptimeMs = 12_000))
        assertThat(accumulator.drain().single().activeScrollMs).isEqualTo(2_000)
    }

    @Test
    fun theNewestCalibrationVersionIsKept() {
        accumulator.add(result(uptimeMs = 1_000, calibrationVersion = 2))
        accumulator.add(result(uptimeMs = 2_000, calibrationVersion = 1))
        assertThat(accumulator.drain().single().calibrationVersion).isEqualTo(2)
    }

    @Test
    fun restoredDeltasMergeWithNewOnes() {
        accumulator.add(result(uptimeMs = 1_000, mm = 10.0))
        val failed = accumulator.drain()
        accumulator.add(result(uptimeMs = 30_000, mm = 1.0))
        accumulator.restore(failed)
        val row = accumulator.drain().single()
        assertThat(row.distanceMm).isEqualTo(11.0)
        assertThat(row.firstEventTimestamp).isEqualTo(BASE_WALL_MS + 1_000)
        assertThat(row.lastEventTimestamp).isEqualTo(BASE_WALL_MS + 30_000)
    }
}
