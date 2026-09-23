package com.scrollmeter.app.measurement

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.calibration.CalibrationMethod
import org.junit.Test

/** Spec §9 xdpi/ydpi conversion, checked on the real test phone's numbers (PLAN §2). */
class PhysicalScaleProviderTest {
    @Test
    fun xdpiAndYdpiAreConvertedSeparately() {
        val scale = TestPhone.scale
        assertThat(scale.mmPerPxX).isWithin(1e-6).of(0.062963)
        assertThat(scale.mmPerPxY).isWithin(1e-6).of(0.063333)
        assertThat(scale.method).isEqualTo(CalibrationMethod.DISPLAY_METRICS)
        assertThat(scale.confidence).isEqualTo(CalibrationConfidence.MEDIUM)
    }

    /** PLAN §2: 1200 px vertically on the test phone ≈ 76.0 mm; via densityDpi 480 it would be 63.5 mm. */
    @Test
    fun testPhone1200PxIs76Mm() {
        val mm = ScrollDistanceCalculator.distance(0, 1200, TestPhone.scale).totalMm
        assertThat(mm).isWithin(0.05).of(76.0)
        val viaDensityDpi = 1200 * MeasurementConfig.MM_PER_INCH / TestPhone.DENSITY_DPI
        assertThat(viaDensityDpi).isWithin(0.05).of(63.5)
        assertThat(mm).isNotWithin(1.0).of(viaDensityDpi)
    }

    @Test
    fun oneBrokenAxisIsReplacedByTheOtherWithLowConfidence() {
        val scale = PhysicalScaleProvider.fromDisplayMetrics(403.411, 0.0, 480)
        assertThat(scale.mmPerPxX).isWithin(1e-9).of(25.4 / 403.411)
        assertThat(scale.mmPerPxY).isWithin(1e-9).of(25.4 / 403.411)
        assertThat(scale.confidence).isEqualTo(CalibrationConfidence.LOW)
    }

    @Test
    fun bothAxesBrokenFallsBackToDensityDpiAsUnknown() {
        val scale = PhysicalScaleProvider.fromDisplayMetrics(Double.NaN, 5_000.0, 480)
        assertThat(scale.mmPerPxX).isWithin(1e-9).of(25.4 / 480)
        assertThat(scale.method).isEqualTo(CalibrationMethod.UNKNOWN)
        assertThat(scale.confidence).isEqualTo(CalibrationConfidence.LOW)
    }

    @Test
    fun geometryDiagonalAndOutlierLimit() {
        assertThat(TestPhone.geometry.diagonalPx).isWithin(0.1).of(2631.8)
        assertThat(TestPhone.geometry.maxEventDistancePx).isWithin(0.5).of(10_527.2)
    }
}
