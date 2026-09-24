package com.scrollmeter.app.aggregation

import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.MeasurementMonitor
import com.scrollmeter.app.measurement.MeasurementSink
import com.scrollmeter.app.measurement.ScrollMeasurementEngine
import com.scrollmeter.app.measurement.ScrollSample
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The accessibility service's pipeline without Android (spec §16, §62, §63, D5): the callback
 * [offer]s samples into a bounded channel; [run] consumes them, sums them in memory and writes a
 * flush when 50 events or a new day make one due, every 10 s while anything is pending, and once
 * more on the way out — whether [close] ended the channel or the caller was cancelled.
 *
 * [run] must execute on a single-threaded dispatcher: the accumulator and the session manager are
 * not thread-safe. A killed process loses at most the last [MeasurementConfig.FLUSH_INTERVAL_MS].
 *
 * [ownPackage] is never stored: outside test mode the engine already excludes it (D18); inside
 * test mode (debug test list, spec §35) it is measured for the live comparison only.
 */
class ScrollPipeline(
    private val engine: ScrollMeasurementEngine,
    private val ownPackage: String,
    private val store: AggregateStore,
    private val monitor: MeasurementMonitor,
    private val sinks: List<MeasurementSink>,
    private val uptimeMs: () -> Long,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val onDayChanged: () -> Unit = {},
) {
    private val accumulator = ScrollAccumulator(zone)
    private val sessions = ScrollSessionManager()
    private val samples = Channel<ScrollSample>(
        capacity = MeasurementConfig.SAMPLE_CHANNEL_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { monitor.droppedSamples.incrementAndGet() },
    )

    /** From the accessibility callback: never blocks; overflow drops the oldest sample. */
    fun offer(sample: ScrollSample) {
        samples.trySend(sample)
    }

    /** No more samples: [run] processes what is queued, flushes and returns. */
    fun close() {
        samples.close()
    }

    suspend fun run() = coroutineScope {
        val ticker = launch { tick() }
        try {
            for (sample in samples) {
                val due = try {
                    process(sample)
                } catch (e: Exception) {
                    // Spec §61: one bad sample must not stop the consumer.
                    monitor.processingFailures.incrementAndGet()
                    false
                }
                if (due) flush()
            }
        } finally {
            ticker.cancel()
            withContext(NonCancellable) {
                sessions.closeAll()
                flush()
            }
        }
    }

    /** True when a flush is due. Stored data first, so a failing debug sink cannot lose it. */
    private fun process(sample: ScrollSample): Boolean {
        val result = engine.process(sample)
        val due = if (sample.packageName == ownPackage) {
            false
        } else {
            sessions.add(result)
            accumulator.add(result)
        }
        monitor.record(result)
        sinks.forEach { it.onResult(result) }
        return due
    }

    private suspend fun tick() {
        var day = today()
        while (true) {
            delay(MeasurementConfig.FLUSH_INTERVAL_MS)
            flush()
            val now = today()
            if (now != day) {
                day = now
                onDayChanged()
            }
        }
    }

    /**
     * Writes what is pending, sessions idle for over a minute included. The write itself is not
     * cancellable — a transaction that committed must not be restored and written twice — and a
     * failed write puts everything back for the next flush (spec §61).
     */
    suspend fun flush() {
        sessions.closeIdle(uptimeMs())
        if (!accumulator.hasPending && !sessions.hasClosed) return
        val deltas = accumulator.drain()
        val closed = sessions.drainClosed()
        try {
            withContext(NonCancellable) { store.write(deltas, closed) }
        } catch (e: Exception) {
            accumulator.restore(deltas)
            sessions.restore(closed)
            monitor.processingFailures.incrementAndGet()
        }
    }

    private fun today(): String = Instant.ofEpochMilli(nowMs()).atZone(zone()).toLocalDate().toString()
}
