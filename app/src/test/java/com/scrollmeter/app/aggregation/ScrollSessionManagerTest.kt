package com.scrollmeter.app.aggregation

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.measurement.APP
import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.MeasurementSource
import org.junit.Test

/** Spec §18: a pause of more than 60 s between counted events of one app starts a new session. */
class ScrollSessionManagerTest {
    private val sessions = ScrollSessionManager()
    private val gap = MeasurementConfig.SCROLL_SESSION_GAP_MS

    @Test
    fun eventsWithinTheGapAreOneSession() {
        sessions.add(result(uptimeMs = 1_000, mm = 10.0))
        sessions.add(result(uptimeMs = 1_000 + gap, mm = 5.0))
        sessions.closeAll()
        val session = sessions.drainClosed().single()
        assertThat(session).isEqualTo(ClosedSession(APP, BASE_WALL_MS + 1_000, BASE_WALL_MS + 1_000 + gap, 15.0, 2))
    }

    @Test
    fun aLongerPauseClosesTheSessionAndStartsANewOne() {
        sessions.add(result(uptimeMs = 1_000))
        sessions.add(result(uptimeMs = 1_001 + gap))
        assertThat(sessions.hasClosed).isTrue()
        assertThat(sessions.drainClosed().single().eventCount).isEqualTo(1)
        sessions.closeAll()
        assertThat(sessions.drainClosed().single().startTimestamp).isEqualTo(BASE_WALL_MS + 1_001 + gap)
    }

    @Test
    fun sessionsArePerApp() {
        sessions.add(result(uptimeMs = 1_000, packageName = "a"))
        sessions.add(result(uptimeMs = 2_000, packageName = "b"))
        sessions.add(result(uptimeMs = 3_000, packageName = "a"))
        sessions.closeAll()
        assertThat(sessions.drainClosed().associate { it.packageName to it.eventCount }).containsExactly("a", 2L, "b", 1L)
    }

    @Test
    fun uncountedEventsNeitherOpenNorExtendASession() {
        sessions.add(result(source = MeasurementSource.UNMEASURABLE, uptimeMs = 1_000))
        sessions.add(result(source = MeasurementSource.OUTLIER_REJECTED, uptimeMs = 2_000))
        sessions.closeAll()
        assertThat(sessions.hasClosed).isFalse()
    }

    @Test
    fun closeIdleClosesOnlySessionsPastTheGap() {
        sessions.add(result(uptimeMs = 1_000, packageName = "old"))
        sessions.add(result(uptimeMs = 50_000, packageName = "recent"))
        sessions.closeIdle(nowUptimeMs = 1_001 + gap)
        assertThat(sessions.drainClosed().map { it.packageName }).containsExactly("old")
    }

    @Test
    fun restoredSessionsComeFirst() {
        sessions.add(result(uptimeMs = 1_000, packageName = "a"))
        sessions.closeAll()
        val failed = sessions.drainClosed()
        sessions.add(result(uptimeMs = 2_000, packageName = "b"))
        sessions.closeAll()
        sessions.restore(failed)
        assertThat(sessions.drainClosed().map { it.packageName }).containsExactly("a", "b").inOrder()
    }
}
