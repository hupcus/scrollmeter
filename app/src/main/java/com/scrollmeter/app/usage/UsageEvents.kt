package com.scrollmeter.app.usage

/** The usage events time in app is built from (ADR-021). Everything else Android reports is ignored. */
enum class UsageEventKind {
    RESUMED,
    PAUSED,
    STOPPED,
    SCREEN_OFF,
    SHUTDOWN,
    STARTUP,
    ;

    companion object {
        /**
         * `UsageEvents.Event` type codes (android-36 sources): ACTIVITY_RESUMED = MOVE_TO_FOREGROUND = 1
         * and ACTIVITY_PAUSED = MOVE_TO_BACKGROUND = 2 (the API 28 names), ACTIVITY_STOPPED = 23 (API 29+),
         * SCREEN_NON_INTERACTIVE = 16, DEVICE_SHUTDOWN = 26, DEVICE_STARTUP = 27.
         */
        fun of(eventType: Int): UsageEventKind? = when (eventType) {
            1 -> RESUMED
            2 -> PAUSED
            23 -> STOPPED
            16 -> SCREEN_OFF
            26 -> SHUTDOWN
            27 -> STARTUP
            else -> null
        }
    }
}

/**
 * One usage event, reduced to what pairing needs. [activity] (the activity's class name) only
 * pairs a RESUMED with its PAUSED / STOPPED in memory — it is never stored.
 */
data class UsageEventSample(
    val timestampMs: Long,
    val packageName: String,
    val activity: String?,
    val kind: UsageEventKind,
)

/** Foreground time of one package on one local day — one `daily_app_usage` row. */
data class UsageDay(
    val date: String,
    val packageName: String,
    val foregroundMs: Long,
    val launchCount: Int,
    val lastEventTimestamp: Long?,
)

/** Reads usage events; the Android implementation is [UsageEventsSource], tests use a list. */
fun interface UsageEventsReader {
    fun read(beginMs: Long, endMs: Long): List<UsageEventSample>
}
