package com.scrollmeter.app.calibration

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display

/**
 * Reads the real (full-screen) size and the physical `xdpi`/`ydpi` of the default display
 * (spec §9, §11), and the phone's manufacturer and model (spec §10 — shown, never required).
 * Works from a non-visual context such as the accessibility service.
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
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
        )
    }
}
