package com.scrollmeter.app.accessibility

import android.view.accessibility.AccessibilityEvent
import com.scrollmeter.app.measurement.ScrollSample

/**
 * The only class that touches [AccessibilityEvent]. It copies numbers and two identifiers
 * (package and view class name) into a [ScrollSample] and nothing else: no text, no content
 * description, no source node, no records (spec §5, §6; guarded by `PolicyGuardTest`).
 */
object AccessibilityEventParser {
    fun parse(event: AccessibilityEvent, wallTimeMs: Long): ScrollSample? {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return null
        return ScrollSample(
            uptimeMs = event.eventTime,
            wallTimeMs = wallTimeMs,
            packageName = event.packageName?.toString(),
            windowId = event.windowId,
            className = event.className?.toString(),
            deltaX = event.scrollDeltaX,
            deltaY = event.scrollDeltaY,
            scrollX = event.scrollX,
            scrollY = event.scrollY,
            maxScrollX = event.maxScrollX,
            maxScrollY = event.maxScrollY,
        )
    }
}
