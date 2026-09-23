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
`eventTime`, `packageName`, `windowId`, `className`, `scrollDeltaX`, `scrollDeltaY`, `scrollX`, `scrollY`.
Nothing else — no text, no node tree, no content description.

Priority of sources per event:

| Source | Rule | Counted as |
|---|---|---|
| A `DIRECT_DELTA` | `scrollDeltaX` or `scrollDeltaY` ≠ 0 → use them | `measuredEventCount` |
| B `FALLBACK_POSITION` | both deltas 0 → `dx = scrollX − prevScrollX`, `dy = scrollY − prevScrollY`, only if previous event has the same `packageName + windowId + className`, the gap is ≤ `FALLBACK_MAX_GAP_MS` (2 s), both positions look valid (≥ 0) and the result passes the outlier check | `fallbackEventCount` |
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
| 1 | `MANUAL_CARD` — user matches an on-screen bar to the long edge of an ISO/IEC 7810 ID-1 card (85.60 mm) | `mmPerPx = 85.60 / referencePixels` (raw px; square pixels assumed → X = Y) | HIGH |
| 2 | `DISPLAY_METRICS` — `DisplayMetrics.xdpi`, `ydpi` | `mmPerPxX = 25.4 / xdpi`, `mmPerPxY = 25.4 / ydpi` | MEDIUM |
| 3 | `MODEL_DATABASE` — not in MVP | — | MEDIUM/HIGH |
| 4 | `UNKNOWN` | — | LOW |

`density` / `densityDpi` are **logical** Android UI density and are never used for distance. On the test
device (OnePlus CPH2399) `densityDpi = 480` while the physical panel is `403.4 × 401.1 dpi` — using the
logical value would under-measure by 16 %.

A recalibration changes only future data. Stored aggregates keep the distance computed with the calibration
valid at the time and carry `calibrationVersion` (ADR-007).

## Fling, horizontal scroll, refresh rate

- Fling: Android keeps emitting scroll events while content decelerates; they count.
- Horizontal: carousels, tabs reported as scroll, galleries, tables — same vector formula. There is no
  separate "vertical metres" headline statistic.
- The sum of deltas represents the displacement regardless of 60 / 90 / 120 Hz; event *count* varies with
  refresh rate and must never be multiplied by an average distance.

## Duplicate events (nested scrolling)

Off by default (ADR-006). Candidate rule, to be validated against debug CSVs in Phase 1:
same `packageName`, same `windowId`, same `dx`, same `dy`, `|Δt| ≤ DEDUP_WINDOW_MS` (5 ms).

## Expected Android mechanics — to verify in Phase 1

These are expectations from the framework sources, not measurements. Phase 1 confirms or corrects them in
`docs/accuracy-testing.md`.

- Classic `View` (incl. `RecyclerView`, `ScrollView`, `ListView`): `View.onScrollChanged()` accumulates
  `dx/dy` in `SendViewScrolledAccessibilityEvent` and posts one `TYPE_VIEW_SCROLLED` per
  `ViewConfiguration.getSendRecurringAccessibilityEventsInterval()` (~100 ms) with `scrollDeltaX/Y` set
  (API 28+). `RecyclerView` reports real deltas while its `scrollX/scrollY` stay 0 → source A works, B does not.
- Jetpack Compose (`LazyColumn`, `LazyRow`, `verticalScroll`): `AndroidComposeView`'s accessibility delegate
  sends `TYPE_VIEW_SCROLLED` from semantics scroll-range changes with `scrollDeltaX/Y` set.
- Chrome / `WebView`: scrolling is compositor-driven; scroll events come from `WebContentsAccessibility`,
  which may only activate for services with `canRetrieveWindowContent` / richer flags (risk R2, ADR-013).
- Android 14+: views can be flagged `accessibilityDataSensitive`; a service with `isAccessibilityTool=false`
  will not see their events. Such apps are reported as limited/unsupported, never worked around.
