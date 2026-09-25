package com.scrollmeter.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Spec §17 Settings in DataStore on the JVM; unreadable values fall back to defaults (spec §61). */
class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "settings.preferences_pb") })
    }
    private val repository by lazy { SettingsRepository(dataStore) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun startsWithDefaults() = runBlocking {
        assertThat(repository.settings.first()).isEqualTo(Settings())
        assertThat(repository.lastSyncMs()).isNull()
    }

    @Test
    fun everySettingRoundTrips() = runBlocking {
        repository.setDailyLimitMm(1_000_000.0)
        repository.setExcluded("a", true)
        repository.setExcluded("b", true)
        repository.setExcluded("a", false)
        repository.setUnitPreference(UnitPreference.KILOMETRES)
        repository.setTheme(ThemePreference.DARK)
        repository.setShowComparisons(false)
        repository.setOnboardingCompleted(true)
        repository.setPrivacyDisclosureAccepted(true)
        repository.setUsageTimeCardDismissed(true)
        repository.setLastSyncMs(42)
        assertThat(repository.settings.first()).isEqualTo(
            Settings(1_000_000.0, setOf("b"), UnitPreference.KILOMETRES, ThemePreference.DARK, false, true, true, true),
        )
        assertThat(repository.lastSyncMs()).isEqualTo(42)
    }

    @Test
    fun aLimitOutsideTheRangeIsClamped() = runBlocking {
        repository.setDailyLimitMm(1.0)
        assertThat(repository.settings.first().dailyLimitMm).isEqualTo(Settings.LIMIT_RANGE_MM.start)
    }

    /** ADR-036: "Bez limitu" is stored as 0 and read back as 0, not as the default. */
    @Test
    fun noLimitIsStoredAsZero() = runBlocking {
        repository.setDailyLimitMm(0.0)
        assertThat(repository.settings.first().dailyLimitMm).isEqualTo(Settings.NO_LIMIT)
        repository.setDailyLimitMm(-3.0)
        assertThat(repository.settings.first().dailyLimitMm).isEqualTo(Settings.NO_LIMIT)
    }

    /** ADR-036: the former goal's keys carry over — the value becomes the limit, the switch stays on. */
    @Test
    fun theFormerGoalCarriesOverAsTheLimit() = runBlocking {
        dataStore.edit {
            it[androidx.datastore.preferences.core.doublePreferencesKey("daily_goal_mm")] = 250_000.0
            it[androidx.datastore.preferences.core.booleanPreferencesKey("notify_goal")] = true
            it[androidx.datastore.preferences.core.booleanPreferencesKey("notify_record")] = true
            it[androidx.datastore.preferences.core.stringPreferencesKey("notified_goal_date")] = "2026-09-25"
        }
        val settings = repository.settings.first()
        assertThat(settings.dailyLimitMm).isEqualTo(250_000.0)
        assertThat(settings.notifyLimit).isTrue()
        assertThat(repository.lastPosted(com.scrollmeter.app.notifications.NotificationKind.LIMIT)).isEqualTo(java.time.LocalDate.parse("2026-09-25"))
    }

    @Test
    fun unreadableValuesFallBackToDefaults() = runBlocking {
        dataStore.edit {
            it[SettingsRepository.UNIT] = "LIGHT_YEARS"
            it[SettingsRepository.THEME] = "NEON"
            it[SettingsRepository.DAILY_LIMIT_MM] = -5.0
        }
        assertThat(repository.settings.first()).isEqualTo(Settings())
    }

    @Test
    fun clearResetsEverything() = runBlocking {
        repository.setOnboardingCompleted(true)
        repository.setLastSyncMs(1)
        repository.clear()
        assertThat(repository.settings.first()).isEqualTo(Settings())
        assertThat(repository.lastSyncMs()).isNull()
    }
}
