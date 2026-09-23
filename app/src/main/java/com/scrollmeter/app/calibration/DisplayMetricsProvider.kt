package com.scrollmeter.app.calibration

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.scrollmeter.app.measurement.DeviceGeometry
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.measurement.ScrollMeasurementEngine

/** What the default display reports about itself, in physical pixels. */
data class DisplaySnapshot(
    val widthPx: Int,
    val heightPx: Int,
    val xdpi: Double,
    val ydpi: Double,
    val densityDpi: Int,
) {
    fun toDisplayScale(): ScrollMeasurementEngine.DisplayScale = ScrollMeasurementEngine.DisplayScale(
        geometry = DeviceGeometry(widthPx, heightPx),
        scale = PhysicalScaleProvider.fromDisplayMetrics(xdpi, ydpi, densityDpi),
    )
}

/**
 * Reads the real (full-screen) size and the physical `xdpi`/`ydpi` of the default display
 * (spec §9, §11). Works from a non-visual context such as the accessibility service.
 */
class DisplayMetricsProvider(private val context: Context) {
    fun read(): DisplaySnapshot {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        // getRealMetrics is deprecated for window sizing, but it is the one call that returns the
        // physical panel size with xdpi/ydpi without needing a visual (activity) context.
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return DisplaySnapshot(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            xdpi = metrics.xdpi.toDouble(),
            ydpi = metrics.ydpi.toDouble(),
            densityDpi = metrics.densityDpi,
        )
    }
}
