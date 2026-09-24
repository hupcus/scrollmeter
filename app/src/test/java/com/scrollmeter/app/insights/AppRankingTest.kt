package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.data.model.DateRange
import com.scrollmeter.app.measurement.MeasurementQuality
import java.time.LocalDate
import org.junit.Test

/** Spec §24, D19: order, share, quality; time-only apps without scroll data. */
class AppRankingTest {
    private fun app(pkg: String, mm: Double?, fg: Long? = null, active: Long? = null, measured: Long = 100) =
        AppSummary(pkg, mm, active, fg, if (mm == null) null else measured, if (mm == null) null else 0, if (mm == null) null else 0, if (mm == null) null else 0)

    private val apps = listOf(app("a", 30.0, fg = 600_000, active = 50_000), app("b", 70.0, fg = 60_000, active = 90_000), app("youtube", null, fg = 3_600_000))

    @Test
    fun byDistanceWithSharesAndQuality() {
        val ranked = AppRanking.rank(apps, AppSort.DISTANCE, usageGranted = true, calibration = CalibrationConfidence.HIGH)
        assertThat(ranked.map { it.app.packageName }).containsExactly("b", "a", "youtube").inOrder()
        assertThat(ranked[0].sharePercent).isWithin(1e-9).of(70.0)
        assertThat(ranked[0].quality).isEqualTo(MeasurementQuality.HIGH)
        assertThat(ranked[2].sharePercent).isNull()
        assertThat(ranked[2].quality).isNull()
    }

    @Test
    fun byTimeUsesTimeInAppWithAccessAndScrollTimeWithout() {
        assertThat(AppRanking.rank(apps, AppSort.TIME, usageGranted = true, calibration = CalibrationConfidence.MEDIUM).map { it.app.packageName })
            .containsExactly("youtube", "a", "b").inOrder()
        assertThat(AppRanking.rank(apps, AppSort.TIME, usageGranted = false, calibration = CalibrationConfidence.MEDIUM).map { it.app.packageName })
            .containsExactly("b", "a", "youtube").inOrder()
    }

    @Test
    fun periodsEndTodayAndLifetimeIsEverything() {
        val today = LocalDate.parse("2026-09-24")
        assertThat(AppsPeriod.TODAY.range(today).fromKey).isEqualTo("2026-09-24")
        assertThat(AppsPeriod.DAYS_7.range(today).fromKey).isEqualTo("2026-09-18")
        assertThat(AppsPeriod.DAYS_30.range(today).fromKey).isEqualTo("2026-08-26")
        assertThat(AppsPeriod.LIFETIME.range(today)).isEqualTo(DateRange.ALL)
    }

    @Test
    fun qualityComesFromTheAllTimeCountersWhenGiven() {
        val today = listOf(app("a", 10.0, measured = 3)) // too few events today to judge
        assertThat(AppRanking.rank(today, AppSort.DISTANCE, true, CalibrationConfidence.HIGH).single().quality).isEqualTo(MeasurementQuality.UNKNOWN)
        val lifetime = mapOf("a" to app("a", 900.0, measured = 500))
        assertThat(AppRanking.rank(today, AppSort.DISTANCE, true, CalibrationConfidence.HIGH, lifetime).single().quality).isEqualTo(MeasurementQuality.HIGH)
    }
}
