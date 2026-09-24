package com.scrollmeter.app.measurement

/**
 * Accessibility scroll delta represents content scroll displacement,
 * not the physical path travelled by the user's finger.
 *
 * Fling/inertial scrolling is intentionally included.
 *
 * Turns one [ScrollSample] into one [MeasurementResult] (spec §63):
 * validator → fallback tracker → physical scale → distance calculator → outlier check.
 * A position fallback is not counted while another view class of the same package delivers
 * direct deltas — that is a second stream reporting the same motion (ADR-020).
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

    private data class DirectMark(val uptimeMs: Long, val className: String?)

    /** The last direct delta per package, least recently used evicted first. */
    private val lastDirect = object : LinkedHashMap<String, DirectMark>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DirectMark>?): Boolean =
            size > MeasurementConfig.FALLBACK_TRACKER_MAX_KEYS
    }

    @Volatile
    var display: DisplayScale = display

    fun process(sample: ScrollSample): MeasurementResult {
        // One read: geometry, scale and its calibration version stay consistent for this event.
        val (geometry, scale) = display
        val version = scale.calibrationVersion
        val verdict = validator.classify(sample)
        if (verdict == ScrollEventValidator.Verdict.EXCLUDED) {
            return MeasurementResult(sample, MeasurementSource.EXCLUDED, 0, 0, ScrollDistance.ZERO, version)
        }
        // The tracker sees every measurable event, so a fallback always compares against the
        // latest known position, even when the previous event carried a direct delta.
        val fallback = fallbackTracker.update(sample, geometry.maxEventDistancePx)
        val packageName = sample.packageName.orEmpty()

        val source: MeasurementSource
        val dx: Int
        val dy: Int
        if (verdict == ScrollEventValidator.Verdict.DIRECT_DELTA) {
            source = MeasurementSource.DIRECT_DELTA
            dx = sample.deltaX
            dy = sample.deltaY
            lastDirect[packageName] = DirectMark(sample.uptimeMs, sample.className)
        } else if (fallback != null) {
            source = if (anotherStreamDeliversDeltas(packageName, sample)) {
                MeasurementSource.SUPERSEDED_BY_DIRECT
            } else {
                MeasurementSource.FALLBACK_POSITION
            }
            dx = fallback.dxPx
            dy = fallback.dyPx
        } else {
            // Spec §6 C: no pixel data, no guessing — never convert item indexes to distance.
            return MeasurementResult(sample, MeasurementSource.UNMEASURABLE, 0, 0, ScrollDistance.ZERO, version)
        }

        val distance = ScrollDistanceCalculator.distance(dx, dy, scale)
        val finalSource = if (source.counted && validator.isOutlier(dx, dy, geometry)) MeasurementSource.OUTLIER_REJECTED else source
        return MeasurementResult(sample, finalSource, dx, dy, distance, version)
    }

    /**
     * Within one view class the tracker already diffs against the position of the latest direct
     * event, so mixing is safe; only a different class of the same package is a second stream.
     */
    private fun anotherStreamDeliversDeltas(packageName: String, sample: ScrollSample): Boolean {
        val last = lastDirect[packageName] ?: return false
        return last.className != sample.className &&
            sample.uptimeMs - last.uptimeMs in 0..MeasurementConfig.DIRECT_SUPERSEDES_FALLBACK_MS
    }
}
