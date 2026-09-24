package com.scrollmeter.app.aggregation

import com.scrollmeter.app.measurement.APP
import com.scrollmeter.app.measurement.MeasurementResult
import com.scrollmeter.app.measurement.MeasurementSource
import com.scrollmeter.app.measurement.ScrollDistance
import com.scrollmeter.app.measurement.sample
import java.time.ZoneOffset

/** 2026-09-21T14:13:20Z — `sample()`'s wall clock is this plus its uptime. */
const val BASE_WALL_MS = 1_790_000_000_000L

/** 2026-09-22T00:00:00Z. */
const val NEXT_MIDNIGHT_UTC_MS = 1_790_035_200_000L

val UTC: ZoneOffset = ZoneOffset.UTC

/** An engine verdict built directly: [mm] vertical millimetres for a counted source, else nothing. */
fun result(
    source: MeasurementSource = MeasurementSource.DIRECT_DELTA,
    uptimeMs: Long = 1_000L,
    packageName: String? = APP,
    mm: Double = 10.0,
    wallMs: Long = BASE_WALL_MS + uptimeMs,
    calibrationVersion: Int = 0,
): MeasurementResult {
    val counted = source.counted || source == MeasurementSource.OUTLIER_REJECTED
    return MeasurementResult(
        sample = sample(dy = 100, packageName = packageName, uptimeMs = uptimeMs).copy(wallTimeMs = wallMs),
        source = source,
        dxPx = 0,
        dyPx = if (counted) 100 else 0,
        distance = if (counted) ScrollDistance(mm, 0.0, mm) else ScrollDistance.ZERO,
        calibrationVersion = calibrationVersion,
    )
}
