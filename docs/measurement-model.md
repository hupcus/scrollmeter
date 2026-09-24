# Measurement model

## What we measure

**Scroll distance**: the physical equivalent of how far scrollable content moved on the display.
It is the sum of the magnitudes of every scroll step, so scrolling 10 cm down and 10 cm up is 20 cm.
Fling / inertial scrolling after the finger lifts is included on purpose.

It is **not** the path of the finger on the glass. Raw touch coordinates are not available across
other apps on a stock device, and any "your thumb travelled X" claim would be invented.

Product copy therefore says „Dnes jsi nascrolloval 428 metrů", never „tvůj palec urazil 428 metrů".

## Source of data

`AccessibilityService` subscribed to `TYPE_VIEW_SCROLLED` only. Per event we read:
`eventTime`, `packageName`, `windowId`, `className`, `scrollDeltaX`, `scrollDeltaY`, `scrollX`, `scrollY`,
`maxScrollX`, `maxScrollY`.
Nothing else — no text, no node tree, no content description.

Priority of sources per event:

| Source | Rule | Counted as |
|---|---|---|
| A `DIRECT_DELTA` | `scrollDeltaX` or `scrollDeltaY` ≠ 0, and not both `-1` (Android's UNDEFINED, ADR-014) → use them | `measuredEventCount` |
| B `FALLBACK_POSITION` | deltas (0, 0) or (-1, -1) → `dx = scrollX − prevScrollX`, `dy = scrollY − prevScrollY`, only if previous event has the same `packageName + windowId + className` + reported axes, the gap is ≤ `FALLBACK_MAX_GAP_MS` (2 s), both positions look valid (≥ 0 and ≤ a positive `maxScrollX/Y`), the position changed, and the jump is within the outlier limit (ADR-015). A Compose lazy-list position (`maxScroll − scroll == 100`) is an index estimate and never used (ADR-019) | `fallbackEventCount` |
| `SUPERSEDED_BY_DIRECT` | a valid fallback while **another view class of the same package** sent a direct delta within 5 s — the same motion reported by a second stream (Chrome) → not counted, kept in the debug CSV (ADR-020) | not counted |
| C `UNMEASURABLE` | no usable pixel data → distance 0. `fromIndex` / `toIndex` / item counts are **never** converted to distance | `unmeasurableEventCount` |
| `OUTLIER_REJECTED` | `hypot(dx, dy) > MAX_EVENT_DISTANCE_FACTOR × screenDiagonalPx` (4 ×) → event not added; no clipping | `rejectedOutlierCount` |
| `EXCLUDED` | `packageName == null`, own package (outside test mode) or user-excluded package | not stored |

## Formulas

```text
mmPerPxX, mmPerPxY               from calibration (below)
dxMm = dxPx · mmPerPxX
dyMm = dyPx · mmPerPxY
eventDistanceMm = hypot(dxMm, dyMm)        // vector magnitude, so diagonal moves are not double counted
totalDistanceMm += eventDistanceMm          // magnitudes add; direction never cancels
horizontalDistanceMm += |dxMm|,  verticalDistanceMm += |dyMm|   // kept for analytics only
```

Worked examples (both are unit tests):

- `xdpi = ydpi = 420` → `mmPerPx = 25.4 / 420 = 0.0604761905`; `dy = 1200 px` → `72.5714 mm`; 100 such events → `7.257 m`.
- `dx = 300, dy = 400, mmPerPx = 0.06` → `18 mm`, `24 mm` → `hypot = 30 mm` (not 42 mm).

## Calibration hierarchy

| Priority | Method | Formula | Confidence |
|---|---|---|---|
| 1 | `MANUAL_CARD` — user matches an on-screen **vertical** bar to the long edge of an ISO/IEC 7810 ID-1 card (85.60 mm; ADR-023) | `mmPerPx = 85.60 / referencePixels` (raw px; square pixels assumed → X = Y) | HIGH |
| 2 | `DISPLAY_METRICS` — `DisplayMetrics.xdpi`, `ydpi` | `mmPerPxX = 25.4 / xdpi`, `mmPerPxY = 25.4 / ydpi` | MEDIUM |
| 3 | `MODEL_DATABASE` — not in MVP | — | MEDIUM/HIGH |
| 4 | `UNKNOWN` | — | LOW |

`density` / `densityDpi` are **logical** Android UI density and are never used for distance. On the test
device (OnePlus CPH2399) `densityDpi = 480` while the physical panel is `403.4 × 401.1 dpi` — using the
logical value would under-measure by 16 %.

A card calibration applies only to the phone and display resolution it was made on (manufacturer + model +
panel size in px); anywhere else the scale falls back to `xdpi`/`ydpi` and the accuracy screen says so (ADR-024).

A recalibration changes only future data. Every engine result carries the `calibrationVersion` its distance
was computed under (the version counts the user's calibration changes, 0 = never calibrated); stored
aggregates keep the distance computed with the calibration valid at the time and carry the version (ADR-007).
Nothing is recomputed.

## Fling, horizontal scroll, refresh rate

- Fling: Android keeps emitting scroll events while content decelerates; they count.
- Horizontal: carousels, tabs reported as scroll, galleries, tables — same vector formula. There is no
  separate "vertical metres" headline statistic.
- The sum of deltas represents the displacement regardless of 60 / 90 / 120 Hz; event *count* varies with
  refresh rate and must never be multiplied by an average distance.

## Duplicate events (nested scrolling)

Off by default (ADR-006). Candidate rule, to be validated against debug CSVs in Phase 1:
same `packageName`, same `windowId`, same `dx`, same `dy`, `|Δt| ≤ DEDUP_WINDOW_MS` (5 ms).

A different kind of duplicate turned up in Chrome: one scroll reported by two streams of different shape
(deltas from a `FrameLayout`, positions from the `WebView` node). The rule above cannot see it; ADR-020 handles
it by letting direct deltas supersede another class's fallback.

## Android mechanics — measured in Phase 1

Measured on the test phone (OnePlus CPH2399, Android 14) with the debug test list and Chrome; numbers in
`docs/accuracy-testing.md`. ✅ = the expectation from the framework sources held, ✏️ = corrected by measurement.

- ✅ Classic `View` (`ScrollView`, `HorizontalScrollView`, `RecyclerView` in Settings): one `TYPE_VIEW_SCROLLED`
  per ~100 ms with `scrollDeltaX/Y` set; `RecyclerView` keeps `scrollX/Y` at 0. Source A, error 0.00–0.45 %
  against ground truth, flings included.
- ✏️ Jetpack Compose (foundation 1.11.4) sends `TYPE_VIEW_SCROLLED` **without** deltas (-1, -1).
  `verticalScroll`/`horizontalScroll` report exact positions → source B, 0.2–6.2 % *under*, MAPE 3.1 % (the first
  event of a gesture after > 2 s idle has nothing to diff against — spec §6 B's gap rule). `LazyColumn`/`LazyRow` report an index estimate
  → UNMEASURABLE by design (ADR-019).
- ✏️ Without `canRetrieveWindowContent` every event has `windowId = -1` — the fallback key carries the axes
  instead (ADR-015).
- ✏️ Chrome emits scroll events **without** `canRetrieveWindowContent` (ADR-013 stays `false`): direct deltas
  from a `FrameLayout` and positions from the `WebView` node for the same scroll (ADR-020). What is counted is
  the page's scroll offset: the 168 px the toolbar needs to hide and the 24 px touch slop are not part of it.
- ⏳ Android 14+ `accessibilityDataSensitive` views and in-app browsers — checked in the manual app run.

## Storing and time — Phase 3

- Results are summed in memory per (local date, package) and **added** to the stored row at each flush (every
  10 s while data is pending, the 50th event, a new day, interrupt, service end) — ADR-026. A killed process
  loses at most the unflushed 10 s; an event's day is the local date of its wall-clock time.
- **Active scroll time** (ADR-022): the gap between two counted events of one app counts when it is ≤ 5 s;
  gaps use `eventTime` (uptime), so changing the clock cannot create time.
- **Time in app** (ADR-021, ADR-025) is separate from scrolling: foreground intervals from Android's usage
  events, split at local midnight. On the API 34 emulator it matched the system's own `totalTimeUsed` to the
  second. Pace (m/min, Phase 4) divides distance by time in app when Usage access is granted, by active scroll
  time otherwise.
