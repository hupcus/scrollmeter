package com.scrollmeter.app.measurement

import kotlin.math.hypot

/** First look at a sample: may it be measured at all, and does it carry its own delta? */
class ScrollEventValidator(
    private val ownPackage: String,
    private val settings: MeasurementSettings,
) {
    enum class Verdict { EXCLUDED, DIRECT_DELTA, NEEDS_FALLBACK }

    fun classify(sample: ScrollSample): Verdict {
        val pkg = sample.packageName
        return when {
            pkg.isNullOrBlank() -> Verdict.EXCLUDED
            pkg == ownPackage && !settings.includeOwnPackage -> Verdict.EXCLUDED
            pkg in settings.excludedPackages -> Verdict.EXCLUDED
            hasDirectDelta(sample) -> Verdict.DIRECT_DELTA
            else -> Verdict.NEEDS_FALLBACK
        }
    }

    /**
     * Spec §11: the whole event is rejected (never clipped) when its pixel vector is longer than
     * [DeviceGeometry.maxEventDistancePx].
     */
    fun isOutlier(dxPx: Int, dyPx: Int, geometry: DeviceGeometry): Boolean =
        hypot(dxPx.toDouble(), dyPx.toDouble()) > geometry.maxEventDistancePx

    private fun hasDirectDelta(sample: ScrollSample): Boolean {
        val undefined = sample.deltaX == MeasurementConfig.UNDEFINED_SCROLL_DELTA &&
            sample.deltaY == MeasurementConfig.UNDEFINED_SCROLL_DELTA
        return !undefined && (sample.deltaX != 0 || sample.deltaY != 0)
    }
}
