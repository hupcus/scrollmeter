package com.scrollmeter.app.measurement

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.calibration.CalibrationConfidence
import com.scrollmeter.app.calibration.CalibrationMethod
import com.scrollmeter.app.calibration.CalibrationState
import com.scrollmeter.app.calibration.CardCalibration
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

    /** Spec §8 over §9, §33: the card wins with HIGH confidence and carries the stored version. */
    @Test
    fun manualCardCalibrationTakesPrecedence() {
        val card = CardCalibration.create(1352, TestPhone.snapshot, 0)
        val scale = PhysicalScaleProvider.resolve(CalibrationState(version = 2, manual = card), TestPhone.snapshot)
        assertThat(scale.method).isEqualTo(CalibrationMethod.MANUAL_CARD)
        assertThat(scale.confidence).isEqualTo(CalibrationConfidence.HIGH)
        assertThat(scale.mmPerPxX).isWithin(1e-12).of(85.60 / 1352)
        assertThat(scale.mmPerPxY).isWithin(1e-12).of(85.60 / 1352)
        assertThat(scale.calibrationVersion).isEqualTo(2)
    }

    @Test
    fun withoutCalibrationTheDisplayMetricsAreUsed() {
        assertThat(PhysicalScaleProvider.resolve(CalibrationState.NONE, TestPhone.snapshot)).isEqualTo(TestPhone.scale)
        // "use the automatic estimate" after a card: xdpi/ydpi again, under the newer version.
        val afterReset = PhysicalScaleProvider.resolve(CalibrationState(version = 3, manual = null), TestPhone.snapshot)
        assertThat(afterReset).isEqualTo(TestPhone.scale.copy(calibrationVersion = 3))
    }

    /** ADR-024: a card measured at another resolution would be ~33 % off — fall back to xdpi/ydpi. */
    @Test
    fun aCalibrationFromAnotherResolutionIsNotUsed() {
        val card = CardCalibration.create(1360, TestPhone.snapshot, 0)
        val lowRes = TestPhone.snapshot.copy(widthPx = 720, heightPx = 1600, xdpi = 268.94, ydpi = 267.37)
        val scale = PhysicalScaleProvider.resolve(CalibrationState(version = 1, manual = card), lowRes)
        assertThat(scale.method).isEqualTo(CalibrationMethod.DISPLAY_METRICS)
        assertThat(scale.confidence).isEqualTo(CalibrationConfidence.MEDIUM)
        assertThat(scale.mmPerPxX).isWithin(1e-9).of(25.4 / 268.94)
        assertThat(scale.calibrationVersion).isEqualTo(1)
    }

    @Test
    fun geometryDiagonalAndOutlierLimit() {
        assertThat(TestPhone.geometry.diagonalPx).isWithin(0.1).of(2631.8)
        assertThat(TestPhone.geometry.maxEventDistancePx).isWithin(0.5).of(10_527.2)
    }
}
