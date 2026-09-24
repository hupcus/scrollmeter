package com.scrollmeter.app.aggregation

/**
 * The pause between two moments, seen on both clocks. `eventTime` is uptime, which stops in deep
 * sleep: a phone that slept for an hour between two scrolls shows a few seconds of uptime. The wall
 * clock shows the hour but can be set back. The longer of the two is the pause — sleep is never
 * taken for scrolling, and a clock set back never merges what uptime keeps apart.
 */
internal fun pauseMs(uptimeGapMs: Long, wallGapMs: Long): Long = maxOf(uptimeGapMs, wallGapMs)
