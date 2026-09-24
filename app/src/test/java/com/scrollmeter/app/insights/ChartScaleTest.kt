package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** D13: round axis maxima and grid lines. */
class ChartScaleTest {
    @Test
    fun roundMaximaWithAboutFourIntervals() {
        val s = ChartScale.of(423.0, 1.0)
        assertThat(s.step).isEqualTo(200.0)
        assertThat(s.max).isEqualTo(600.0)
        assertThat(s.gridLines).containsExactly(0.0, 200.0, 400.0, 600.0).inOrder()
        assertThat(ChartScale.of(1000.0, 1.0)).isEqualTo(ChartScale(1000.0, 250.0))
        assertThat(ChartScale.of(2_840_000.0, 1.0)).isEqualTo(ChartScale(3_000_000.0, 1_000_000.0))
        assertThat(ChartScale.of(7.0, 1.0)).isEqualTo(ChartScale(8.0, 2.0))
    }

    @Test
    fun emptyDataUsesTheGivenAxis() {
        assertThat(ChartScale.of(0.0, 1_000.0)).isEqualTo(ChartScale(1_000.0, 250.0))
        assertThat(ChartScale.of(Double.NaN, 1_000.0)).isEqualTo(ChartScale(1_000.0, 250.0))
    }

    @Test
    fun fractionIsClipped() {
        val s = ChartScale(100.0, 25.0)
        assertThat(s.fraction(50.0)).isEqualTo(0.5f)
        assertThat(s.fraction(150.0)).isEqualTo(1f)
        assertThat(s.fraction(-1.0)).isEqualTo(0f)
    }

    @Test
    fun minuteAxesUseClockSteps() {
        assertThat(ChartScale.ofMinutes(47.0, 10.0)).isEqualTo(ChartScale(60.0, 15.0))
        assertThat(ChartScale.ofMinutes(9.0, 10.0)).isEqualTo(ChartScale(10.0, 5.0))
        assertThat(ChartScale.ofMinutes(200.0, 10.0)).isEqualTo(ChartScale(240.0, 60.0))
        assertThat(ChartScale.ofMinutes(0.0, 10.0)).isEqualTo(ChartScale(10.0, 5.0))
        assertThat(ChartScale.ofMinutes(9_000.0, 10.0)).isEqualTo(ChartScale(9_360.0, 720.0))
    }
}
