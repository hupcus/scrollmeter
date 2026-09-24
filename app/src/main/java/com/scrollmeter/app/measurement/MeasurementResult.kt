package com.scrollmeter.app.measurement

enum class MeasurementSource {
    /** `scrollDeltaX/Y` reported by the app (spec §6 A). */
    DIRECT_DELTA,

    /** Difference of `scrollX/Y` against the previous event of the same key (spec §6 B). */
    FALLBACK_POSITION,

    /**
     * A valid position fallback while another view class of the same package delivered direct deltas within
     * [MeasurementConfig.DIRECT_SUPERSEDES_FALLBACK_MS] — the same motion was already counted (ADR-020).
     */
    SUPERSEDED_BY_DIRECT,

    /** A scroll event without usable pixel data — counts as 0, never estimated (spec §6 C). */
    UNMEASURABLE,

    /** Larger than [MeasurementConfig.MAX_EVENT_DISTANCE_FACTOR] × screen diagonal — not counted (spec §11). */
    OUTLIER_REJECTED,

    /** No package, our own package outside test mode, or a user-excluded package (spec §11, §14). */
    EXCLUDED,
    ;

    val counted: Boolean get() = this == DIRECT_DELTA || this == FALLBACK_POSITION
}

/** Physical distance of one event; horizontal/vertical are kept for future analytics (spec §13). */
data class ScrollDistance(
    val totalMm: Double,
    val horizontalMm: Double,
    val verticalMm: Double,
) {
    companion object {
        val ZERO = ScrollDistance(0.0, 0.0, 0.0)
    }
}

/**
 * The engine's verdict on one sample. [dxPx]/[dyPx] are the pixels the verdict is based on
 * (direct or fallback); for OUTLIER_REJECTED they and [distance] are kept for analysis but
 * [accepted] is false, so nothing is added to any total. [calibrationVersion] is the calibration
 * [distance] was computed under — a later recalibration never changes it (spec §65, ADR-007).
 */
data class MeasurementResult(
    val sample: ScrollSample,
    val source: MeasurementSource,
    val dxPx: Int,
    val dyPx: Int,
    val distance: ScrollDistance,
    val calibrationVersion: Int,
) {
    val accepted: Boolean get() = source.counted
}
