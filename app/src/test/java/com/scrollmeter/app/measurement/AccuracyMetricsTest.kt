package com.scrollmeter.app.measurement

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Spec §38 accuracy metrics as shown on the debug test list. */
class AccuracyMetricsTest {
    @Test
    fun maeAndMapeOverRuns() {
        val summary = AccuracySummary.of(listOf(AccuracyRun(100.0, 98.0), AccuracyRun(200.0, 210.0)))
        assertThat(summary.runs).isEqualTo(2)
        assertThat(summary.maeMm!!).isWithin(1e-12).of(6.0) // (2 + 10) / 2
        assertThat(summary.mapePercent!!).isWithin(1e-12).of(3.5) // (2 % + 5 %) / 2
    }

    @Test
    fun aRunWithoutGroundTruthCountsForMaeButNotForMape() {
        val summary = AccuracySummary.of(listOf(AccuracyRun(0.0, 5.0), AccuracyRun(100.0, 99.0)))
        assertThat(summary.maeMm!!).isWithin(1e-12).of(3.0)
        assertThat(summary.mapePercent!!).isWithin(1e-12).of(1.0)
    }

    @Test
    fun emptySeriesHasNoNumbers() {
        assertThat(AccuracySummary.of(emptyList())).isEqualTo(AccuracySummary(0, null, null))
    }
}
