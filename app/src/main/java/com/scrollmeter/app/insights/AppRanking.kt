package com.scrollmeter.app.insights

import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.measurement.MeasurementQuality
import com.scrollmeter.app.measurement.MeasurementQualityRater
import java.time.LocalDate

enum class AppSort { DISTANCE, TIME }

/** Spec §24: Dnes | 7 dní | 30 dní | Celkem — the Aplikace list and the app detail. */
enum class AppsPeriod {
    TODAY, DAYS_7, DAYS_30, LIFETIME;

    fun range(today: LocalDate): DateRange = when (this) {
        TODAY -> DateRange.day(today)
        DAYS_7 -> DateRange(today.minusDays(6), today)
        DAYS_30 -> DateRange(today.minusDays(29), today)
        LIFETIME -> DateRange.ALL
    }
}

/** One row of Aplikace (spec §24): share of the period's distance, quality; null = no scroll data. */
data class RankedApp(val app: AppSummary, val sharePercent: Double?, val quality: MeasurementQuality?)

/**
 * Orders the apps of a period (spec §24, D19). Quality is rated on [qualityBasis] — the app's
 * all-time counters — when given: it describes how the app reports scrolling, so it should not
 * flip to "málo dat" every morning just because today has few events (ADR-029). By distance: longest first. By time: time in app
 * when Usage access is granted, scroll time otherwise. Apps with only time in app (YouTube) have no
 * share and no quality and sort after every app with data of the chosen kind. Pure Kotlin.
 */
object AppRanking {
    fun rank(
        apps: List<AppSummary>,
        sort: AppSort,
        usageGranted: Boolean,
        calibration: CalibrationConfidence,
        qualityBasis: Map<String, AppSummary> = emptyMap(),
    ): List<RankedApp> {
        val total = apps.sumOf { it.distanceMm ?: 0.0 }
        val ranked = apps.map { app ->
            val scrolled = app.distanceMm != null
            RankedApp(
                app = app,
                sharePercent = if (scrolled && total > 0) app.distanceMm / total * 100 else null,
                quality = if (scrolled) quality(qualityBasis[app.packageName] ?: app, calibration) else null,
            )
        }
        val key: (RankedApp) -> Double? = when (sort) {
            AppSort.DISTANCE -> { r -> r.app.distanceMm }
            AppSort.TIME -> { r -> (if (usageGranted) r.app.foregroundMs else r.app.activeScrollMs)?.toDouble() }
        }
        return ranked.sortedWith(compareBy<RankedApp> { key(it) == null }.thenByDescending { key(it) ?: 0.0 }.thenBy { it.app.packageName })
    }

    /** The quality word for one app's counters (spec §20, ADR-029). */
    fun quality(app: AppSummary, calibration: CalibrationConfidence): MeasurementQuality = MeasurementQualityRater.rate(
        app.measuredEventCount ?: 0, app.fallbackEventCount ?: 0, app.unmeasurableEventCount ?: 0, app.rejectedOutlierCount ?: 0, calibration,
    )
}
