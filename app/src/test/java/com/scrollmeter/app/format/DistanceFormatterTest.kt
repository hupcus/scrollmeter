package com.scrollmeter.app.format

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.settings.UnitPreference
import java.util.Locale
import org.junit.Test

/** Spec §21, §26 Units: m/km boundaries and rounding, Czech decimal comma. */
class DistanceFormatterTest {
    private val cs = Locale.forLanguageTag("cs")
    private fun auto(metres: Double) = DistanceFormatter.format(metres * 1_000, UnitPreference.AUTOMATIC, cs).nbsp()
    private fun String.nbsp() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun spec21Examples() {
        assertThat(auto(428.0)).isEqualTo("428 m")
        assertThat(auto(2_840.0)).isEqualTo("2,84 km")
        assertThat(auto(11_700.0)).isEqualTo("11,7 km")
        assertThat(auto(42_600.0)).isEqualTo("42,6 km")
    }

    @Test
    fun smallValuesKeepOneDecimalAndNothingIsZero() {
        assertThat(auto(0.0)).isEqualTo("0,0 m")
        assertThat(auto(3.44)).isEqualTo("3,4 m")
        assertThat(auto(9.96)).isEqualTo("10 m")
        assertThat(auto(Double.NaN)).isEqualTo("0,0 m")
        assertThat(auto(-5.0)).isEqualTo("0,0 m")
    }

    @Test
    fun theKilometreBoundaryIsDecidedOnTheRoundedValue() {
        assertThat(auto(999.4)).isEqualTo("999 m")
        assertThat(auto(999.6)).isEqualTo("1,00 km")
        assertThat(auto(9_996.0)).isEqualTo("10,0 km")
        assertThat(auto(99_960.0)).isEqualTo("100 km")
        assertThat(auto(1_234_000.0)).isEqualTo("1 234 km")
    }

    @Test
    fun fixedUnits() {
        assertThat(DistanceFormatter.format(2_840_000.0, UnitPreference.METRES, cs).nbsp()).isEqualTo("2 840 m")
        assertThat(DistanceFormatter.format(428_000.0, UnitPreference.KILOMETRES, cs).nbsp()).isEqualTo("0,43 km")
    }

    @Test
    fun englishUsesADecimalPoint() {
        assertThat(DistanceFormatter.format(2_840_000.0, UnitPreference.AUTOMATIC, Locale.US)).isEqualTo("2.84 km")
    }
}
