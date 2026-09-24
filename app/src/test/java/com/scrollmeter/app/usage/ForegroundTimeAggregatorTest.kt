package com.scrollmeter.app.usage

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.usage.UsageEventKind.PAUSED
import com.scrollmeter.app.usage.UsageEventKind.RESUMED
import com.scrollmeter.app.usage.UsageEventKind.SCREEN_OFF
import com.scrollmeter.app.usage.UsageEventKind.SHUTDOWN
import com.scrollmeter.app.usage.UsageEventKind.STARTUP
import com.scrollmeter.app.usage.UsageEventKind.STOPPED
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** ADR-021, ADR-025, PLAN Phase 3: foreground time per local day from usage event lists. */
class ForegroundTimeAggregatorTest {
    private val zone = ZoneId.of("Europe/Prague")
    private val aggregator = ForegroundTimeAggregator(zone, excludedPackages = setOf(OWN))

    private fun at(local: String): Long = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun ev(local: String, pkg: String, kind: UsageEventKind, activity: String? = "Main") =
        UsageEventSample(at(local), pkg, activity, kind)

    private fun aggregate(vararg events: UsageEventSample, from: String = "2026-09-21T00:00", to: String = "2026-09-23T00:00") =
        aggregator.aggregate(events.toList(), at(from), at(to)).associateBy { it.date to it.packageName }

    private fun Map<Pair<String, String>, UsageDay>.minutes(date: String, pkg: String = A): Double? =
        get(date to pkg)?.foregroundMs?.div(60_000.0)

    @Test
    fun oneVisit() {
        val days = aggregate(ev("2026-09-21T10:00", A, RESUMED), ev("2026-09-21T10:20", A, PAUSED))
        assertThat(days.minutes("2026-09-21")).isEqualTo(20.0)
        assertThat(days.getValue("2026-09-21" to A).launchCount).isEqualTo(1)
        assertThat(days.getValue("2026-09-21" to A).lastEventTimestamp).isEqualTo(at("2026-09-21T10:20"))
    }

    @Test
    fun twoResumedActivitiesOfOneAppCountOnce() {
        val days = aggregate(
            ev("2026-09-21T10:00", A, RESUMED, "Main"),
            ev("2026-09-21T10:05", A, RESUMED, "Detail"),
            ev("2026-09-21T10:10", A, PAUSED, "Detail"),
            ev("2026-09-21T10:20", A, PAUSED, "Main"),
        )
        assertThat(days.minutes("2026-09-21")).isEqualTo(20.0)
        assertThat(days.getValue("2026-09-21" to A).launchCount).isEqualTo(1)
    }

    @Test
    fun movingBetweenOwnActivitiesWithinTheGraceIsOneVisit() {
        val days = aggregate(
            ev("2026-09-21T10:00", A, RESUMED, "Main"),
            ev("2026-09-21T10:10:00", A, PAUSED, "Main"),
            UsageEventSample(at("2026-09-21T10:10:00") + 500, A, "Detail", RESUMED),
            ev("2026-09-21T10:30", A, PAUSED, "Detail"),
        )
        assertThat(days.minutes("2026-09-21")).isEqualTo(30.0)
        assertThat(days.getValue("2026-09-21" to A).launchCount).isEqualTo(1)
    }

    @Test
    fun comingBackAfterTheGraceIsASecondVisitAndTheGapIsNotCounted() {
        val days = aggregate(
            ev("2026-09-21T10:00", A, RESUMED),
            ev("2026-09-21T10:10:00", A, PAUSED),
            ev("2026-09-21T10:10:05", A, RESUMED),
            ev("2026-09-21T10:20:05", A, PAUSED),
        )
        assertThat(days.minutes("2026-09-21")).isEqualTo(20.0)
        assertThat(days.getValue("2026-09-21" to A).launchCount).isEqualTo(2)
    }

    @Test
    fun anotherAppComingUpEndsTheLeavingAppAtItsPause() {
        val days = aggregate(
            ev("2026-09-21T10:00", A, RESUMED),
            ev("2026-09-21T10:10:00", A, PAUSED),
            UsageEventSample(at("2026-09-21T10:10:00") + 300, B, "Main", RESUMED),
            ev("2026-09-21T10:15", B, PAUSED),
        )
        assertThat(days.minutes("2026-09-21", A)).isEqualTo(10.0)
        assertThat(days.getValue("2026-09-21" to B).foregroundMs).isEqualTo(5 * 60_000L - 300)
    }

    @Test
    fun pausedThenStoppedEndsAtThePause() {
        val days = aggregate(
            ev("2026-09-21T10:00", A, RESUMED),
            ev("2026-09-21T10:04", A, PAUSED),
            ev("2026-09-21T10:05", A, STOPPED),
        )
        assertThat(days.minutes("2026-09-21")).isEqualTo(4.0)
    }

    @Test
    fun stoppedAloneAlsoEndsTheVisit() {
        val days = aggregate(ev("2026-09-21T10:00", A, RESUMED), ev("2026-09-21T10:05", A, STOPPED))
        assertThat(days.minutes("2026-09-21")).isEqualTo(5.0)
    }

    @Test
    fun midnightSplitsTheVisitAndTheLaunchStaysOnTheFirstDay() {
        val days = aggregate(ev("2026-09-21T23:50", A, RESUMED), ev("2026-09-22T00:10", A, PAUSED))
        assertThat(days.minutes("2026-09-21")).isEqualTo(10.0)
        assertThat(days.minutes("2026-09-22")).isEqualTo(10.0)
        assertThat(days.getValue("2026-09-21" to A).launchCount).isEqualTo(1)
        assertThat(days.getValue("2026-09-22" to A).launchCount).isEqualTo(0)
    }

    @Test
    fun dstDaysHave23And25Hours() {
        val spring = aggregate(
            ev("2026-03-28T23:00", A, RESUMED),
            ev("2026-03-30T01:00", A, PAUSED),
            from = "2026-03-28T00:00",
            to = "2026-03-31T00:00",
        )
        assertThat(spring.minutes("2026-03-28")).isEqualTo(60.0)
        assertThat(spring.minutes("2026-03-29")).isEqualTo(23 * 60.0)
        assertThat(spring.minutes("2026-03-30")).isEqualTo(60.0)

        val autumn = aggregate(
            ev("2026-10-24T23:00", A, RESUMED),
            ev("2026-10-26T01:00", A, PAUSED),
            from = "2026-10-24T00:00",
            to = "2026-10-27T00:00",
        )
        assertThat(autumn.minutes("2026-10-25")).isEqualTo(25 * 60.0)
    }

    @Test
    fun screenOffClosesEverythingAndALatePauseIsIgnored() {
        val days = aggregate(
            ev("2026-09-21T10:00", A, RESUMED),
            ev("2026-09-21T10:15", A, SCREEN_OFF),
            ev("2026-09-21T10:30", A, PAUSED),
        )
        assertThat(days.minutes("2026-09-21")).isEqualTo(15.0)
    }

    @Test
    fun shutdownClosesAtTheShutdown() {
        val days = aggregate(
            ev("2026-09-21T10:00", A, RESUMED),
            ev("2026-09-21T10:30", ANDROID, SHUTDOWN),
            ev("2026-09-21T10:32", ANDROID, STARTUP),
        )
        assertThat(days.minutes("2026-09-21")).isEqualTo(30.0)
    }

    @Test
    fun startupWithoutShutdownDiscardsTheOpenVisit() {
        val days = aggregate(ev("2026-09-21T10:00", A, RESUMED), ev("2026-09-21T11:00", ANDROID, STARTUP))
        assertThat(days.getValue("2026-09-21" to A).foregroundMs).isEqualTo(0)
        assertThat(days.getValue("2026-09-21" to A).launchCount).isEqualTo(1)
    }

    @Test
    fun anUnpairedPauseIsIgnored() {
        assertThat(aggregate(ev("2026-09-21T10:00", A, PAUSED))).isEmpty()
    }

    @Test
    fun anOpenVisitEndsAtTheWindowEnd() {
        val days = aggregate(ev("2026-09-21T10:00", A, RESUMED), to = "2026-09-21T10:30")
        assertThat(days.minutes("2026-09-21")).isEqualTo(30.0)
    }

    @Test
    fun aVisitStartedBeforeTheWindowCountsOnlyInsideItWithoutALaunch() {
        val days = aggregate(ev("2026-09-20T23:00", A, RESUMED), ev("2026-09-21T01:00", A, PAUSED))
        assertThat(days.minutes("2026-09-21")).isEqualTo(60.0)
        assertThat(days.getValue("2026-09-21" to A).launchCount).isEqualTo(0)
        assertThat(days.keys).doesNotContain("2026-09-20" to A)
    }

    @Test
    fun splitScreenCountsBothApps() {
        val days = aggregate(
            ev("2026-09-21T10:00", A, RESUMED),
            ev("2026-09-21T10:00:01", B, RESUMED),
            ev("2026-09-21T10:20", A, PAUSED),
            ev("2026-09-21T10:20", B, PAUSED),
        )
        assertThat(days.minutes("2026-09-21", A)).isEqualTo(20.0)
        assertThat(days.getValue("2026-09-21" to B).foregroundMs).isEqualTo(20 * 60_000L - 1_000)
    }

    @Test
    fun theOwnPackageIsExcluded() {
        val days = aggregate(ev("2026-09-21T10:00", OWN, RESUMED), ev("2026-09-21T10:20", OWN, PAUSED))
        assertThat(days).isEmpty()
    }

    @Test
    fun api28EventCodesMapToTheKinds() {
        assertThat(UsageEventKind.of(1)).isEqualTo(RESUMED) // MOVE_TO_FOREGROUND on API 28
        assertThat(UsageEventKind.of(2)).isEqualTo(PAUSED) // MOVE_TO_BACKGROUND on API 28
        assertThat(UsageEventKind.of(23)).isEqualTo(STOPPED)
        assertThat(UsageEventKind.of(16)).isEqualTo(SCREEN_OFF)
        assertThat(UsageEventKind.of(26)).isEqualTo(SHUTDOWN)
        assertThat(UsageEventKind.of(27)).isEqualTo(STARTUP)
        assertThat(UsageEventKind.of(5)).isNull() // CONFIGURATION_CHANGE
        assertThat(UsageEventKind.of(15)).isNull() // SCREEN_INTERACTIVE
    }

    private companion object {
        const val A = "com.example.feed"
        const val B = "com.example.video"
        const val OWN = "com.scrollmeter.app.debug"
        const val ANDROID = "android"
    }
}
