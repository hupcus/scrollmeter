package com.scrollmeter.app.settings

/** Spec §26 Units: Automatic switches from m to km at 1 km; the other two are fixed. */
enum class UnitPreference { AUTOMATIC, METRES, KILOMETRES }

enum class ThemePreference { SYSTEM, LIGHT, DARK }

/**
 * User settings (spec §17 Settings). Calibration lives in its own store (ADR-024); the usage-sync
 * bookkeeping is in [SettingsRepository] but not here — it is state, not a setting.
 */
data class Settings(
    val dailyGoalMm: Double = DEFAULT_DAILY_GOAL_MM,
    val excludedPackages: Set<String> = emptySet(),
    val unitPreference: UnitPreference = UnitPreference.AUTOMATIC,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val showComparisons: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val privacyDisclosureAccepted: Boolean = false,
    val usageTimeCardDismissed: Boolean = false,
    /** Spec §25, §26: every notification is opt-in; none is needed for measuring. */
    val notifyGoal: Boolean = false,
    val notifyRecord: Boolean = false,
    val notifySummary: Boolean = false,
) {
    companion object {
        /** Spec §25 options; 500 m is the spec's own example. */
        const val DEFAULT_DAILY_GOAL_MM = 500_000.0
        val GOAL_PRESETS_MM = listOf(100_000.0, 250_000.0, 500_000.0, 1_000_000.0, 2_000_000.0, 5_000_000.0)

        /** Custom goals (spec §25, ADR-031): the dialog rejects anything outside; never clamped silently. */
        val GOAL_RANGE_MM = 10_000.0..100_000_000.0
    }
}
