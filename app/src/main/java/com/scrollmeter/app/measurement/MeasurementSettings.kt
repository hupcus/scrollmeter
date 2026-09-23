package com.scrollmeter.app.measurement

/**
 * Runtime switches the engine reads on every event. Written from the UI thread, read on the
 * engine's thread — hence volatile, immutable values.
 */
class MeasurementSettings {
    /**
     * Test mode (spec §35): while the debug test list is open, our own package is measured so
     * it can be compared with ground truth. Outside test mode it is always excluded.
     */
    @Volatile
    var includeOwnPackage: Boolean = false

    /** Packages the user excluded (spec §14) — a Settings screen writes this from Phase 6. */
    @Volatile
    var excludedPackages: Set<String> = emptySet()
}
