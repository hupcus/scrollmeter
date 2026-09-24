package com.scrollmeter.app.notifications

import com.scrollmeter.app.settings.Settings
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Posts notices; Android's side (channels, permission) lives in the implementation. */
interface NotificationPoster {
    /** False without POST_NOTIFICATIONS (API 33+) or with the app's notifications switched off. */
    fun canPost(): Boolean

    fun post(notice: Notice, settings: Settings)
}

/**
 * Called after every flush of the accessibility service (ADR-031). Cheap when there is nothing to
 * do: no enabled kind, no permission, or everything already posted today ends it before any query.
 * Checks never overlap. Pure Kotlin — the facts come from a function (the repository in the app).
 */
class NotificationWatcher(
    private val settings: suspend () -> Settings,
    private val facts: suspend (today: LocalDate, settings: Settings) -> NotificationFacts,
    private val state: NotificationState,
    private val poster: NotificationPoster,
    private val today: () -> LocalDate = LocalDate::now,
) {
    private val mutex = Mutex()

    suspend fun check() = mutex.withLock {
        val current = settings()
        val enabled = NotificationRules.enabled(current)
        if (enabled.isEmpty() || !poster.canPost()) return@withLock
        val day = today()
        val due = enabled.filterTo(HashSet()) { state.lastPosted(it) != day }
        if (due.isEmpty()) return@withLock
        NotificationRules.decide(facts(day, current), due).forEach { notice ->
            poster.post(notice, current)
            state.setLastPosted(notice.kind, day)
        }
    }
}
