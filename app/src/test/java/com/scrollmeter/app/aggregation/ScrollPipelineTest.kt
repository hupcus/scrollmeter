package com.scrollmeter.app.aggregation

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.data.DataEraser
import com.scrollmeter.app.measurement.MeasurementConfig
import com.scrollmeter.app.measurement.MeasurementMonitor
import com.scrollmeter.app.measurement.MeasurementSettings
import com.scrollmeter.app.measurement.OWN_PACKAGE
import com.scrollmeter.app.measurement.ScrollMeasurementEngine
import com.scrollmeter.app.measurement.TestPhone
import com.scrollmeter.app.measurement.sample
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Spec §16: when the service's pipeline writes, and that nothing is lost on the way out. Virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScrollPipelineTest {
    private class FakeStore : AggregateStore {
        val writes = mutableListOf<Pair<List<AggregateDelta>, List<ClosedSession>>>()
        var failures = 0
        var writeDurationMs = 0L
        private var writing = 0
        var maxConcurrentWrites = 0

        override suspend fun write(deltas: List<AggregateDelta>, sessions: List<ClosedSession>) {
            writing++
            maxConcurrentWrites = maxOf(maxConcurrentWrites, writing)
            try {
                if (writeDurationMs > 0) kotlinx.coroutines.delay(writeDurationMs) // Room writes elsewhere; the caller suspends
                record(deltas, sessions)
            } finally {
                writing--
            }
        }

        private fun record(deltas: List<AggregateDelta>, sessions: List<ClosedSession>) {
            if (failures > 0) {
                failures--
                throw IOException("disk full")
            }
            writes += deltas to sessions
        }

        val distanceMm: Double get() = writes.sumOf { (d, _) -> d.sumOf { it.distanceMm } }
        val sessions: List<ClosedSession> get() = writes.flatMap { it.second }
    }

    private val store = FakeStore()
    private val monitor = MeasurementMonitor(OWN_PACKAGE)
    private val settings = MeasurementSettings()
    private val engine = ScrollMeasurementEngine(OWN_PACKAGE, settings, TestPhone.display)
    private val mmPer100Px = 100 * TestPhone.scale.mmPerPxY

    private fun TestScope.pipeline(wallStartMs: Long = BASE_WALL_MS, onDayChanged: () -> Unit = {}, onFlushed: () -> Unit = {}) = ScrollPipeline(
        engine = engine,
        ownPackage = OWN_PACKAGE,
        store = store,
        monitor = monitor,
        sinks = emptyList(),
        uptimeMs = { testScheduler.currentTime },
        nowMs = { wallStartMs + testScheduler.currentTime },
        zone = { UTC },
        onDayChanged = onDayChanged,
        onFlushed = onFlushed,
    )

    @Test
    fun aQuietPipelineWritesWithinTenSeconds() = runTest {
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        repeat(3) { pipeline.offer(sample(dy = 100, uptimeMs = it.toLong())) }
        runCurrent()
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS - 1)
        assertThat(store.writes).isEmpty()
        advanceTimeBy(2)
        assertThat(store.writes).hasSize(1)
        assertThat(store.distanceMm).isWithin(1e-9).of(3 * mmPer100Px)
        job.cancel()
    }

    @Test
    fun nothingPendingMeansNoWrite() = runTest {
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        advanceTimeBy(5 * MeasurementConfig.FLUSH_INTERVAL_MS)
        assertThat(store.writes).isEmpty()
        job.cancel()
    }

    @Test
    fun theFiftiethEventWritesAtOnce() = runTest {
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        repeat(MeasurementConfig.FLUSH_EVENT_COUNT) { pipeline.offer(sample(dy = 100, uptimeMs = it.toLong())) }
        runCurrent()
        assertThat(store.writes).hasSize(1)
        assertThat(store.writes.single().first.single().measuredEventCount).isEqualTo(MeasurementConfig.FLUSH_EVENT_COUNT.toLong())
        job.cancel()
    }

    @Test
    fun closingProcessesTheQueueAndWritesEverythingWithTheOpenSession() = runTest {
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        repeat(5) { pipeline.offer(sample(dy = 100, uptimeMs = it.toLong())) }
        pipeline.close()
        job.join()
        assertThat(store.distanceMm).isWithin(1e-9).of(5 * mmPer100Px)
        assertThat(store.sessions.single().eventCount).isEqualTo(5)
    }

    @Test
    fun cancellationStillWritesAFinalFlush() = runTest {
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        pipeline.offer(sample(dy = 100, uptimeMs = 1))
        runCurrent()
        job.cancel()
        job.join()
        assertThat(store.distanceMm).isWithin(1e-9).of(mmPer100Px)
    }

    @Test
    fun aFailedWriteIsRetriedByTheNextFlush() = runTest {
        store.failures = 1
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        pipeline.offer(sample(dy = 100, uptimeMs = 1))
        runCurrent()
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS + 1)
        assertThat(store.writes).isEmpty()
        assertThat(monitor.processingFailures.get()).isEqualTo(1)
        pipeline.offer(sample(dy = 100, uptimeMs = MeasurementConfig.FLUSH_INTERVAL_MS + 2))
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS)
        assertThat(store.writes).hasSize(1)
        assertThat(store.distanceMm).isWithin(1e-9).of(2 * mmPer100Px)
        job.cancel()
    }

    @Test
    fun anIdleSessionIsWrittenByTheTicker() = runTest {
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        pipeline.offer(sample(dy = 100, uptimeMs = 0))
        runCurrent()
        advanceTimeBy(MeasurementConfig.SCROLL_SESSION_GAP_MS + MeasurementConfig.FLUSH_INTERVAL_MS + 1)
        assertThat(store.sessions).hasSize(1)
        job.cancel()
    }

    @Test
    fun flushesNeverOverlapWhileAWriteIsSuspended() = runTest {
        store.writeDurationMs = 3_000
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        pipeline.offer(sample(dy = 100, uptimeMs = 1))
        runCurrent()
        launch { pipeline.flush() } // e.g. onInterrupt
        runCurrent()
        pipeline.offer(sample(dy = 100, uptimeMs = 2))
        runCurrent()
        launch { pipeline.flush() } // a second request while the first write is still running
        advanceTimeBy(10_000)
        assertThat(store.maxConcurrentWrites).isEqualTo(1)
        assertThat(store.distanceMm).isWithin(1e-9).of(2 * mmPer100Px)
        job.cancel()
    }

    @Test
    fun unflushedDistanceIsPublishedUntilTheWriteSucceeds() = runTest {
        store.failures = 1
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        pipeline.offer(sample(dy = 100, uptimeMs = 1))
        runCurrent()
        val key = "2026-09-21" to com.scrollmeter.app.measurement.APP
        assertThat(monitor.unflushed.value.getValue(key)).isWithin(1e-9).of(mmPer100Px)
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS + 1) // the write fails: still unflushed
        assertThat(monitor.unflushed.value.getValue(key)).isWithin(1e-9).of(mmPer100Px)
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS) // the retry succeeds
        assertThat(monitor.unflushed.value).isEmpty()
        assertThat(store.distanceMm).isWithin(1e-9).of(mmPer100Px)
        job.cancel()
    }

    @Test
    fun theOwnPackageInTestModeIsMeasuredLiveButNeverStored() = runTest {
        settings.enterTestMode()
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        pipeline.offer(sample(dy = 100, uptimeMs = 1, packageName = OWN_PACKAGE))
        pipeline.close()
        job.join()
        assertThat(monitor.selfTest.value.events).isEqualTo(1)
        assertThat(store.writes).isEmpty()
        assertThat(monitor.unflushed.value).isEmpty()
    }

    @Test
    fun theDayChangeIsReportedOnceByTheTicker() = runTest {
        var dayChanges = 0
        val pipeline = pipeline(wallStartMs = NEXT_MIDNIGHT_UTC_MS - 15_000, onDayChanged = { dayChanges++ })
        val job = launch { pipeline.run() }
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS + 1)
        assertThat(dayChanges).isEqualTo(0)
        advanceTimeBy(5 * MeasurementConfig.FLUSH_INTERVAL_MS)
        assertThat(dayChanges).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun whatWasPendingBeforeAnEraseIsDroppedNotWritten() = runTest {
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        repeat(3) { pipeline.offer(sample(dy = 100, uptimeMs = it.toLong())) }
        runCurrent()
        assertThat(monitor.unflushed.value.values.sum()).isWithin(1e-9).of(3 * mmPer100Px)
        monitor.writeLock.withLock { monitor.dataEpoch.incrementAndGet() } // "Smazat všechna data"
        repeat(2) { pipeline.offer(sample(dy = 100, uptimeMs = 10L + it)) }
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS + 1)
        assertThat(store.distanceMm).isWithin(1e-9).of(2 * mmPer100Px)
        job.cancel()
    }

    @Test
    fun anEraseWaitsForTheWriteInProgress() = runTest {
        store.writeDurationMs = 5_000
        var clearedAt = -1L
        val eraser = DataEraser(
            monitor = monitor,
            clearTables = { clearedAt = testScheduler.currentTime },
            clearSettings = {},
            forgetCalibration = {},
            setFloor = {},
            deleteFiles = {},
            usageExclusive = { it() },
        )
        val pipeline = pipeline()
        val job = launch { pipeline.run() }
        repeat(MeasurementConfig.FLUSH_EVENT_COUNT) { pipeline.offer(sample(dy = 100, uptimeMs = it.toLong())) }
        runCurrent() // the 50th event started a 5 s write
        val erase = launch { eraser.erase(alsoSettings = false) }
        advanceTimeBy(1_000)
        assertThat(clearedAt).isEqualTo(-1L)
        advanceTimeBy(5_000)
        assertThat(store.writes).hasSize(1)
        assertThat(clearedAt).isEqualTo(5_000L)
        erase.join()
        job.cancel()
    }

    @Test
    fun onFlushedFollowsOnlyACommittedWrite() = runTest {
        var flushed = 0
        store.failures = 1
        val pipeline = pipeline(onFlushed = { flushed++ })
        val job = launch { pipeline.run() }
        pipeline.offer(sample(dy = 100, uptimeMs = 0))
        runCurrent()
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS + 1)
        assertThat(flushed).isEqualTo(0)
        advanceTimeBy(MeasurementConfig.FLUSH_INTERVAL_MS)
        assertThat(flushed).isEqualTo(1)
        assertThat(store.writes).hasSize(1)
        job.cancel()
    }

}
