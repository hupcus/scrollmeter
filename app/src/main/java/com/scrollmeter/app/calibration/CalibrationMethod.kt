package com.scrollmeter.app.calibration

/** Where the px → mm scale came from (spec §8–§10, §33). Pure Kotlin — the engine uses it. */
enum class CalibrationMethod {
    /** The user matched a bar to an ID-1 card (85.60 mm) — Phase 2. */
    MANUAL_CARD,

    /** `DisplayMetrics.xdpi` / `ydpi`. */
    DISPLAY_METRICS,

    /** Nothing trustworthy; logical `densityDpi` as a last resort. */
    UNKNOWN,
}

enum class CalibrationConfidence { HIGH, MEDIUM, LOW }
