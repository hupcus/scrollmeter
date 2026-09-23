package com.scrollmeter.app.devtools

import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.MeasurementResult
import com.scrollmeter.app.measurement.MeasurementSink
import com.scrollmeter.app.measurement.MeasurementSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Counts since the last Clear, per source. */
data class DebugCounts(
    val bySource: Map<MeasurementSource, Long> = emptyMap(),
    val recorded: Int = 0,
    val overflowed: Long = 0L,
) {
    val total: Long get() = bySource.values.sum()
}

/**
 * Debug-only event log in RAM (spec §34): the newest [visibleCapacity] results for the screen,
 * and up to [recordingCapacity] for the CSV export of one app run. Results hold primitives only.
 * Nothing is written to disk until the user presses Export.
 */
class DebugEventLog(
    private val ownPackage: String,
    private val visibleCapacity: Int = MeasurementConfig.DEBUG_LOG_VISIBLE_EVENTS,
    private val recordingCapacity: Int = MeasurementConfig.DEBUG_LOG_RECORDING_EVENTS,
) : MeasurementSink {
    private val lock = Any()
    private val recording = ArrayDeque<MeasurementResult>()
    private var bySource = mutableMapOf<MeasurementSource, Long>()
    private var overflowed = 0L

    private val _visible = MutableStateFlow<List<MeasurementResult>>(emptyList())

    /** Newest first. */
    val visible: StateFlow<List<MeasurementResult>> = _visible.asStateFlow()

    private val _counts = MutableStateFlow(DebugCounts())
    val counts: StateFlow<DebugCounts> = _counts.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    override fun onResult(result: MeasurementResult) {
        if (_paused.value) return
        // Scrolling this very screen produces our own EXCLUDED events; they would only push the
        // interesting rows away. In test mode our events are accepted and do show up.
        if (result.source == MeasurementSource.EXCLUDED && result.sample.packageName == ownPackage) return
        synchronized(lock) {
            recording.addLast(result)
            if (recording.size > recordingCapacity) {
                recording.removeFirst()
                overflowed++
            }
            bySource[result.source] = (bySource[result.source] ?: 0L) + 1
            _visible.value = recording.takeLast(visibleCapacity).asReversed()
            _counts.value = DebugCounts(bySource.toMap(), recording.size, overflowed)
        }
    }

    fun setPaused(paused: Boolean) {
        _paused.value = paused
    }

    fun clear() {
        synchronized(lock) {
            recording.clear()
            bySource = mutableMapOf()
            overflowed = 0L
            _visible.value = emptyList()
            _counts.value = DebugCounts()
        }
    }

    /** Oldest first, for export. */
    fun snapshot(): List<MeasurementResult> = synchronized(lock) { recording.toList() }
}
