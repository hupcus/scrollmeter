package com.scrollmeter.app.measurement

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.sqrt

/** Spec §57 DistanceCalculator cases, §58 and §59 worked examples. */
class ScrollDistanceCalculatorTest {
    private val scale = uniformScale(0.06)
    private val tolerance = 1e-9

    @Test
    fun verticalOnly() {
        val d = ScrollDistanceCalculator.distance(0, 1000, scale)
        assertThat(d.totalMm).isWithin(tolerance).of(60.0)
        assertThat(d.verticalMm).isWithin(tolerance).of(60.0)
        assertThat(d.horizontalMm).isWithin(tolerance).of(0.0)
    }

    @Test
    fun horizontalOnly() {
        val d = ScrollDistanceCalculator.distance(1000, 0, scale)
        assertThat(d.totalMm).isWithin(tolerance).of(60.0)
        assertThat(d.horizontalMm).isWithin(tolerance).of(60.0)
        assertThat(d.verticalMm).isWithin(tolerance).of(0.0)
    }

    @Test
    fun diagonalIsTheVectorLengthNotTheSum() {
        val d = ScrollDistanceCalculator.distance(1000, 1000, scale)
        assertThat(d.totalMm).isWithin(tolerance).of(60.0 * sqrt(2.0))
    }

    @Test
    fun negativeDxCountsAsDistance() {
        assertThat(ScrollDistanceCalculator.distance(-1000, 0, scale).totalMm).isWithin(tolerance).of(60.0)
        assertThat(ScrollDistanceCalculator.distance(-1000, 0, scale).horizontalMm).isWithin(tolerance).of(60.0)
    }

    @Test
    fun negativeDyCountsAsDistance() {
        assertThat(ScrollDistanceCalculator.distance(0, -1000, scale).totalMm).isWithin(tolerance).of(60.0)
        assertThat(ScrollDistanceCalculator.distance(0, -1000, scale).verticalMm).isWithin(tolerance).of(60.0)
    }

    @Test
    fun zeroMovementIsZero() {
        assertThat(ScrollDistanceCalculator.distance(0, 0, scale)).isEqualTo(ScrollDistance.ZERO)
    }

    /** Spec §58: 420 dpi, dy = 1200 px → 72.5714 mm; 100 such events → 7.257 m. */
    @Test
    fun specExample58() {
        val scale420 = PhysicalScaleProvider.fromDisplayMetrics(420.0, 420.0, 420)
        assertThat(scale420.mmPerPxY).isWithin(1e-10).of(0.0604761905)

        val one = ScrollDistanceCalculator.distance(0, 1200, scale420).totalMm
        assertThat(one).isWithin(1e-4).of(72.5714)

        val hundred = (1..100).sumOf { ScrollDistanceCalculator.distance(0, 1200, scale420).totalMm }
        assertThat(hundred / 1000.0).isWithin(1e-3).of(7.257)
    }

    /** Spec §59: 300/400 px at 0.06 mm/px is 30 mm — not 18 + 24 = 42 mm. */
    @Test
    fun specExample59() {
        val d = ScrollDistanceCalculator.distance(300, 400, scale)
        assertThat(d.horizontalMm).isWithin(tolerance).of(18.0)
        assertThat(d.verticalMm).isWithin(tolerance).of(24.0)
        assertThat(d.totalMm).isWithin(tolerance).of(30.0)
        assertThat(d.totalMm).isNotWithin(1.0).of(42.0)
    }

    /** Spec §7: 10 cm down then 10 cm up is 20 cm of scroll distance, not 0. */
    @Test
    fun directionChangesAddUp() {
        val down = ScrollDistanceCalculator.distance(0, 1000, scale).totalMm
        val up = ScrollDistanceCalculator.distance(0, -1000, scale).totalMm
        assertThat(down + up).isWithin(tolerance).of(120.0)
    }
}
