package com.scrollmeter.app.calibration

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.TestPhone
import org.junit.Assert.assertThrows
import org.junit.Test

/** Spec §8 and §57 "85.6 mm / known pixel width", on the test phone's numbers. */
class CardCalibrationTest {
    @Test
    fun cardWidthOverKnownPixelsIsMmPerPx() {
        assertThat(CardCalibration.mmPerPx(1360)).isWithin(1e-12).of(85.60 / 1360)
        assertThat(CardCalibration.mmPerPx(1360)).isWithin(1e-6).of(0.062941)
        assertThat(MeasurementConfig.CARD_WIDTH_MM).isEqualTo(85.60)
    }

    /** PLAN Phase 2: on the test phone a card should come out near 0.0630 mm/px (xdpi 403.411 → 1359.5 px). */
    @Test
    fun testPhoneXdpiPredictsTheCardLength() {
        val predicted = CardCalibration.referencePxFor(MeasurementConfig.MM_PER_INCH / TestPhone.XDPI)
        assertThat(predicted).isEqualTo(1360)
        assertThat(CardCalibration.mmPerPx(predicted)).isWithin(0.0001).of(0.0630)
    }

    @Test
    fun createAssumesSquarePixelsAndRecordsTheDisplay() {
        val c = CardCalibration.create(1352, TestPhone.snapshot, nowMs = 42L)
        assertThat(c.referencePx).isEqualTo(1352)
        assertThat(c.mmPerPxX).isWithin(1e-12).of(85.60 / 1352)
        assertThat(c.mmPerPxY).isEqualTo(c.mmPerPxX)
        assertThat(c.calibratedAtMs).isEqualTo(42L)
        assertThat(c.manufacturer).isEqualTo("OnePlus")
        assertThat(c.model).isEqualTo("CPH2399")
        assertThat(c.xdpiAtCalibration).isEqualTo(TestPhone.XDPI)
        assertThat(c.ydpiAtCalibration).isEqualTo(TestPhone.YDPI)
        assertThat(c.panelShortPx).isEqualTo(1080)
        assertThat(c.panelLongPx).isEqualTo(2400)
    }

    /** The bar may only say what a 100–1000 dpi panel could (the xdpi plausibility range, ADR-016). */
    @Test
    fun plausibleLengthsFollowTheDpiRange() {
        assertThat(CardCalibration.plausibleReferencePx.first).isEqualTo(338)
        assertThat(CardCalibration.plausibleReferencePx.last).isEqualTo(3370)
        assertThrows(IllegalArgumentException::class.java) { CardCalibration.create(337, TestPhone.snapshot, 0) }
        assertThrows(IllegalArgumentException::class.java) { CardCalibration.create(3371, TestPhone.snapshot, 0) }
        assertThat(CardCalibration.isPlausible(85.60 / 1360)).isTrue()
        assertThat(CardCalibration.isPlausible(25.4 / 50.0)).isFalse()
        assertThat(CardCalibration.isPlausible(25.4 / 2_000.0)).isFalse()
        assertThat(CardCalibration.isPlausible(Double.NaN)).isFalse()
    }

    /** ADR-024: same phone and resolution in any rotation — yes; other resolution or phone — no. */
    @Test
    fun calibrationAppliesOnlyToTheDisplayItWasMadeOn() {
        val c = CardCalibration.create(1360, TestPhone.snapshot, 0)
        val landscape = TestPhone.snapshot.copy(widthPx = 2400, heightPx = 1080, xdpi = TestPhone.YDPI, ydpi = TestPhone.XDPI)
        assertThat(c.appliesTo(TestPhone.snapshot)).isTrue()
        assertThat(c.appliesTo(landscape)).isTrue()
        assertThat(c.appliesTo(TestPhone.snapshot.copy(widthPx = 720, heightPx = 1600))).isFalse()
        assertThat(c.appliesTo(TestPhone.snapshot.copy(model = "CPH2451"))).isFalse()
        assertThat(c.appliesTo(TestPhone.snapshot.copy(manufacturer = "samsung"))).isFalse()
    }
}
