package com.scrollmeter.app.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * The only class that touches `android.app.usage` (ADR-021). Reads the system's usage events and
 * keeps timestamp, package, activity class (for pairing only) and the kind — nothing else.
 * Needs Usage access; without it the system returns no events.
 */
class UsageEventsSource(private val context: Context) : UsageEventsReader {
    override fun read(beginMs: Long, endMs: Long): List<UsageEventSample> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = manager.queryEvents(beginMs, endMs) ?: return emptyList()
        val out = ArrayList<UsageEventSample>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(event)) break
            val kind = UsageEventKind.of(event.eventType) ?: continue
            out += UsageEventSample(event.timeStamp, event.packageName.orEmpty(), event.className, kind)
        }
        return out
    }
}
