package com.scrollmeter.app.measurement

import kotlin.math.hypot

/**
 * Remembers the last scroll position per (package, windowId, className) and turns a position
 * change into a delta for events that carry none (spec §6 B). The difference is used only when
 * the previous event has the same key, is at most [maxGapMs] old, both positions look valid and
 * the jump is not larger than the outlier limit.
 *
 * `scrollX/Y` is not guaranteed to be monotonic or global — RecyclerView keeps it at 0, lazy
 * lists and WebViews may reset it — so every doubt resolves to "no fallback" (UNMEASURABLE).
 * Not thread-safe: the engine calls it from its single consumer.
 */
class ScrollFallbackTracker(
    private val maxGapMs: Long = MeasurementConfig.FALLBACK_MAX_GAP_MS,
    private val maxKeys: Int = MeasurementConfig.FALLBACK_TRACKER_MAX_KEYS,
) {
    data class Delta(val dxPx: Int, val dyPx: Int)

    private data class Key(val packageName: String?, val windowId: Int, val className: String?)

    private data class Position(val uptimeMs: Long, val scrollX: Int, val scrollY: Int)

    private val lastPositions = object : LinkedHashMap<Key, Position>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Position>?): Boolean = size > maxKeys
    }

    /**
     * Records [sample]'s position and returns the movement since the previous event of the same
     * key, or null when spec §6 B does not allow a fallback.
     */
    fun update(sample: ScrollSample, maxJumpPx: Double): Delta? {
        val key = Key(sample.packageName, sample.windowId, sample.className)
        if (!sample.hasValidPosition()) {
            lastPositions.remove(key)
            return null
        }
        val current = Position(sample.uptimeMs, sample.scrollX, sample.scrollY)
        val previous = lastPositions.put(key, current) ?: return null

        val gapMs = current.uptimeMs - previous.uptimeMs
        if (gapMs < 0 || gapMs > maxGapMs) return null

        val dx = current.scrollX - previous.scrollX
        val dy = current.scrollY - previous.scrollY
        if (dx == 0 && dy == 0) return null
        if (hypot(dx.toDouble(), dy.toDouble()) > maxJumpPx) return null
        return Delta(dx, dy)
    }

    private fun ScrollSample.hasValidPosition(): Boolean =
        scrollX >= 0 && scrollY >= 0 &&
            (maxScrollX <= 0 || scrollX <= maxScrollX) &&
            (maxScrollY <= 0 || scrollY <= maxScrollY)
}
