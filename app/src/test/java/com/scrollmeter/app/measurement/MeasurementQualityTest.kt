package com.scrollmeter.app.measurement

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.calibration.CalibrationConfidence.HIGH
import com.scrollmeter.app.calibration.CalibrationConfidence.MEDIUM
import org.junit.Test

/** Spec §20 / ADR-029 thresholds. */
class MeasurementQualityTest {
    private fun rate(measured: Long, fallback: Long = 0, unmeasurable: Long = 0, outliers: Long = 0, card: Boolean = true) =
        MeasurementQualityRater.rate(measured, fallback, unmeasurable, outliers, if (card) HIGH else MEDIUM)

    @Test
    fun tooFewEventsIsUnknown() {
        assertThat(rate(19)).isEqualTo(MeasurementQuality.UNKNOWN)
        assertThat(rate(20)).isEqualTo(MeasurementQuality.HIGH)
    }

    @Test
    fun directDeltasWithACardAreHigh() {
        assertThat(rate(90, fallback = 10)).isEqualTo(MeasurementQuality.HIGH)
        assertThat(rate(89, fallback = 11)).isEqualTo(MeasurementQuality.MEDIUM)
    }

    @Test
    fun theAutomaticScaleIsAtBestMedium() {
        assertThat(rate(100, card = false)).isEqualTo(MeasurementQuality.MEDIUM)
    }

    @Test
    fun frequentFallbackIsMedium() {
        assertThat(rate(20, fallback = 80)).isEqualTo(MeasurementQuality.MEDIUM)
    }

    @Test
    fun mostlyUnmeasurableOrManyOutliersIsLow() {
        assertThat(rate(50, unmeasurable = 50)).isEqualTo(MeasurementQuality.LOW)
        assertThat(rate(51, unmeasurable = 49)).isEqualTo(MeasurementQuality.MEDIUM)
        assertThat(rate(95, outliers = 5)).isEqualTo(MeasurementQuality.LOW)
        assertThat(rate(0, unmeasurable = 40)).isEqualTo(MeasurementQuality.LOW)
    }

    @Test
    fun highNeedsFewUnmeasurableAndOutliers() {
        assertThat(rate(90, unmeasurable = 10)).isEqualTo(MeasurementQuality.MEDIUM) // 10 % unmeasurable
        assertThat(rate(99, outliers = 1)).isEqualTo(MeasurementQuality.MEDIUM) // 1 % outliers
        assertThat(rate(199, outliers = 1)).isEqualTo(MeasurementQuality.HIGH) // 0,5 %
    }
}
