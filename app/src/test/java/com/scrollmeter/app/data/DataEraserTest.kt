package com.scrollmeter.app.data

import com.google.common.truth.Truth.assertThat
import com.scrollmeter.app.measurement.MeasurementMonitor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Spec §45, ADR-031: what is deleted, in which order, and that nothing can come back. */
class DataEraserTest {
    private val monitor = MeasurementMonitor("own")
    private val calls = mutableListOf<String>()
    private var usageLocked = false
    private var tablesReached: CompletableDeferred<Unit>? = null
    private var tablesMayFinish: CompletableDeferred<Unit>? = null

    private val eraser = DataEraser(
        monitor = monitor,
        clearTables = {
            tablesReached?.complete(Unit)
            tablesMayFinish?.await()
            calls += "tables:locked=${monitor.writeLock.isLocked},usage=$usageLocked,epoch=${monitor.dataEpoch.get()}"
        },
        clearSettings = { calls += "settings" },
        forgetCalibration = { calls += "calibration" },
        setFloor = { calls += "floor:$it" },
        clearLeftovers = { calls += "leftovers" },
        usageExclusive = { block ->
            usageLocked = true
            block()
            usageLocked = false
        },
        nowMs = { 42L },
    )

    @Test
    fun dataOnlyKeepsSettingsAndCalibration() = runBlocking {
        monitor.unflushed.value = mapOf(("2026-09-24" to "a") to 3.0)
        eraser.erase(alsoSettings = false)
        assertThat(calls).containsExactly("floor:42", "tables:locked=true,usage=true,epoch=1", "leftovers").inOrder()
        assertThat(monitor.unflushed.value).isEmpty()
    }

    @Test
    fun everythingClearsSettingsFirstSoTheFloorSurvives() = runBlocking {
        eraser.erase(alsoSettings = true)
        assertThat(calls).containsExactly("settings", "calibration", "floor:42", "tables:locked=true,usage=true,epoch=1", "leftovers").inOrder()
        assertThat(monitor.writeLock.isLocked).isFalse()
    }

    /** The screen that asked may leave mid-way (back, rotation): the erase still ends, never half done. */
    @Test
    fun cancellingTheCallerDoesNotStopAnEraseHalfWay() = runBlocking {
        tablesReached = CompletableDeferred()
        tablesMayFinish = CompletableDeferred()
        val caller = launch { eraser.erase(alsoSettings = true) }
        tablesReached!!.await()
        val cancelling = launch { caller.cancelAndJoin() }
        tablesMayFinish!!.complete(Unit)
        cancelling.join()
        assertThat(calls).containsExactly("settings", "calibration", "floor:42", "tables:locked=true,usage=true,epoch=1", "leftovers").inOrder()
        assertThat(monitor.writeLock.isLocked).isFalse()
    }
}
