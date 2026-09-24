package com.scrollmeter.app.measurement

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/** Receives every engine result — the debug event log and logcat hook in here (debug builds only). */
fun interface MeasurementSink {
    fun onResult(result: MeasurementResult)
}

/** Counters since the service last connected, in RAM — the stored totals are in Room (Phase 3). */
data class LiveTotals(
    val connectedAtWallMs: Long = 0L,
    val countedMm: Double = 0.0,
    val events: Long = 0L,
    val direct: Long = 0L,
    val fallback: Long = 0L,
    val unmeasurable: Long = 0L,
    val outliers: Long = 0L,
    val excluded: Long = 0L,
)

/** Our own package's counted movement while the test list is open (spec §35). */
data class SelfTestTotals(
    val absDxPx: Long = 0L,
    val absDyPx: Long = 0L,
    val countedMm: Double = 0.0,
    val events: Long = 0L,
)

/**
 * Live state shared by the accessibility service (writer) and the UI (reader). Both run in the
 * app's single process, so the service and the activity see the same instance via `AppGraph`.
 */
class MeasurementMonitor(private val ownPackage: String) {
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _totals = MutableStateFlow(LiveTotals())
    val totals: StateFlow<LiveTotals> = _totals.asStateFlow()

    private val _selfTest = MutableStateFlow(SelfTestTotals())
    val selfTest: StateFlow<SelfTestTotals> = _selfTest.asStateFlow()

    /** Samples lost because the channel overflowed (DROP_OLDEST) — should stay 0. */
    val droppedSamples = AtomicLong()

    /** Events the callback or the consumer could not process (spec §61) — should stay 0. */
    val processingFailures = AtomicLong()

    fun onServiceConnected(nowWallMs: Long) {
        _totals.value = LiveTotals(connectedAtWallMs = nowWallMs)
        _serviceConnected.value = true
    }

    fun onServiceDisconnected() {
        _serviceConnected.value = false
    }

    fun record(result: MeasurementResult) {
        _totals.update { t ->
            t.copy(
                countedMm = t.countedMm + if (result.accepted) result.distance.totalMm else 0.0,
                events = t.events + 1,
                direct = t.direct + if (result.source == MeasurementSource.DIRECT_DELTA) 1 else 0,
                fallback = t.fallback + if (result.source == MeasurementSource.FALLBACK_POSITION) 1 else 0,
                unmeasurable = t.unmeasurable + if (result.source == MeasurementSource.UNMEASURABLE) 1 else 0,
                outliers = t.outliers + if (result.source == MeasurementSource.OUTLIER_REJECTED) 1 else 0,
                excluded = t.excluded + if (result.source == MeasurementSource.EXCLUDED) 1 else 0,
            )
        }
        if (result.accepted && result.sample.packageName == ownPackage) {
            _selfTest.update { s ->
                s.copy(
                    absDxPx = s.absDxPx + abs(result.dxPx),
                    absDyPx = s.absDyPx + abs(result.dyPx),
                    countedMm = s.countedMm + result.distance.totalMm,
                    events = s.events + 1,
                )
            }
        }
    }

    fun resetSelfTest() {
        _selfTest.value = SelfTestTotals()
    }
}
