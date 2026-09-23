package com.scrollmeter.app.measurement

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** End-to-end over synthetic sequences: validator → fallback → scale → calculator → outlier (spec §63). */
class ScrollMeasurementEngineTest {
    private val settings = MeasurementSettings()
    private val engine = ScrollMeasurementEngine(OWN_PACKAGE, settings, TestPhone.display)

    private fun List<ScrollSample>.measure() = map(engine::process)

    @Test
    fun directDeltasAreSummedWithTheTestPhoneScale() {
        val results = List(10) { i -> sample(dy = 120, uptimeMs = 100L * i) }.measure()
        assertThat(results.map { it.source }.toSet()).containsExactly(MeasurementSource.DIRECT_DELTA)
        // 10 × 120 px = 1200 px ≈ 76.0 mm on the test phone (PLAN §2).
        assertThat(results.sumOf { it.distance.totalMm }).isWithin(0.05).of(76.0)
    }

    /** Spec §36 Test E: down 1000 px then up 1000 px is 2000 px of distance, not 0. */
    @Test
    fun reversalAddsUp() {
        val results = listOf(sample(dy = 1000, uptimeMs = 0), sample(dy = -1000, uptimeMs = 500)).measure()
        val mm = results.sumOf { it.distance.totalMm }
        assertThat(mm).isWithin(1e-9).of(2000 * TestPhone.scale.mmPerPxY)
    }

    @Test
    fun appWithoutDeltasIsMeasuredThroughPositionFallback() {
        val results = listOf(
            positionOnlySample(scrollY = 0, uptimeMs = 0),
            positionOnlySample(scrollY = 300, uptimeMs = 100),
            positionOnlySample(scrollY = 700, uptimeMs = 200),
        ).measure()
        assertThat(results.map { it.source }).containsExactly(
            MeasurementSource.UNMEASURABLE,
            MeasurementSource.FALLBACK_POSITION,
            MeasurementSource.FALLBACK_POSITION,
        ).inOrder()
        assertThat(results.filter { it.accepted }.sumOf { it.dyPx }).isEqualTo(700)
    }

    @Test
    fun fallbackComparesAgainstTheLatestPositionEvenAfterADirectEvent() {
        val results = listOf(
            sample(dy = -1, dx = -1, scrollY = 100, uptimeMs = 0),
            sample(dy = 50, scrollY = 150, uptimeMs = 100),
            sample(dy = -1, dx = -1, scrollY = 400, uptimeMs = 200),
        ).measure()
        assertThat(results[2].source).isEqualTo(MeasurementSource.FALLBACK_POSITION)
        assertThat(results[2].dyPx).isEqualTo(250)
    }

    @Test
    fun zeroDeltaWithoutPositionIsUnmeasurableAndCountsNothing() {
        // RecyclerView keeps scrollY at 0 — no fallback possible, never guess (spec §6 C).
        val result = engine.process(sample(dx = 0, dy = 0))
        assertThat(result.source).isEqualTo(MeasurementSource.UNMEASURABLE)
        assertThat(result.accepted).isFalse()
        assertThat(result.distance).isEqualTo(ScrollDistance.ZERO)
    }

    @Test
    fun outlierIsRejectedWholeButKeptForAnalysis() {
        val result = engine.process(sample(dy = 12_000))
        assertThat(result.source).isEqualTo(MeasurementSource.OUTLIER_REJECTED)
        assertThat(result.accepted).isFalse()
        assertThat(result.dyPx).isEqualTo(12_000)
        // Not clipped to the limit: the full distance is reported for the debug CSV.
        assertThat(result.distance.totalMm).isWithin(1e-6).of(12_000 * TestPhone.scale.mmPerPxY)
    }

    @Test
    fun ownPackageIsExcludedUnlessTestModeIsOn() {
        assertThat(engine.process(sample(dy = 100, packageName = OWN_PACKAGE)).source).isEqualTo(MeasurementSource.EXCLUDED)
        settings.includeOwnPackage = true
        assertThat(engine.process(sample(dy = 100, packageName = OWN_PACKAGE)).source).isEqualTo(MeasurementSource.DIRECT_DELTA)
    }

    @Test
    fun excludedEventsDoNotFeedTheFallbackTracker() {
        settings.excludedPackages = setOf(APP)
        engine.process(positionOnlySample(scrollY = 0, uptimeMs = 0))
        settings.excludedPackages = emptySet()
        // Had the excluded event been tracked, this would be a 500 px fallback.
        assertThat(engine.process(positionOnlySample(scrollY = 500, uptimeMs = 100)).source)
            .isEqualTo(MeasurementSource.UNMEASURABLE)
    }

    @Test
    fun displayChangeAppliesToTheNextEvent() {
        engine.display = ScrollMeasurementEngine.DisplayScale(TestPhone.geometry, uniformScale(0.1))
        assertThat(engine.process(sample(dy = 100)).distance.totalMm).isWithin(1e-9).of(10.0)
    }

    @Test
    fun monitorCountsOnlyAcceptedDistance() {
        val monitor = MeasurementMonitor(OWN_PACKAGE)
        settings.includeOwnPackage = true
        listOf(
            sample(dy = 1200),
            sample(dy = 20_000),
            sample(dx = 0, dy = 0),
            sample(dx = 300, packageName = OWN_PACKAGE),
            sample(dy = 50, packageName = null),
        ).measure().forEach(monitor::record)

        val totals = monitor.totals.value
        assertThat(totals.events).isEqualTo(5)
        assertThat(totals.direct).isEqualTo(2)
        assertThat(totals.outliers).isEqualTo(1)
        assertThat(totals.unmeasurable).isEqualTo(1)
        assertThat(totals.excluded).isEqualTo(1)
        val expected = 1200 * TestPhone.scale.mmPerPxY + 300 * TestPhone.scale.mmPerPxX
        assertThat(totals.countedMm).isWithin(1e-9).of(expected)

        val self = monitor.selfTest.value
        assertThat(self.absDxPx).isEqualTo(300)
        assertThat(self.absDyPx).isEqualTo(0)
        assertThat(self.events).isEqualTo(1)
    }
}
