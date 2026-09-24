package com.scrollmeter.app.notifications

import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.settings.Settings
import java.time.LocalDate

/** Spec §26, §44: the three optional notifications. */
enum class NotificationKind { GOAL, RECORD, SUMMARY }

/** The day each kind was last posted — at most one per kind and day (spec §26, ADR-031). */
interface NotificationState {
    suspend fun lastPosted(kind: NotificationKind): LocalDate?
    suspend fun setLastPosted(kind: NotificationKind, date: LocalDate)
}

/** One notification to post; [distanceMm] is the number it shows. */
data class Notice(val kind: NotificationKind, val distanceMm: Double)

/**
 * What a check knows. [previousBestMm] and [priorMeasuredDays] cover the days before [today] with
 * a distance above 0; [yesterdayMm] is yesterday's total. Excluded apps are left out everywhere.
 */
data class NotificationFacts(
    val today: LocalDate,
    val todayMm: Double,
    val goalMm: Double,
    val previousBestMm: Double,
    val priorMeasuredDays: Int,
    val yesterdayMm: Double,
)

/**
 * Pure Kotlin (ADR-031). Never per event: the watcher asks after a flush, and each kind is posted
 * at most once a day.
 * - GOAL: today reached the daily goal.
 * - RECORD: today passed the best earlier day — only after [MeasurementConfig.RECORD_MIN_PRIOR_DAYS] measured days
 *   (the second day of use is not a record worth a notification) and above [MeasurementConfig.RECORD_MIN_MM].
 * - SUMMARY: yesterday's total, with the first check of a new day (i.e. the first scroll — never at
 *   midnight, no scheduled job); nothing when yesterday was empty.
 */
object NotificationRules {
    fun enabled(settings: Settings): Set<NotificationKind> = buildSet {
        if (settings.notifyGoal) add(NotificationKind.GOAL)
        if (settings.notifyRecord) add(NotificationKind.RECORD)
        if (settings.notifySummary) add(NotificationKind.SUMMARY)
    }

    /** [due] = the enabled kinds not yet posted today. */
    fun decide(facts: NotificationFacts, due: Set<NotificationKind>): List<Notice> = buildList {
        if (NotificationKind.GOAL in due && facts.goalMm > 0 && facts.todayMm >= facts.goalMm) {
            add(Notice(NotificationKind.GOAL, facts.goalMm))
        }
        if (NotificationKind.RECORD in due && facts.priorMeasuredDays >= MeasurementConfig.RECORD_MIN_PRIOR_DAYS &&
            facts.todayMm > facts.previousBestMm && facts.todayMm >= MeasurementConfig.RECORD_MIN_MM
        ) {
            add(Notice(NotificationKind.RECORD, facts.todayMm))
        }
        if (NotificationKind.SUMMARY in due && facts.yesterdayMm > 0) {
            add(Notice(NotificationKind.SUMMARY, facts.yesterdayMm))
        }
    }
}
