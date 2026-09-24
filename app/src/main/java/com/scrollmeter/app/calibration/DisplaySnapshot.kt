package com.scrollmeter.app.calibration

import com.scrollmeter.app.measurement.DeviceGeometry
import com.scrollmeter.app.measurement.PhysicalScaleProvider
import com.scrollmeter.app.measurement.ScrollMeasurementEngine

/**
 * What the default display reports about itself, in physical pixels, plus the phone it belongs to.
 * Pure Kotlin: [DisplayMetricsProvider] fills it on the device, tests build it directly.
 */
data class DisplaySnapshot(
    val widthPx: Int,
    val heightPx: Int,
    val xdpi: Double,
    val ydpi: Double,
    val densityDpi: Int,
    val manufacturer: String,
    val model: String,
) {
    /** Rotation swaps width and height; the panel's edges stay the same. */
    val shortEdgePx: Int get() = minOf(widthPx, heightPx)
    val longEdgePx: Int get() = maxOf(widthPx, heightPx)

    /** Geometry for the outlier limit and the px → mm scale: the stored calibration over xdpi/ydpi. */
    fun toDisplayScale(calibration: CalibrationState): ScrollMeasurementEngine.DisplayScale =
        ScrollMeasurementEngine.DisplayScale(
            geometry = DeviceGeometry(widthPx, heightPx),
            scale = PhysicalScaleProvider.resolve(calibration, this),
        )
}
