package com.scrollmeter.app.data

import com.scrollmeter.app.measurement.MeasurementMonitor
import kotlinx.coroutines.sync.withLock

/**
 * "Smazat všechna data" (spec §45, ADR-031). Deletes every measured value — daily aggregates, time
 * in app, sessions — and optionally the settings and the card calibration too. Pure Kotlin; the
 * Android pieces come in as functions.
 *
 * Nothing deleted may come back:
 * - the pipeline's pending millimetres: the tables are cleared inside [MeasurementMonitor.writeLock]
 *   with [MeasurementMonitor.dataEpoch] raised, so a flush either committed before the clear or
 *   sees the new epoch and drops what it holds;
 * - time in app: [setFloor] runs first and the usage sync never imports events before the floor;
 *   the whole erase runs inside [usageExclusive], so a sync already running finishes first.
 */
class DataEraser(
    private val monitor: MeasurementMonitor,
    private val clearTables: suspend () -> Unit,
    private val clearSettings: suspend () -> Unit,
    private val forgetCalibration: suspend () -> Unit,
    private val setFloor: suspend (Long) -> Unit,
    private val deleteFiles: suspend () -> Unit,
    private val usageExclusive: suspend (suspend () -> Unit) -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun erase(alsoSettings: Boolean) {
        usageExclusive {
            if (alsoSettings) {
                clearSettings()
                forgetCalibration()
            }
            setFloor(nowMs())
            monitor.writeLock.withLock {
                monitor.dataEpoch.incrementAndGet()
                clearTables()
            }
        }
        monitor.unflushed.value = emptyMap()
        deleteFiles()
    }
}
