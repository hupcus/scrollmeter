package com.scrollmeter.app.measurement

import java.util.concurrent.atomic.AtomicInteger

/**
 * Runtime switches the engine reads on every event. Written from the UI thread, read on the
 * engine's thread — hence volatile / atomic fields.
 */
class MeasurementSettings {
    private val testModeHolders = AtomicInteger(0)

    /**
     * Test mode (spec §35): while a debug test list is open, our own package is measured so it
     * can be compared with ground truth. Outside test mode it is always excluded.
     *
     * Counted, not a flag: when an activity is recreated, the old screen's dispose can arrive
     * after the new screen has entered test mode (seen on the test phone with CLEAR_TASK).
     */
    val includeOwnPackage: Boolean get() = testModeHolders.get() > 0

    fun enterTestMode() {
        testModeHolders.incrementAndGet()
    }

    fun exitTestMode() {
        testModeHolders.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /** Packages the user excluded (spec §14) — a Settings screen writes this from Phase 6. */
    @Volatile
    var excludedPackages: Set<String> = emptySet()
}
