package com.scrollmeter.app

import android.content.Context
import com.scrollmeter.app.accessibility.AccessibilityStatusChecker
import com.scrollmeter.app.calibration.DisplayMetricsProvider
import com.scrollmeter.app.devtools.DevTools
import com.scrollmeter.app.measurement.MeasurementMonitor
import com.scrollmeter.app.measurement.MeasurementSettings
import com.scrollmeter.app.measurement.MeasurementSink
import com.scrollmeter.app.measurement.ScrollMeasurementEngine

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
    val statusChecker by lazy { AccessibilityStatusChecker(appContext) }

    /** Debug builds: event log + logcat. Release builds: none (src/release DevTools). */
    val measurementSinks: List<MeasurementSink> by lazy { DevTools.measurementSinks(this) }

    fun readDisplayScale(): ScrollMeasurementEngine.DisplayScale = displayMetricsProvider.read().toDisplayScale()

    fun newEngine(): ScrollMeasurementEngine =
        ScrollMeasurementEngine(ownPackage, measurementSettings, readDisplayScale())
}
