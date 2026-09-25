package com.scrollmeter.app.notifications

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.settings.Settings
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Spec §26, ADR-036: optional, never per event, at most once a day per kind; a limit, no record. */
class NotificationRulesTest {
    private val today = LocalDate.parse("2026-09-24")
    private val all = NotificationKind.entries.toSet()

    private fun facts(todayMm: Double, limit: Double = 500_000.0, yesterday: Double = 300_000.0) =
        NotificationFacts(today, todayMm, limitMm = limit, yesterdayMm = yesterday)

    @Test
    fun limitAndSummaryFollowTheirThresholds() {
        assertThat(NotificationRules.decide(facts(499_999.0), all).map { it.kind }).containsExactly(NotificationKind.SUMMARY)
        assertThat(NotificationRules.decide(facts(500_000.0), all)).containsExactly(
            Notice(NotificationKind.LIMIT, 500_000.0), Notice(NotificationKind.SUMMARY, 300_000.0),
        ).inOrder()
    }

    @Test
    fun noLimitNoticeWithoutALimitAndNoEmptySummary() {
        assertThat(NotificationRules.decide(facts(900_000.0, limit = 0.0), setOf(NotificationKind.LIMIT))).isEmpty()
        assertThat(NotificationRules.decide(facts(900_000.0, yesterday = 0.0), setOf(NotificationKind.SUMMARY))).isEmpty()
    }

    @Test
    fun theSwitchesPickTheKinds() {
        assertThat(NotificationRules.enabled(Settings())).isEmpty()
        assertThat(NotificationRules.enabled(Settings(notifyLimit = true, notifySummary = true))).containsExactly(NotificationKind.LIMIT, NotificationKind.SUMMARY)
    }

    @Test
    fun theWatcherPostsEachKindOnceADayAndSkipsWithoutPermission() = runTest {
        val posted = mutableListOf<NotificationKind>()
        var permitted = true
        var day = today
        val state = object : NotificationState {
            val last = HashMap<NotificationKind, LocalDate>()
            override suspend fun lastPosted(kind: NotificationKind) = last[kind]
            override suspend fun setLastPosted(kind: NotificationKind, date: LocalDate) { last[kind] = date }
        }
        var queries = 0
        val watcher = NotificationWatcher(
            settings = { Settings(notifyLimit = true) },
            facts = { d, _ -> queries++; NotificationFacts(d, 600_000.0, 500_000.0, 0.0) },
            state = state,
            poster = object : NotificationPoster {
                override fun canPost() = permitted
                override fun post(notice: Notice, settings: Settings) { posted += notice.kind }
            },
            today = { day },
        )
        watcher.check()
        watcher.check()
        assertThat(posted).containsExactly(NotificationKind.LIMIT)
        assertThat(queries).isEqualTo(1) // posted today: the second check reads nothing

        day = today.plusDays(1)
        permitted = false
        watcher.check()
        assertThat(posted).hasSize(1)
        permitted = true
        watcher.check()
        assertThat(posted).containsExactly(NotificationKind.LIMIT, NotificationKind.LIMIT)
    }

    /** A delete between reading the facts and posting: the notice would describe deleted data (ADR-031). */
    @Test
    fun theWatcherPostsNothingWhenTheDataWasErasedWhileItReadTheFacts() = runTest {
        val posted = mutableListOf<NotificationKind>()
        val state = object : NotificationState {
            val last = HashMap<NotificationKind, LocalDate>()
            override suspend fun lastPosted(kind: NotificationKind) = last[kind]
            override suspend fun setLastPosted(kind: NotificationKind, date: LocalDate) { last[kind] = date }
        }
        var epoch = 0L
        val watcher = NotificationWatcher(
            settings = { Settings(notifyLimit = true) },
            facts = { d, _ -> epoch++; NotificationFacts(d, 600_000.0, 500_000.0, 0.0) },
            state = state,
            poster = object : NotificationPoster {
                override fun canPost() = true
                override fun post(notice: Notice, settings: Settings) { posted += notice.kind }
            },
            today = { today },
            epoch = { epoch },
        )
        watcher.check()
        assertThat(posted).isEmpty()
        assertThat(state.last).isEmpty()
    }

    /** Posting waits for an erase that holds the write lock, and then sees its epoch. */
    @Test
    fun theWatcherPostsUnderTheWriteLock() = runTest {
        val lock = kotlinx.coroutines.sync.Mutex()
        var lockedWhilePosting: Boolean? = null
        val watcher = NotificationWatcher(
            settings = { Settings(notifyLimit = true) },
            facts = { d, _ -> NotificationFacts(d, 600_000.0, 500_000.0, 0.0) },
            state = object : NotificationState {
                override suspend fun lastPosted(kind: NotificationKind): LocalDate? = null
                override suspend fun setLastPosted(kind: NotificationKind, date: LocalDate) = Unit
            },
            poster = object : NotificationPoster {
                override fun canPost() = true
                override fun post(notice: Notice, settings: Settings) { lockedWhilePosting = lock.isLocked }
            },
            today = { today },
            postLock = lock,
        )
        watcher.check()
        assertThat(lockedWhilePosting).isTrue()
        assertThat(lock.isLocked).isFalse()
    }
}
