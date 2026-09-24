package com.scrollmeter.app.calibration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One DataStore per file per process — the service and the UI share it (same process). */
private val Context.calibrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "calibration",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The calibration setting in DataStore Preferences (spec §17 CalibrationEntity / Preferences,
 * D8, ADR-024). Every change bumps [CalibrationState.version]; values that fail the plausibility
 * check read as "no manual calibration" rather than as a wrong scale.
 */
class CalibrationRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.calibrationDataStore)

    /** Current state first, then every change. A storage error reads as [CalibrationState.NONE] (spec §61). */
    val state: Flow<CalibrationState> = dataStore.data
        .map(::toState)
        .catch { emit(CalibrationState.NONE) }
        .distinctUntilChanged()

    suspend fun saveManual(calibration: ManualCalibration) {
        dataStore.edit { prefs ->
            prefs.bumpVersion()
            prefs[METHOD] = CalibrationMethod.MANUAL_CARD.name
            prefs[REFERENCE_PX] = calibration.referencePx
            prefs[MM_PER_PX_X] = calibration.mmPerPxX
            prefs[MM_PER_PX_Y] = calibration.mmPerPxY
            prefs[CALIBRATED_AT_MS] = calibration.calibratedAtMs
            prefs[MANUFACTURER] = calibration.manufacturer
            prefs[MODEL] = calibration.model
            prefs[XDPI_AT_CALIBRATION] = calibration.xdpiAtCalibration
            prefs[YDPI_AT_CALIBRATION] = calibration.ydpiAtCalibration
            prefs[PANEL_SHORT_PX] = calibration.panelShortPx
            prefs[PANEL_LONG_PX] = calibration.panelLongPx
        }
    }

    /** "Přeskočit – použít automatický odhad": forget the card, measure with xdpi/ydpi again. */
    suspend fun useAutomatic() {
        dataStore.edit { prefs ->
            val hadManual = prefs[METHOD] != null
            MANUAL_KEYS.forEach { prefs.remove(it) }
            if (hadManual) prefs.bumpVersion()
        }
    }

    private fun MutablePreferences.bumpVersion() {
        this[VERSION] = (this[VERSION] ?: 0) + 1
    }

    internal companion object {
        val VERSION = intPreferencesKey("calibration_version")
        val METHOD = stringPreferencesKey("method")
        val REFERENCE_PX = intPreferencesKey("reference_px")
        val MM_PER_PX_X = doublePreferencesKey("mm_per_px_x")
        val MM_PER_PX_Y = doublePreferencesKey("mm_per_px_y")
        val CALIBRATED_AT_MS = longPreferencesKey("calibrated_at_ms")
        val MANUFACTURER = stringPreferencesKey("device_manufacturer")
        val MODEL = stringPreferencesKey("device_model")
        val XDPI_AT_CALIBRATION = doublePreferencesKey("xdpi_at_calibration")
        val YDPI_AT_CALIBRATION = doublePreferencesKey("ydpi_at_calibration")
        val PANEL_SHORT_PX = intPreferencesKey("panel_short_px")
        val PANEL_LONG_PX = intPreferencesKey("panel_long_px")

        private val MANUAL_KEYS: List<Preferences.Key<*>> = listOf(
            METHOD, REFERENCE_PX, MM_PER_PX_X, MM_PER_PX_Y, CALIBRATED_AT_MS, MANUFACTURER, MODEL,
            XDPI_AT_CALIBRATION, YDPI_AT_CALIBRATION, PANEL_SHORT_PX, PANEL_LONG_PX,
        )

        fun toState(prefs: Preferences): CalibrationState {
            val version = runCatching { prefs[VERSION] }.getOrNull() ?: 0
            return CalibrationState(version, runCatching { manualOf(prefs) }.getOrNull())
        }

        /** Null unless every field is present and the numbers are physically possible. */
        private fun manualOf(prefs: Preferences): ManualCalibration? {
            if (prefs[METHOD] != CalibrationMethod.MANUAL_CARD.name) return null
            val calibration = ManualCalibration(
                referencePx = prefs[REFERENCE_PX] ?: return null,
                mmPerPxX = prefs[MM_PER_PX_X] ?: return null,
                mmPerPxY = prefs[MM_PER_PX_Y] ?: return null,
                calibratedAtMs = prefs[CALIBRATED_AT_MS] ?: return null,
                manufacturer = prefs[MANUFACTURER] ?: return null,
                model = prefs[MODEL] ?: return null,
                xdpiAtCalibration = prefs[XDPI_AT_CALIBRATION] ?: return null,
                ydpiAtCalibration = prefs[YDPI_AT_CALIBRATION] ?: return null,
                panelShortPx = prefs[PANEL_SHORT_PX] ?: return null,
                panelLongPx = prefs[PANEL_LONG_PX] ?: return null,
            )
            val plausible = calibration.referencePx in CardCalibration.plausibleReferencePx &&
                CardCalibration.isPlausible(calibration.mmPerPxX) &&
                CardCalibration.isPlausible(calibration.mmPerPxY)
            return calibration.takeIf { plausible }
        }
    }
}
