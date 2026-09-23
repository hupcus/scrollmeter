package com.scrollmeter.app.measurement

/**
 * Turns one [ScrollSample] into one [MeasurementResult] (spec §63):
 * validator → fallback tracker → physical scale → distance calculator → outlier check.
 *
 * Pure Kotlin, no Android types, so the whole pipeline runs as JVM unit tests. Call [process]
 * from a single thread (the service's consumer coroutine); [display] may be replaced from any
 * thread when the screen rotates or the calibration changes.
 */
class ScrollMeasurementEngine(
    ownPackage: String,
    settings: MeasurementSettings,
    display: DisplayScale,
    private val fallbackTracker: ScrollFallbackTracker = ScrollFallbackTracker(),
) {
    /** Geometry and scale travel together so a reader never sees one without the other. */
    data class DisplayScale(val geometry: DeviceGeometry, val scale: PhysicalScale)

    private val validator = ScrollEventValidator(ownPackage, settings)

    @Volatile
    var display: DisplayScale = display

    fun process(sample: ScrollSample): MeasurementResult {
        val verdict = validator.classify(sample)
        if (verdict == ScrollEventValidator.Verdict.EXCLUDED) {
            return MeasurementResult(sample, MeasurementSource.EXCLUDED, 0, 0, ScrollDistance.ZERO)
        }
        val (geometry, scale) = display
        // The tracker sees every measurable event, so a fallback always compares against the
        // latest known position, even when the previous event carried a direct delta.
        val fallback = fallbackTracker.update(sample, geometry.maxEventDistancePx)

        val source: MeasurementSource
        val dx: Int
        val dy: Int
        if (verdict == ScrollEventValidator.Verdict.DIRECT_DELTA) {
            source = MeasurementSource.DIRECT_DELTA
            dx = sample.deltaX
            dy = sample.deltaY
        } else if (fallback != null) {
            source = MeasurementSource.FALLBACK_POSITION
            dx = fallback.dxPx
            dy = fallback.dyPx
        } else {
            // Spec §6 C: no pixel data, no guessing — never convert item indexes to distance.
            return MeasurementResult(sample, MeasurementSource.UNMEASURABLE, 0, 0, ScrollDistance.ZERO)
        }

        val distance = ScrollDistanceCalculator.distance(dx, dy, scale)
        val finalSource = if (validator.isOutlier(dx, dy, geometry)) MeasurementSource.OUTLIER_REJECTED else source
        return MeasurementResult(sample, finalSource, dx, dy, distance)
    }
}
