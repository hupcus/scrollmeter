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
import kotlinx.coroutines.sync.withLock
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
 *
 * What is summed but not yet committed — pending in the accumulator or in a write still running —
 * is published as [MeasurementMonitor.unflushed], so the UI shows every counted millimetre at once
 * instead of up to 10 s late.
 *
 * Flushes take [MeasurementMonitor.writeLock]; when "Smazat všechna data" raised
 * [MeasurementMonitor.dataEpoch], whatever the pipeline still holds is dropped instead of written
 * (ADR-031). [onFlushed] runs after every committed write — the notification check hangs there.
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
    private val onFlushed: () -> Unit = {},
) {
    private val accumulator = ScrollAccumulator(zone)
    private val sessions = ScrollSessionManager()
    private val inFlight = ArrayList<Map<Pair<String, String>, Double>>()
    private var epoch = monitor.dataEpoch.get()
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
        discardIfErased()
        val result = engine.process(sample)
        val due = if (sample.packageName == ownPackage) {
            false
        } else {
            sessions.add(result)
            accumulator.add(result).also { if (result.accepted) publishUnflushed() }
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
     * failed write puts everything back for the next flush (spec §61). Flushes never overlap: Room
     * writes on its own executor, which would otherwise let a second flush start meanwhile. The
     * consumer, the ticker and `onInterrupt` may all ask for one; they write one after another.
     */
    suspend fun flush() = monitor.writeLock.withLock {
        discardIfErased()
        sessions.closeIdle(uptimeMs(), nowMs())
        if (!accumulator.hasPending && !sessions.hasClosed) return@withLock
        val deltas = accumulator.drain()
        val closed = sessions.drainClosed()
        val writing = deltas.associate { (it.date to it.packageName) to it.distanceMm }
        inFlight += writing
        val written = try {
            withContext(NonCancellable) { store.write(deltas, closed) }
            true
        } catch (e: Exception) {
            accumulator.restore(deltas)
            sessions.restore(closed)
            monitor.processingFailures.incrementAndGet()
            false
        } finally {
            inFlight.remove(writing)
            publishUnflushed()
        }
        // Outside the write's try: a failing listener must never restore what was committed.
        if (written) {
            try {
                onFlushed()
            } catch (e: Exception) {
                monitor.processingFailures.incrementAndGet()
            }
        }
    }

    /** The data was erased since this pipeline last looked: drop what it holds (pipeline thread only). */
    private fun discardIfErased() {
        val current = monitor.dataEpoch.get()
        if (current == epoch) return
        epoch = current
        accumulator.drain()
        sessions.closeAll()
        sessions.drainClosed()
        publishUnflushed()
    }

    private fun publishUnflushed() {
        val total = HashMap(accumulator.pendingDistance())
        inFlight.forEach { batch -> batch.forEach { (key, mm) -> total.merge(key, mm, Double::plus) } }
        monitor.unflushed.value = total
    }

    private fun today(): String = Instant.ofEpochMilli(nowMs()).atZone(zone()).toLocalDate().toString()
}
