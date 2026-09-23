package com.scrollmeter.app.measurement

/**
 * One `TYPE_VIEW_SCROLLED` event reduced to primitives. Nothing textual from the event ever gets
 * here: no text, content description, source node or URL (spec §5, §6).
 *
 * @property uptimeMs `AccessibilityEvent.eventTime` (uptime clock) — used for gaps and dedupe.
 * @property wallTimeMs wall clock at receipt — used for display, CSV and calendar dates.
 * @property deltaX, deltaY `scrollDeltaX/Y`; (-1, -1) means the app set none (see [MeasurementConfig.UNDEFINED_SCROLL_DELTA]).
 */
data class ScrollSample(
    val uptimeMs: Long,
    val wallTimeMs: Long,
    val packageName: String?,
    val windowId: Int,
    val className: String?,
    val deltaX: Int,
    val deltaY: Int,
    val scrollX: Int,
    val scrollY: Int,
    val maxScrollX: Int,
    val maxScrollY: Int,
)
