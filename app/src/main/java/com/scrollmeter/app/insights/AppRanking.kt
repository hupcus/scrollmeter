package com.scrollmeter.app.insights

import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.measurement.MeasurementQuality
import com.scrollmeter.app.measurement.MeasurementQualityRater

/**
 * The app list of a period (ADR-036) and the app detail's share and quality. Pure Kotlin.
 */
object AppRanking {
    /**
     * By distance, longest first; apps with only time in app (YouTube, D19) after every app with a
     * distance, longest time first; rows with nothing to show are left out.
     */
    fun list(apps: List<AppSummary>): List<AppSummary> = apps
        .filter { (it.distanceMm ?: 0.0) > 0.0 || (it.foregroundMs ?: 0L) > 0L || (it.activeScrollMs ?: 0L) > 0L }
        .sortedWith(
            compareBy<AppSummary> { it.distanceMm == null }
                .thenByDescending { it.distanceMm ?: 0.0 }
                .thenByDescending { it.foregroundMs ?: 0L }
                .thenBy { it.packageName },
        )

    /** An app's share of the period's distance in percent; null without scroll data or in an empty period. */
    fun sharePercent(appMm: Double?, totalMm: Double?): Double? {
        val distance = appMm ?: return null
        if (totalMm == null || totalMm <= 0.0) return null
        return distance / totalMm * 100
    }

    /**
     * The quality word for one app's counters (spec §20, ADR-029) — rated on the all-time counters, so
     * it does not flip to "málo dat" every morning just because today has few events.
     */
    fun quality(app: AppSummary, calibration: CalibrationConfidence): MeasurementQuality = MeasurementQualityRater.rate(
        app.measuredEventCount ?: 0, app.fallbackEventCount ?: 0, app.unmeasurableEventCount ?: 0, app.rejectedOutlierCount ?: 0, calibration,
    )
}
