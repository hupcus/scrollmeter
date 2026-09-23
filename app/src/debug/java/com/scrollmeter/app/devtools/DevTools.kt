package com.scrollmeter.app.devtools

import com.scrollmeter.app.AppGraph
import com.scrollmeter.app.R
import com.scrollmeter.app.measurement.MeasurementSink

/** Debug build: the event log, logcat, and the two developer screens (spec §34, §35). */
object DevTools {
    @Volatile
    private var eventLog: DebugEventLog? = null

    /** One log per process, shared by the service (writer) and the debug screen (reader). */
    fun eventLog(graph: AppGraph): DebugEventLog =
        eventLog ?: synchronized(this) {
            eventLog ?: DebugEventLog(graph.ownPackage).also { eventLog = it }
        }

    val entries: List<DevToolEntry> = listOf(
        DevToolEntry(R.string.devtools_debug_title) { graph, onBack ->
            DebugMeasurementScreen(graph, eventLog(graph), onBack)
        },
        DevToolEntry(R.string.devtools_testlist_title) { graph, onBack ->
            TestListScreen(graph, onBack)
        },
    )

    fun measurementSinks(graph: AppGraph): List<MeasurementSink> = listOf(eventLog(graph), LogcatSink())
}
