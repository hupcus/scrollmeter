package com.scrollmeter.app

import android.content.Context
import com.scrollmeter.app.accessibility.AccessibilityStatusChecker
import com.scrollmeter.app.apps.AppInfoProvider
import com.scrollmeter.app.calibration.CalibrationRepository
import com.scrollmeter.app.calibration.CalibrationState
import com.scrollmeter.app.calibration.DisplayMetricsProvider
import com.scrollmeter.app.data.local.ScrollDatabase
import com.scrollmeter.app.data.repository.ScrollRepository
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.measurement.MeasurementMonitor
import com.scrollmeter.app.measurement.MeasurementSettings
import com.scrollmeter.app.measurement.MeasurementSink
import com.scrollmeter.app.measurement.ScrollMeasurementEngine
import com.scrollmeter.app.settings.SettingsRepository
import com.scrollmeter.app.usage.UsageAccessChecker
import com.scrollmeter.app.usage.UsageEventsSource
import com.scrollmeter.app.usage.UsageSyncer
import com.scrollmeter.app.apps.SuggestedExclusions
import com.scrollmeter.app.data.DataEraser
import com.scrollmeter.app.export.CsvExportWriter
import com.scrollmeter.app.notifications.AndroidNotificationPoster
import com.scrollmeter.app.notifications.NotificationWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Lazy singletons shared by the UI and the accessibility service. Constructors stay plain so
 * every piece can be built directly in tests.
 */
class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext

    /** `com.scrollmeter.app` or `com.scrollmeter.app.debug` — excluded from measurement (D18). */
    val ownPackage: String = appContext.packageName

    val measurementSettings = MeasurementSettings()
    val monitor = MeasurementMonitor(ownPackage)

    val displayMetricsProvider by lazy { DisplayMetricsProvider(appContext) }
    val calibrationRepository by lazy { CalibrationRepository(appContext) }
    val statusChecker by lazy { AccessibilityStatusChecker(appContext) }
    val settingsRepository by lazy { SettingsRepository(appContext) }

    /** One Room instance per process — the service writes, the UI reads its Flows (D8). */
    val database by lazy { ScrollDatabase.create(appContext) }
    val scrollRepository by lazy {
        ScrollRepository(database.scrollDao(), monitor.unflushed, settingsRepository.settings.map { it.excludedPackages })
    }

    val usageAccessChecker by lazy { UsageAccessChecker(appContext) }

    /** Labels and icons for the UI (ADR-008) — never used by the measurement pipeline. */
    val appInfoProvider by lazy { AppInfoProvider(appContext) }
    val usageSyncer by lazy {
        UsageSyncer(
            reader = UsageEventsSource(appContext),
            store = scrollRepository,
            state = settingsRepository,
            hasAccess = usageAccessChecker::isGranted,
            ownPackage = ownPackage,
            excludedPackages = { settingsRepository.stored.first().excludedPackages },
        )
    }

    val suggestedExclusions by lazy { SuggestedExclusions(appContext) }

    val csvExportWriter by lazy { CsvExportWriter(appContext, scrollRepository, appInfoProvider) }

    /** After every flush of the service (ADR-031); a no-op until the user switches a notification on. */
    private val notificationPoster by lazy { AndroidNotificationPoster(appContext) }

    val notificationWatcher by lazy {
        NotificationWatcher(
            settings = { settingsRepository.stored.first() },
            facts = { today, settings -> scrollRepository.notificationFacts(today, settings.dailyGoalMm) },
            state = settingsRepository,
            poster = notificationPoster,
        )
    }

    /** "Smazat všechna data" (spec §45, ADR-031). */
    val dataEraser by lazy {
        DataEraser(
            monitor = monitor,
            clearTables = { withContext(Dispatchers.IO) { database.clearAllTables() } },
            clearSettings = settingsRepository::clear,
            forgetCalibration = calibrationRepository::useAutomatic,
            setFloor = settingsRepository::setDataFloorMs,
            clearLeftovers = {
                withContext(Dispatchers.IO) { csvExportWriter.deleteCached() }
                notificationPoster.clearShown()
            },
            usageExclusive = usageSyncer::exclusive,
        )
    }

    /** Debug builds: event log + logcat. Release builds: none (src/release DevTools). */
    val measurementSinks: List<MeasurementSink> by lazy { DevTools.measurementSinks(this) }

    /** The display as it is now, scaled by [calibration] where it applies (ADR-024). */
    fun readDisplayScale(calibration: CalibrationState): ScrollMeasurementEngine.DisplayScale =
        displayMetricsProvider.read().toDisplayScale(calibration)

    fun newEngine(calibration: CalibrationState): ScrollMeasurementEngine =
        ScrollMeasurementEngine(ownPackage, measurementSettings, readDisplayScale(calibration))
}
