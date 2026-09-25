package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.data.model.AppSummary
import com.scrollmeter.app.measurement.MeasurementQuality
import org.junit.Test

/** ADR-036: one list per period, by distance; time-only apps (D19) last; share and quality for the detail. */
class AppRankingTest {
    private fun app(pkg: String, mm: Double?, fg: Long? = null, active: Long? = null, measured: Long = 100) =
        AppSummary(pkg, mm, active, fg, if (mm == null) null else measured, if (mm == null) null else 0, if (mm == null) null else 0, if (mm == null) null else 0)

    @Test
    fun byDistanceThenTimeOnlyAppsByTime() {
        val apps = listOf(
            app("a", 30.0, fg = 600_000), app("youtube", null, fg = 3_600_000), app("b", 70.0, fg = 60_000),
            app("maps", null, fg = 120_000), app("same", 30.0, fg = 900_000),
        )
        assertThat(AppRanking.list(apps).map { it.packageName }).containsExactly("b", "same", "a", "youtube", "maps").inOrder()
    }

    @Test
    fun rowsWithNothingToShowAreLeftOut() {
        val apps = listOf(app("empty", 0.0, fg = 0), app("nothing", null), app("unmeasurable", 0.0, active = 5_000))
        assertThat(AppRanking.list(apps).map { it.packageName }).containsExactly("unmeasurable")
    }

    @Test
    fun shareOfThePeriod() {
        assertThat(AppRanking.sharePercent(30.0, 120.0)).isWithin(1e-9).of(25.0)
        assertThat(AppRanking.sharePercent(null, 120.0)).isNull()
        assertThat(AppRanking.sharePercent(0.0, 0.0)).isNull()
        assertThat(AppRanking.sharePercent(1.0, null)).isNull()
    }

    @Test
    fun qualityFromTheCounters() {
        assertThat(AppRanking.quality(app("a", 10.0, measured = 3), CalibrationConfidence.HIGH)).isEqualTo(MeasurementQuality.UNKNOWN)
        assertThat(AppRanking.quality(app("a", 900.0, measured = 500), CalibrationConfidence.HIGH)).isEqualTo(MeasurementQuality.HIGH)
    }
}
