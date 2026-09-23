package com.scrollmeter.app.measurement

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Spec §6 B: position difference only for the same key, a short gap, valid values and a sane jump. */
class ScrollFallbackTrackerTest {
    private val tracker = ScrollFallbackTracker(maxGapMs = 2_000, maxKeys = 4)
    private val limit = TestPhone.geometry.maxEventDistancePx

    @Test
    fun firstEventOfAKeyHasNoFallback() {
        assertThat(tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0), limit)).isNull()
    }

    @Test
    fun sameKeyShortGapGivesTheDifference() {
        tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0), limit)
        val delta = tracker.update(positionOnlySample(scrollY = 460, uptimeMs = 120), limit)
        assertThat(delta).isEqualTo(ScrollFallbackTracker.Delta(0, 360))
    }

    @Test
    fun scrollingBackGivesANegativeDifference() {
        tracker.update(positionOnlySample(scrollY = 900, uptimeMs = 0), limit)
        assertThat(tracker.update(positionOnlySample(scrollY = 400, uptimeMs = 100), limit))
            .isEqualTo(ScrollFallbackTracker.Delta(0, -500))
    }

    @Test
    fun differentWindowHasNoFallback() {
        tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0, windowId = 1), limit)
        assertThat(tracker.update(positionOnlySample(scrollY = 400, uptimeMs = 100, windowId = 2), limit)).isNull()
    }

    @Test
    fun differentPackageHasNoFallback() {
        tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0, packageName = "a.b"), limit)
        assertThat(tracker.update(positionOnlySample(scrollY = 400, uptimeMs = 100, packageName = "c.d"), limit)).isNull()
    }

    @Test
    fun longGapHasNoFallback() {
        tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0), limit)
        assertThat(tracker.update(positionOnlySample(scrollY = 400, uptimeMs = 2_001), limit)).isNull()
    }

    @Test
    fun gapAtTheLimitIsStillAllowed() {
        tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0), limit)
        assertThat(tracker.update(positionOnlySample(scrollY = 400, uptimeMs = 2_000), limit)).isNotNull()
    }

    @Test
    fun nonsenseJumpHasNoFallback() {
        tracker.update(positionOnlySample(scrollY = 0, uptimeMs = 0), limit)
        assertThat(tracker.update(positionOnlySample(scrollY = 50_000, uptimeMs = 100), limit)).isNull()
    }

    @Test
    fun invalidPositionHasNoFallbackAndForgetsTheKey() {
        tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0), limit)
        assertThat(tracker.update(positionOnlySample(scrollY = -5, uptimeMs = 50), limit)).isNull()
        // The key was forgotten, so the next valid event starts over instead of diffing against 100.
        assertThat(tracker.update(positionOnlySample(scrollY = 300, uptimeMs = 100), limit)).isNull()
    }

    @Test
    fun positionAboveMaxScrollIsInvalid() {
        tracker.update(sample(dx = -1, dy = -1, scrollY = 100, maxScrollY = 1_000, uptimeMs = 0), limit)
        assertThat(tracker.update(sample(dx = -1, dy = -1, scrollY = 1_200, maxScrollY = 1_000, uptimeMs = 50), limit))
            .isNull()
    }

    @Test
    fun unchangedPositionHasNoFallback() {
        tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0), limit)
        assertThat(tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 50), limit)).isNull()
    }

    @Test
    fun leastRecentlyUsedKeysAreForgotten() {
        (1..5).forEach { window -> tracker.update(positionOnlySample(scrollY = 100, uptimeMs = 0, windowId = window), limit) }
        // maxKeys = 4: window 1 was evicted, window 5 is remembered.
        assertThat(tracker.update(positionOnlySample(scrollY = 300, uptimeMs = 10, windowId = 1), limit)).isNull()
        assertThat(tracker.update(positionOnlySample(scrollY = 300, uptimeMs = 10, windowId = 5), limit)).isNotNull()
    }

    /** Compose lazy list (foundation 1.11.4): position = index × 500 + offset, max = position + 100. */
    private fun composeLazy(position: Int, uptimeMs: Long, atEnd: Boolean = false) = sample(
        dx = -1, dy = -1, className = "android.view.View", windowId = -1, uptimeMs = uptimeMs,
        scrollY = position, maxScrollY = if (atEnd) position else position + 100,
    )

    @Test
    fun composeLazyEstimateIsNeverUsed() {
        assertThat(tracker.update(composeLazy(0, uptimeMs = 0), limit)).isNull()
        assertThat(tracker.update(composeLazy(561, uptimeMs = 100), limit)).isNull()
        assertThat(tracker.update(composeLazy(1_433, uptimeMs = 200), limit)).isNull()
        // At the end of the list max == position; the key stays marked as an estimate.
        assertThat(tracker.update(composeLazy(1_500, uptimeMs = 300, atEnd = true), limit)).isNull()
    }

    @Test
    fun composeLazyEstimateIsCaughtWhenTheListStartsAtItsEnd() {
        assertThat(tracker.update(composeLazy(1_500, uptimeMs = 0, atEnd = true), limit)).isNull()
        // Scrolling back: the max moved with the position — an estimate, not 1 500 − 1 100 px.
        assertThat(tracker.update(composeLazy(1_100, uptimeMs = 100), limit)).isNull()
        assertThat(tracker.update(composeLazy(600, uptimeMs = 200), limit)).isNull()
    }

    @Test
    fun exactColumnPassing100PxBeforeItsEndStaysMeasured() {
        val column = { y: Int, t: Long -> sample(dx = -1, dy = -1, className = "android.view.View", windowId = -1, uptimeMs = t, scrollY = y, maxScrollY = 40_000) }
        tracker.update(column(39_500, 0), limit)
        assertThat(tracker.update(column(39_900, 100), limit)).isEqualTo(ScrollFallbackTracker.Delta(0, 400))
        assertThat(tracker.update(column(40_000, 200), limit)).isEqualTo(ScrollFallbackTracker.Delta(0, 100))
        assertThat(tracker.update(column(39_700, 300), limit)).isEqualTo(ScrollFallbackTracker.Delta(0, -300))
    }

    @Test
    fun exactComposeColumnPositionsAreUsed() {
        // verticalScroll(ScrollState): real pixels and a fixed max.
        val column = { y: Int, t: Long -> sample(dx = -1, dy = -1, className = "android.view.View", windowId = -1, uptimeMs = t, scrollY = y, maxScrollY = 40_000) }
        tracker.update(column(0, 0), limit)
        assertThat(tracker.update(column(476, 100), limit)).isEqualTo(ScrollFallbackTracker.Delta(0, 476))
    }

    @Test
    fun horizontalAndVerticalScrollablesOfOneAppAreNotDiffedAgainstEachOther() {
        val row = { x: Int, t: Long -> sample(dx = -1, dy = -1, className = "android.view.View", windowId = -1, uptimeMs = t, scrollX = x, maxScrollX = 20_000) }
        val column = { y: Int, t: Long -> sample(dx = -1, dy = -1, className = "android.view.View", windowId = -1, uptimeMs = t, scrollY = y, maxScrollY = 40_000) }
        tracker.update(row(3_000, 0), limit)
        assertThat(tracker.update(column(200, 50), limit)).isNull() // first event of the vertical key
        assertThat(tracker.update(row(3_300, 100), limit)).isEqualTo(ScrollFallbackTracker.Delta(300, 0))
        assertThat(tracker.update(column(500, 150), limit)).isEqualTo(ScrollFallbackTracker.Delta(0, 300))
    }
}
