package com.scrollmeter.app.measurement

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.measurement.ScrollEventValidator.Verdict
import org.junit.Test

/** Spec §57 outlier validator: normal accepted, huge rejected, zero → fallback; plus §11/§14 exclusions. */
class ScrollEventValidatorTest {
    private val settings = MeasurementSettings()
    private val validator = ScrollEventValidator(OWN_PACKAGE, settings)
    private val geometry = TestPhone.geometry

    @Test
    fun normalEventIsAccepted() {
        assertThat(validator.classify(sample(dy = 250))).isEqualTo(Verdict.DIRECT_DELTA)
        assertThat(validator.isOutlier(0, 250, geometry)).isFalse()
    }

    @Test
    fun hugeEventIsRejected() {
        assertThat(validator.isOutlier(0, 10_600, geometry)).isTrue()
        assertThat(validator.isOutlier(8_000, 8_000, geometry)).isTrue()
    }

    @Test
    fun eventExactlyAtTheLimitIsNotAnOutlier() {
        val limit = geometry.maxEventDistancePx.toInt()
        assertThat(validator.isOutlier(0, limit, geometry)).isFalse()
        assertThat(validator.isOutlier(0, limit + 1, geometry)).isTrue()
    }

    @Test
    fun zeroEventNeedsFallback() {
        assertThat(validator.classify(sample(dx = 0, dy = 0))).isEqualTo(Verdict.NEEDS_FALLBACK)
    }

    @Test
    fun undefinedDeltasNeedFallback() {
        assertThat(validator.classify(sample(dx = -1, dy = -1))).isEqualTo(Verdict.NEEDS_FALLBACK)
    }

    @Test
    fun onePixelScrollOnOneAxisIsARealDelta() {
        assertThat(validator.classify(sample(dx = 0, dy = -1))).isEqualTo(Verdict.DIRECT_DELTA)
        assertThat(validator.classify(sample(dx = -1, dy = 0))).isEqualTo(Verdict.DIRECT_DELTA)
    }

    @Test
    fun nullOrBlankPackageIsExcluded() {
        assertThat(validator.classify(sample(dy = 100, packageName = null))).isEqualTo(Verdict.EXCLUDED)
        assertThat(validator.classify(sample(dy = 100, packageName = " "))).isEqualTo(Verdict.EXCLUDED)
    }

    @Test
    fun ownPackageIsExcludedOutsideTestMode() {
        assertThat(validator.classify(sample(dy = 100, packageName = OWN_PACKAGE))).isEqualTo(Verdict.EXCLUDED)
        settings.enterTestMode()
        assertThat(validator.classify(sample(dy = 100, packageName = OWN_PACKAGE))).isEqualTo(Verdict.DIRECT_DELTA)
    }

    @Test
    fun userExcludedPackageIsExcluded() {
        settings.excludedPackages = setOf(APP)
        assertThat(validator.classify(sample(dy = 100))).isEqualTo(Verdict.EXCLUDED)
    }

    @Test
    fun testModeIsHeldUntilTheLastHolderLeaves() {
        settings.enterTestMode() // old screen
        settings.enterTestMode() // new screen, before the old one is disposed
        settings.exitTestMode() // old screen disposed late
        assertThat(validator.classify(sample(dy = 100, packageName = OWN_PACKAGE))).isEqualTo(Verdict.DIRECT_DELTA)
        settings.exitTestMode()
        settings.exitTestMode() // an extra exit never goes below zero
        assertThat(validator.classify(sample(dy = 100, packageName = OWN_PACKAGE))).isEqualTo(Verdict.EXCLUDED)
        settings.enterTestMode()
        assertThat(validator.classify(sample(dy = 100, packageName = OWN_PACKAGE))).isEqualTo(Verdict.DIRECT_DELTA)
    }
}
