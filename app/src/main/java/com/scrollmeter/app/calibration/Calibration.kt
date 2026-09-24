package com.scrollmeter.app.calibration

import com.scrollmeter.app.measurement.MeasurementConfig
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A saved card calibration (spec §8, §17 CalibrationEntity / Preferences). Pure Kotlin —
 * [CalibrationRepository] stores it, [com.scrollmeter.app.measurement.PhysicalScaleProvider] uses it.
 */
data class ManualCalibration(
    /** Length of the bar, in raw display pixels, that matched the card's long edge. */
    val referencePx: Int,
    val mmPerPxX: Double,
    val mmPerPxY: Double,
    val calibratedAtMs: Long,
    val manufacturer: String,
    val model: String,
    val xdpiAtCalibration: Double,
    val ydpiAtCalibration: Double,
    val panelShortPx: Int,
    val panelLongPx: Int,
) {
    /**
     * Pixels measured on one panel mean nothing on another: a calibration applies only to the
     * phone and the display resolution it was made on (ADR-024). Rotation does not matter.
     */
    fun appliesTo(display: DisplaySnapshot): Boolean =
        manufacturer == display.manufacturer &&
            model == display.model &&
            panelShortPx == display.shortEdgePx &&
            panelLongPx == display.longEdgePx
}

/**
 * The stored calibration setting. [version] counts every change the user made (a card saved or
 * "use the automatic estimate"); 0 = never touched. Aggregates carry it from Phase 3 (spec §65).
 */
data class CalibrationState(val version: Int, val manual: ManualCalibration?) {
    companion object {
        val NONE = CalibrationState(version = 0, manual = null)
    }
}

/** The card method's arithmetic (spec §8): `mmPerPx = 85.60 / referencePixels`, square pixels. */
object CardCalibration {
    /**
     * Bar lengths that imply a physical density inside
     * [MeasurementConfig.MIN_PLAUSIBLE_DPI]..[MeasurementConfig.MAX_PLAUSIBLE_DPI] — the same range
     * that decides whether `xdpi`/`ydpi` are believable (ADR-016).
     */
    val plausibleReferencePx: IntRange =
        ceil(MeasurementConfig.CARD_WIDTH_MM / MeasurementConfig.MM_PER_INCH * MeasurementConfig.MIN_PLAUSIBLE_DPI).toInt()..
            floor(MeasurementConfig.CARD_WIDTH_MM / MeasurementConfig.MM_PER_INCH * MeasurementConfig.MAX_PLAUSIBLE_DPI).toInt()

    fun mmPerPx(referencePx: Int): Double = MeasurementConfig.CARD_WIDTH_MM / referencePx

    /** The bar length that a scale of [mmPerPx] predicts for the card — the slider's start. */
    fun referencePxFor(mmPerPx: Double): Int = (MeasurementConfig.CARD_WIDTH_MM / mmPerPx).roundToInt()

    fun isPlausible(mmPerPx: Double): Boolean =
        !mmPerPx.isNaN() && mmPerPx in mmPerPx(plausibleReferencePx.last)..mmPerPx(plausibleReferencePx.first)

    fun create(referencePx: Int, display: DisplaySnapshot, nowMs: Long): ManualCalibration {
        require(referencePx in plausibleReferencePx) { "reference $referencePx px outside $plausibleReferencePx" }
        val mmPerPx = mmPerPx(referencePx)
        return ManualCalibration(
            referencePx = referencePx,
            mmPerPxX = mmPerPx,
            mmPerPxY = mmPerPx,
            calibratedAtMs = nowMs,
            manufacturer = display.manufacturer,
            model = display.model,
            xdpiAtCalibration = display.xdpi,
            ydpiAtCalibration = display.ydpi,
            panelShortPx = display.shortEdgePx,
            panelLongPx = display.longEdgePx,
        )
    }
}
