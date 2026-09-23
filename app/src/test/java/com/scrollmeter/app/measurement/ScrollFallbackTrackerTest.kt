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
}
