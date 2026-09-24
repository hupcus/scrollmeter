package com.scrollmeter.app.insights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Spec §22 and PLAN Phase 4: one fitting comparison, never a flood. */
class DistanceComparisonProviderTest {
    private fun m(metres: Double) = DistanceComparisonProvider.compare(metres * 1_000)

    @Test
    fun planExamples() {
        assertThat(m(428.0)).isEqualTo(DistanceComparison(Reference.RUNNING_TRACK_LAP, Qualifier.ABOUT, 1))
        assertThat(m(4_200.0)).isEqualTo(DistanceComparison(Reference.RUN_5K, Qualifier.ALMOST, 1))
        assertThat(m(42_300.0)).isEqualTo(DistanceComparison(Reference.MARATHON, Qualifier.ABOUT, 1))
    }

    @Test
    fun longerDistancesCountTheReference() {
        assertThat(m(1_200.0)).isEqualTo(DistanceComparison(Reference.RUNNING_TRACK_LAP, Qualifier.ABOUT, 3))
        assertThat(m(150_000.0)).isEqualTo(DistanceComparison(Reference.MARATHON, Qualifier.ABOUT, 4))
        assertThat(m(30_000.0)).isEqualTo(DistanceComparison(Reference.HALF_MARATHON, Qualifier.MORE_THAN, 1))
    }

    @Test
    fun tooShortOrInvalidHasNoComparison() {
        assertThat(m(80.0)).isNull()
        assertThat(m(0.0)).isNull()
        assertThat(DistanceComparisonProvider.compare(Double.NaN)).isNull()
        assertThat(m(90.0)).isEqualTo(DistanceComparison(Reference.FOOTBALL_FIELD, Qualifier.ALMOST, 1))
    }
}
