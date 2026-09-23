package com.scrollmeter.app.measurement

import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.calibration.CalibrationMethod

/** Millimetres per physical pixel on each axis, and where the numbers came from. */
data class PhysicalScale(
    val mmPerPxX: Double,
    val mmPerPxY: Double,
    val method: CalibrationMethod,
    val confidence: CalibrationConfidence,
)

/**
 * Picks the px → mm scale. Phase 1 knows only the display's own `xdpi`/`ydpi` (spec §9); the
 * manual card calibration takes precedence from Phase 2 on (spec §8).
 *
 * `densityDpi` is Android's *logical* UI density and is not the panel's physical PPI (on the
 * test phone it is 480 against a physical ~402, which would under-measure by 16 %). It is used
 * only when both `xdpi` and `ydpi` are implausible, and then with LOW confidence.
 */
object PhysicalScaleProvider {
    fun fromDisplayMetrics(xdpi: Double, ydpi: Double, densityDpi: Int): PhysicalScale {
        val xValid = xdpi.isPlausibleDpi()
        val yValid = ydpi.isPlausibleDpi()
        return when {
            xValid && yValid -> PhysicalScale(
                mmPerPxX = MeasurementConfig.MM_PER_INCH / xdpi,
                mmPerPxY = MeasurementConfig.MM_PER_INCH / ydpi,
                method = CalibrationMethod.DISPLAY_METRICS,
                confidence = CalibrationConfidence.MEDIUM,
            )
            // Square pixels are the norm, so one sane axis stands in for the broken one.
            xValid || yValid -> {
                val mmPerPx = MeasurementConfig.MM_PER_INCH / (if (xValid) xdpi else ydpi)
                PhysicalScale(mmPerPx, mmPerPx, CalibrationMethod.DISPLAY_METRICS, CalibrationConfidence.LOW)
            }
            else -> {
                val mmPerPx = MeasurementConfig.MM_PER_INCH / densityDpi.coerceAtLeast(1)
                PhysicalScale(mmPerPx, mmPerPx, CalibrationMethod.UNKNOWN, CalibrationConfidence.LOW)
            }
        }
    }

    private fun Double.isPlausibleDpi(): Boolean =
        !isNaN() && this in MeasurementConfig.MIN_PLAUSIBLE_DPI..MeasurementConfig.MAX_PLAUSIBLE_DPI
}
