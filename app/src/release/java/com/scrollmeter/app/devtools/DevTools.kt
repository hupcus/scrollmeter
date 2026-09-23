package com.scrollmeter.app.devtools

import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.measurement.MeasurementSink

/** Release build: no developer screens, no event log, no logging (ADR-010). */
object DevTools {
    val entries: List<DevToolEntry> = emptyList()

    fun measurementSinks(graph: AppGraph): List<MeasurementSink> = emptyList()
}
