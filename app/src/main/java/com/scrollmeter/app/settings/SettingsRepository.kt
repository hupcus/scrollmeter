package com.scrollmeter.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scrollmeter.app.usage.UsageSyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Settings in DataStore Preferences (spec §17, D8). A value that cannot be read falls back to its
 * default instead of failing the screen or the service (spec §61). Also keeps the time of the last
 * usage sync for [com.scrollmeter.app.usage.UsageSyncer].
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) : UsageSyncState {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    val settings: Flow<Settings> = dataStore.data
        .map(::toSettings)
        .catch { emit(Settings()) }
        .distinctUntilChanged()

    suspend fun setDailyGoalMm(mm: Double) = edit { it[DAILY_GOAL_MM] = mm.coerceIn(Settings.GOAL_RANGE_MM) }

    suspend fun setExcluded(packageName: String, excluded: Boolean) = edit {
        val current = it[EXCLUDED_PACKAGES].orEmpty()
        it[EXCLUDED_PACKAGES] = if (excluded) current + packageName else current - packageName
    }

    suspend fun setUnitPreference(unit: UnitPreference) = edit { it[UNIT] = unit.name }

    suspend fun setTheme(theme: ThemePreference) = edit { it[THEME] = theme.name }

    suspend fun setShowComparisons(show: Boolean) = edit { it[SHOW_COMPARISONS] = show }

    suspend fun setOnboardingCompleted(done: Boolean) = edit { it[ONBOARDING_COMPLETED] = done }

    suspend fun setPrivacyDisclosureAccepted(accepted: Boolean) = edit { it[PRIVACY_DISCLOSURE_ACCEPTED] = accepted }

    suspend fun setUsageTimeCardDismissed(dismissed: Boolean) = edit { it[USAGE_TIME_CARD_DISMISSED] = dismissed }

    override suspend fun lastSyncMs(): Long? = runCatching { dataStore.data.first()[USAGE_SYNC_LAST_MS] }.getOrNull()

    override suspend fun setLastSyncMs(ms: Long) = edit { it[USAGE_SYNC_LAST_MS] = ms }

    /** "Smazat všechna data" (Phase 6): back to defaults; the next usage sync starts from scratch. */
    suspend fun clear() = edit { it.clear() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    internal companion object {
        val DAILY_GOAL_MM = doublePreferencesKey("daily_goal_mm")
        val EXCLUDED_PACKAGES = stringSetPreferencesKey("excluded_packages")
        val UNIT = stringPreferencesKey("unit_preference")
        val THEME = stringPreferencesKey("theme")
        val SHOW_COMPARISONS = booleanPreferencesKey("show_comparisons")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PRIVACY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("privacy_disclosure_accepted")
        val USAGE_TIME_CARD_DISMISSED = booleanPreferencesKey("usage_time_card_dismissed")
        val USAGE_SYNC_LAST_MS = longPreferencesKey("usage_sync_last_ms")

        fun toSettings(prefs: Preferences): Settings {
            val defaults = Settings()
            fun <T> read(block: () -> T?): T? = runCatching(block).getOrNull()
            return Settings(
                dailyGoalMm = read { prefs[DAILY_GOAL_MM] }?.takeIf { it in Settings.GOAL_RANGE_MM } ?: defaults.dailyGoalMm,
                excludedPackages = read { prefs[EXCLUDED_PACKAGES] } ?: defaults.excludedPackages,
                unitPreference = read { prefs[UNIT]?.let(UnitPreference::valueOf) } ?: defaults.unitPreference,
                theme = read { prefs[THEME]?.let(ThemePreference::valueOf) } ?: defaults.theme,
                showComparisons = read { prefs[SHOW_COMPARISONS] } ?: defaults.showComparisons,
                onboardingCompleted = read { prefs[ONBOARDING_COMPLETED] } ?: defaults.onboardingCompleted,
                privacyDisclosureAccepted = read { prefs[PRIVACY_DISCLOSURE_ACCEPTED] } ?: defaults.privacyDisclosureAccepted,
                usageTimeCardDismissed = read { prefs[USAGE_TIME_CARD_DISMISSED] } ?: defaults.usageTimeCardDismissed,
            )
        }
    }
}
