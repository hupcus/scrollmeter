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

    /** Accumulator flush triggers (spec §16): every 10 s with data pending, or after 50 events. */
    const val FLUSH_INTERVAL_MS = 10_000L
    const val FLUSH_EVENT_COUNT = 50

    /** A gap longer than this starts a new scroll session (spec §18). */
    const val SCROLL_SESSION_GAP_MS = 60_000L

    /**
     * Active scroll time (ADR-022, SPEC §50): the gap between two counted events of one package
     * counts as scrolling when it is at most this long; a longer pause is not scrolling.
     */
    const val ACTIVE_SCROLL_GAP_MS = 5_000L

    /**
     * Time in app (ADR-021): the sync recomputes whole local days from one day before the last
     * sync, but never further back than this — Android keeps usage events for about ten days.
     */
    const val USAGE_SYNC_MAX_DAYS = 9L

    /**
     * Events are read this far before the first recomputed day, so an app already in the
     * foreground at midnight is paired with its RESUMED event (ADR-025).
     */
    const val USAGE_SYNC_LOOKBACK_MS = 12 * 60 * 60_000L

    /** The service re-syncs time in app on connect when the last sync is older than this (PLAN Phase 3). */
    const val USAGE_SYNC_STALE_MS = 6 * 60 * 60_000L

    /**
     * An app whose last activity paused and that resumes again within this window never left the
     * foreground — switching between its own activities pauses one before resuming the next (ADR-025).
     */
    const val USAGE_SAME_APP_GRACE_MS = 2_000L

    /** ADR-031: closed sessions older than this are deleted — they have no UI in the MVP (ADR-011). */
    const val SESSION_RETENTION_DAYS = 90L

    /**
     * "Nový rekord" (spec §26, ADR-031): only after this many earlier measured days — the second day
     * of use is not a record worth a notification — and only above [RECORD_MIN_MM].
     */
    const val RECORD_MIN_PRIOR_DAYS = 3
    const val RECORD_MIN_MM = 10_000.0

    /**
     * Measurement quality per app (spec §20, ADR-029). Under [QUALITY_MIN_EVENTS] stored events there is
     * too little to judge. LOW when at least half the events carried no usable pixels or 5 % were
     * outliers; HIGH when ≥ 90 % of counted events were direct deltas, < 10 % unmeasurable, < 1 %
     * outliers and the display is calibrated with a card; MEDIUM otherwise.
     */
    const val QUALITY_MIN_EVENTS = 20L
    const val QUALITY_LOW_UNMEASURABLE_SHARE = 0.5
    const val QUALITY_LOW_OUTLIER_SHARE = 0.05
    const val QUALITY_HIGH_DIRECT_SHARE = 0.9
    const val QUALITY_HIGH_MAX_UNMEASURABLE_SHARE = 0.1
    const val QUALITY_HIGH_MAX_OUTLIER_SHARE = 0.01

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
