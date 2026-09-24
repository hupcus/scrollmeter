package com.scrollmeter.app.data

import com.scrollmeter.app.measurement.MeasurementMonitor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * "Smazat všechna data" (spec §45, ADR-031). Deletes every measured value — daily aggregates, time
 * in app, sessions — and optionally the settings and the card calibration too. Pure Kotlin; the
 * Android pieces come in as functions.
 *
 * Nothing deleted may come back:
 * - the pipeline's pending millimetres: everything below runs inside [MeasurementMonitor.writeLock]
 *   with [MeasurementMonitor.dataEpoch] raised first, so a flush either committed before the erase or
 *   sees the new epoch and drops what it holds;
 * - a notification about deleted data: the notification check posts under the same lock and only
 *   while the epoch it read its facts at still holds; one posted just before is cancelled at the end
 *   by [clearLeftovers], and its "already posted today" mark goes with the settings;
 * - time in app: [setFloor] runs before the tables are cleared and the usage sync never imports events
 *   before the floor; the whole erase runs inside [usageExclusive], so a sync already running finishes
 *   first. Settings are cleared before the floor is set — clearing them would drop the floor;
 * - a half-done erase: it cannot be cancelled once started (the screen that asked may be gone —
 *   back pressed, phone rotated — before it ends), and a step that fails does not stop the others.
 *
 * [clearLeftovers] removes what lives outside the stores: shared CSV copies, posted notifications. It
 * runs first as well, so a process killed mid-erase leaves no shared copy behind.
 */
class DataEraser(
    private val monitor: MeasurementMonitor,
    private val clearTables: suspend () -> Unit,
    private val clearSettings: suspend () -> Unit,
    private val forgetCalibration: suspend () -> Unit,
    private val setFloor: suspend (Long) -> Unit,
    private val clearLeftovers: suspend () -> Unit,
    private val usageExclusive: suspend (suspend () -> Unit) -> Unit,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    /** False when a step failed (spec §61: a storage error) — the user is told and can run it again. */
    suspend fun erase(alsoSettings: Boolean): Boolean = withContext(NonCancellable) {
        var ok = true
        suspend fun step(block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ok = false
            }
        }
        step { clearLeftovers() }
        usageExclusive {
            monitor.writeLock.withLock {
                monitor.dataEpoch.incrementAndGet()
                if (alsoSettings) {
                    step { clearSettings() }
                    step { forgetCalibration() }
                }
                step { setFloor(nowMs()) }
                step { clearTables() }
            }
        }
        monitor.unflushed.value = emptyMap()
        step { clearLeftovers() }
        ok
    }
}
