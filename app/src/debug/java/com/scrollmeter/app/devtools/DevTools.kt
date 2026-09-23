package com.scrollmeter.app.devtools

import android.content.Intent
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
        DevToolEntry("debug", R.string.devtools_debug_title) { graph, onBack ->
            DebugMeasurementScreen(graph, eventLog(graph), onBack)
        },
        DevToolEntry("testlist", R.string.devtools_testlist_title) { graph, onBack ->
            TestListScreen(graph, onBack)
        },
    )

    fun measurementSinks(graph: AppGraph): List<MeasurementSink> = listOf(eventLog(graph), LogcatSink())

    /**
     * `adb shell am start -n <app>/com.scrollmeter.app.MainActivity -f 0x10008000 --es devtool testlist`
     * opens a tool without tapping — adb scripts must not read the screen with `uiautomator dump`
     * while measuring, because it unbinds every accessibility service.
     */
    fun toolFromLaunch(intent: Intent?): Int = entries.indexOfFirst { it.key == intent?.getStringExtra(EXTRA_DEVTOOL) }

    private const val EXTRA_DEVTOOL = "devtool"
}
