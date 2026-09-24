package com.scrollmeter.app.measurement

import com.scrollmeter.app.calibration.CalibrationConfidence

/** Spec §20: a word, never a made-up accuracy percentage. UNKNOWN = too few events to judge. */
enum class MeasurementQuality { HIGH, MEDIUM, LOW, UNKNOWN }

/**
 * Rates how trustworthy an app's stored distance is from its event counters and the calibration
 * in force (spec §20, ADR-029). Pure Kotlin; thresholds in [MeasurementConfig].
 *
 * - LOW: most events carried no usable pixels, or outliers are frequent — the app does not report
 *   its scrolling in a way we can measure well.
 * - HIGH: almost everything counted came from direct deltas, little was unmeasurable or rejected,
 *   and the display is calibrated with a card.
 * - MEDIUM: everything between — typically the automatic xdpi/ydpi scale or a frequent position fallback.
 */
object MeasurementQualityRater {
    fun rate(
        measured: Long,
        fallback: Long,
        unmeasurable: Long,
        outliers: Long,
        calibration: CalibrationConfidence,
    ): MeasurementQuality {
        val total = measured + fallback + unmeasurable + outliers
        if (total < MeasurementConfig.QUALITY_MIN_EVENTS) return MeasurementQuality.UNKNOWN
        val unmeasurableShare = unmeasurable.toDouble() / total
        val outlierShare = outliers.toDouble() / total
        val counted = measured + fallback
        val directShare = if (counted == 0L) 0.0 else measured.toDouble() / counted
        return when {
            unmeasurableShare >= MeasurementConfig.QUALITY_LOW_UNMEASURABLE_SHARE ||
                outlierShare >= MeasurementConfig.QUALITY_LOW_OUTLIER_SHARE -> MeasurementQuality.LOW
            directShare >= MeasurementConfig.QUALITY_HIGH_DIRECT_SHARE &&
                unmeasurableShare < MeasurementConfig.QUALITY_HIGH_MAX_UNMEASURABLE_SHARE &&
                outlierShare < MeasurementConfig.QUALITY_HIGH_MAX_OUTLIER_SHARE &&
                calibration == CalibrationConfidence.HIGH -> MeasurementQuality.HIGH
            else -> MeasurementQuality.MEDIUM
        }
    }
}
