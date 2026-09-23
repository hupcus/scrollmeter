package com.scrollmeter.app.accessibility

import android.view.accessibility.AccessibilityEvent
import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.ScrollSample
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real framework `AccessibilityEvent` (Robolectric, API 34 — the test phone's level). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityEventParserTest {
    private fun scrollEvent() = AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
        packageName = "com.example.feed"
        className = "androidx.recyclerview.widget.RecyclerView"
        eventTime = 12_345L
        scrollDeltaX = 3
        scrollDeltaY = -240
        scrollX = 10
        scrollY = 900
        maxScrollX = 20
        maxScrollY = 5_000
    }

    @Test
    fun copiesOnlyPrimitivesAndIdentifiers() {
        val sample = AccessibilityEventParser.parse(scrollEvent(), wallTimeMs = 99L)
        assertThat(sample).isEqualTo(
            ScrollSample(
                uptimeMs = 12_345L,
                wallTimeMs = 99L,
                packageName = "com.example.feed",
                windowId = -1,
                className = "androidx.recyclerview.widget.RecyclerView",
                deltaX = 3,
                deltaY = -240,
                scrollX = 10,
                scrollY = 900,
                maxScrollX = 20,
                maxScrollY = 5_000,
            ),
        )
    }

    @Test
    fun textAndContentDescriptionNeverReachTheSample() {
        val event = scrollEvent().apply {
            text.add("secret message")
            contentDescription = "private"
        }
        val sample = AccessibilityEventParser.parse(event, wallTimeMs = 0L)!!
        assertThat(sample.toString()).doesNotContain("secret")
        assertThat(sample.toString()).doesNotContain("private")
    }

    @Test
    fun nonScrollEventsAreIgnored() {
        val click = AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED).apply { packageName = "a.b" }
        assertThat(AccessibilityEventParser.parse(click, 0L)).isNull()
    }

    /** Pins the framework fact the engine relies on: deltas an app never set read back as -1. */
    @Test
    fun unsetDeltasAreUndefinedMinusOne() {
        val event = AccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply { packageName = "a.b" }
        val sample = AccessibilityEventParser.parse(event, 0L)!!
        assertThat(sample.deltaX).isEqualTo(MeasurementConfig.UNDEFINED_SCROLL_DELTA)
        assertThat(sample.deltaY).isEqualTo(MeasurementConfig.UNDEFINED_SCROLL_DELTA)
    }
}
