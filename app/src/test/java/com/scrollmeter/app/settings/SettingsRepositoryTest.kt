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
        repository.setDailyGoalMm(1_000_000.0)
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
    fun aGoalOutsideTheRangeIsClamped() = runBlocking {
        repository.setDailyGoalMm(1.0)
        assertThat(repository.settings.first().dailyGoalMm).isEqualTo(Settings.GOAL_RANGE_MM.start)
    }

    @Test
    fun unreadableValuesFallBackToDefaults() = runBlocking {
        dataStore.edit {
            it[SettingsRepository.UNIT] = "LIGHT_YEARS"
            it[SettingsRepository.THEME] = "NEON"
            it[SettingsRepository.DAILY_GOAL_MM] = -5.0
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
