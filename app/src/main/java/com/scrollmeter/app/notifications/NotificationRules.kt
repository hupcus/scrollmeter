package com.scrollmeter.app.notifications

import com.scrollmeter.app.settings.Settings
import java.time.LocalDate

/**
 * Spec §26, §44, ADR-036: the two optional notifications. Nothing celebrates scrolling more — the
 * "record" notification is gone and the goal became a limit.
 */
enum class NotificationKind { LIMIT, SUMMARY }

/** The day each kind was last posted — at most one per kind and day (spec §26, ADR-031). */
interface NotificationState {
    suspend fun lastPosted(kind: NotificationKind): LocalDate?
    suspend fun setLastPosted(kind: NotificationKind, date: LocalDate)
}

/** One notification to post; [distanceMm] is the number it shows. */
data class Notice(val kind: NotificationKind, val distanceMm: Double)

/** What a check knows: today's and yesterday's totals, excluded apps left out; [limitMm] 0 = no limit. */
data class NotificationFacts(
    val today: LocalDate,
    val todayMm: Double,
    val limitMm: Double,
    val yesterdayMm: Double,
)

/**
 * Pure Kotlin (ADR-031, ADR-036). Never per event: the watcher asks after a flush, and each kind is
 * posted at most once a day.
 * - LIMIT: today went over the daily limit (none without a limit).
 * - SUMMARY: yesterday's total, with the first check of a new day (i.e. the first scroll — never at
 *   midnight, no scheduled job); nothing when yesterday was empty.
 */
object NotificationRules {
    fun enabled(settings: Settings): Set<NotificationKind> = buildSet {
        if (settings.notifyLimit) add(NotificationKind.LIMIT)
        if (settings.notifySummary) add(NotificationKind.SUMMARY)
    }

    /** [due] = the enabled kinds not yet posted today. */
    fun decide(facts: NotificationFacts, due: Set<NotificationKind>): List<Notice> = buildList {
        if (NotificationKind.LIMIT in due && facts.limitMm > 0 && facts.todayMm >= facts.limitMm) {
            add(Notice(NotificationKind.LIMIT, facts.limitMm))
        }
        if (NotificationKind.SUMMARY in due && facts.yesterdayMm > 0) {
            add(Notice(NotificationKind.SUMMARY, facts.yesterdayMm))
        }
    }
}
