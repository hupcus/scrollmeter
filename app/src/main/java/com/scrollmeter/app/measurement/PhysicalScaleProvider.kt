package com.scrollmeter.app.measurement

import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.calibration.CalibrationState
import com.scrollmeter.app.calibration.DisplaySnapshot

/**
 * Millimetres per physical pixel on each axis, where the numbers came from, and the stored
 * calibration version they were resolved under (spec §65 — distances keep the version they
 * were computed with).
 */
data class PhysicalScale(
    val mmPerPxX: Double,
    val mmPerPxY: Double,
    val method: CalibrationMethod,
    val confidence: CalibrationConfidence,
    val calibrationVersion: Int = 0,
)

/**
 * Picks the px → mm scale: the user's card calibration when it applies to this display
 * (spec §8, ADR-024), otherwise the display's own `xdpi`/`ydpi` (spec §9).
 *
 * densityDpi is logical Android UI density and is not used as the
 * primary physical-distance conversion.
 *
 * (On the test phone it is 480 against a physical ~402 dpi, which would under-measure by 16 %.)
 * It is used only when both `xdpi` and `ydpi` are implausible, and then with LOW confidence.
 */
object PhysicalScaleProvider {
    fun resolve(calibration: CalibrationState, display: DisplaySnapshot): PhysicalScale {
        val manual = calibration.manual
        return if (manual != null && manual.appliesTo(display)) {
            PhysicalScale(manual.mmPerPxX, manual.mmPerPxY, CalibrationMethod.MANUAL_CARD, CalibrationConfidence.HIGH, calibration.version)
        } else {
            fromDisplayMetrics(display.xdpi, display.ydpi, display.densityDpi).copy(calibrationVersion = calibration.version)
        }
    }

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
