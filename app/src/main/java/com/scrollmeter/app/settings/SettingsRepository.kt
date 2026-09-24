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
import com.scrollmeter.app.notifications.NotificationKind
import com.scrollmeter.app.notifications.NotificationState
import com.scrollmeter.app.usage.UsageSyncState
import java.time.LocalDate
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
 * default instead of failing the screen or the service (spec §61). Also keeps state that is not a
 * setting: the last usage sync and the erase floor for [com.scrollmeter.app.usage.UsageSyncer], and
 * the day each notification was last posted (ADR-031).
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) : UsageSyncState, NotificationState {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    /** For screens: a read error shows the defaults (spec §61). */
    val settings: Flow<Settings> = dataStore.data
        .map(::toSettings)
        .catch { emit(Settings()) }
        .distinctUntilChanged()

    /**
     * For the service and the usage sync: a read error is an error, not "no exclusions" — otherwise
     * an unreadable file would silently start measuring apps the user excluded.
     */
    val stored: Flow<Settings> = dataStore.data
        .map(::toSettings)
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

    suspend fun setNotify(kind: NotificationKind, enabled: Boolean) = edit { it[notifyKey(kind)] = enabled }

    override suspend fun lastPosted(kind: NotificationKind): LocalDate? =
        runCatching { dataStore.data.first()[postedKey(kind)]?.let(LocalDate::parse) }.getOrNull()

    override suspend fun setLastPosted(kind: NotificationKind, date: LocalDate) = edit { it[postedKey(kind)] = date.toString() }

    override suspend fun dataFloorMs(): Long? = runCatching { dataStore.data.first()[DATA_FLOOR_MS] }.getOrNull()

    /** "Smazat všechna data": time in app before [ms] is never imported again (ADR-031). */
    suspend fun setDataFloorMs(ms: Long) = edit { it[DATA_FLOOR_MS] = ms }

    override suspend fun lastSyncMs(): Long? = runCatching { dataStore.data.first()[USAGE_SYNC_LAST_MS] }.getOrNull()

    override suspend fun setLastSyncMs(ms: Long) = edit { it[USAGE_SYNC_LAST_MS] = ms }

    /** "Smazat všechna data i nastavení": back to defaults (the eraser sets the floor again afterwards). */
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
        val DATA_FLOOR_MS = longPreferencesKey("data_floor_ms")
        val NOTIFY_GOAL = booleanPreferencesKey("notify_goal")
        val NOTIFY_RECORD = booleanPreferencesKey("notify_record")
        val NOTIFY_SUMMARY = booleanPreferencesKey("notify_summary")

        fun notifyKey(kind: NotificationKind) = when (kind) {
            NotificationKind.GOAL -> NOTIFY_GOAL
            NotificationKind.RECORD -> NOTIFY_RECORD
            NotificationKind.SUMMARY -> NOTIFY_SUMMARY
        }

        fun postedKey(kind: NotificationKind) = stringPreferencesKey("notified_${kind.name.lowercase()}_date")

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
                notifyGoal = read { prefs[NOTIFY_GOAL] } ?: defaults.notifyGoal,
                notifyRecord = read { prefs[NOTIFY_RECORD] } ?: defaults.notifyRecord,
                notifySummary = read { prefs[NOTIFY_SUMMARY] } ?: defaults.notifySummary,
            )
        }
    }
}
