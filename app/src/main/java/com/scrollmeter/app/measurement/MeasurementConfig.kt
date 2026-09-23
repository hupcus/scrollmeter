package com.scrollmeter.app.measurement

/**
 * Every tunable measurement constant lives here (ADR-005) so it can be retuned after real data
 * in one place. Changing a value is a decision: record it in docs/measurement-decisions.md.
 */
object MeasurementConfig {
    /**
     * An event whose hypot(dx, dy) exceeds this multiple of the screen diagonal is rejected whole
     * as OUTLIER_REJECTED — never clipped (spec §11). Phase 1 checks it against real flings.
     */
    const val MAX_EVENT_DISTANCE_FACTOR = 4.0

    /** The scrollX/Y fallback is used only when the previous event of the same key is this recent (spec §6 B). */
    const val FALLBACK_MAX_GAP_MS = 2_000L

    /**
     * A position fallback is not counted while another view class of the same package delivered a
     * direct delta this recently:
     * Chrome reports every scroll twice — deltas from its compositor `FrameLayout` and positions
     * from the `WebView` node — and counting both doubles the distance (ADR-020).
     */
    const val DIRECT_SUPERSEDES_FALLBACK_MS = 5_000L

    /** Upper bound of remembered fallback keys (package + windowId + className); least recently used goes first. */
    const val FALLBACK_TRACKER_MAX_KEYS = 64

    /**
     * `AccessibilityRecord` initialises scrollDeltaX/Y to UNDEFINED = -1 and only `View` and
     * frameworks that call setScrollDeltaX/Y overwrite it. A (-1, -1) pair therefore means
     * "this app sent no delta", not a one-pixel diagonal scroll.
     */
    const val UNDEFINED_SCROLL_DELTA = -1

    /**
     * Jetpack Compose lazy lists send scroll events without deltas and with a *made-up* position:
     * `estimatedLazyScrollOffset = firstVisibleItemScrollOffset + firstVisibleItemIndex × 500` and
     * `maxScroll = that + 100` while the list can scroll further (foundation 1.11.4,
     * LazyLayoutSemantics). A fallback key showing `maxScroll − scroll == 100` while its max moves
     * with the position is marked as an index-based estimate and never measured (spec §6 C, §72 —
     * ADR-019).
     */
    const val COMPOSE_LAZY_MAX_SCROLL_MARGIN_PX = 100

    /** Duplicate suppression is off until debug CSVs prove duplicates exist (spec §70, ADR-006). */
    const val DEDUP_ENABLED = false
    const val DEDUP_WINDOW_MS = 5L

    /** Accumulator flush triggers — used from Phase 3 (spec §16). */
    const val FLUSH_INTERVAL_MS = 10_000L
    const val FLUSH_EVENT_COUNT = 50

    /** A gap longer than this starts a new scroll session — used from Phase 3 (spec §18). */
    const val SCROLL_SESSION_GAP_MS = 60_000L

    /** ISO/IEC 7810 ID-1 card width for manual calibration — used from Phase 2 (spec §8). */
    const val CARD_WIDTH_MM = 85.60
    const val MM_PER_INCH = 25.4

    /**
     * Physical dpi outside this range is treated as a broken `xdpi`/`ydpi` report. Phones sit
     * roughly between 250 and 600; the range is deliberately wide.
     */
    const val MIN_PLAUSIBLE_DPI = 100.0
    const val MAX_PLAUSIBLE_DPI = 1_000.0

    /** Samples waiting between the accessibility callback and the engine; overflow drops the oldest (D5). */
    const val SAMPLE_CHANNEL_CAPACITY = 4_096

    /** Debug builds only: events shown on the debug screen, and events kept in RAM for CSV export. */
    const val DEBUG_LOG_VISIBLE_EVENTS = 100
    const val DEBUG_LOG_RECORDING_EVENTS = 20_000
}
