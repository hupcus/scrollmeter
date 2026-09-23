# Accuracy testing

Numbers in this file come from measurements only. An empty cell means "not measured yet".

## Metrics (spec §38)

```text
absoluteErrorMm  = |measuredMm − groundTruthMm|
MAE              = mean(absoluteErrorMm)
percentageError  = |measured − groundTruth| / groundTruth × 100
MAPE             = mean(percentageError)
```

Target on the app's own test list: MAPE < 5 % (ideal < 2 %). Third-party apps have no ground truth → no
accuracy claim, only compatibility.

## Protocol

Preparation: `./gradlew installDebug`, enable the service via adb (commands in `CLAUDE.md`).

**Own test list** — `python3 tools/device_accuracy.py --surface view,column,lazy` opens the debug build's
test list (`--es devtool testlist`), picks a surface, resets it and runs the gestures below. Ground truth
is the list's own scroll: `OnScrollChangeListener` for the View surface, a `NestedScrollConnection`
(Σ consumed) for the Compose surfaces. The screen logs one `TESTLIST` line per settled change; the
script compares it with the engine's own-package totals.

| Test | What | Gesture (portrait, 1080×2400) | Pass |
|---|---|---|---|
| A linear | known distance | `input motionevent` DOWN / MOVE… / 0.6 s hold / UP — velocity ≈ 0 at lift, so no fling; 500, 1000, 5 × 1000 px | MAPE < 5 % |
| B repeated | 20 similar swipes | `input swipe … 700 px 300 ms` × 20 — an automated stand-in for Honza's manual B | MAPE < 5 % |
| C fling | fast swipe, let it coast | `input swipe … 600 px 120 ms` (× 1 and × 3); `MARK_UP` logged at lift | events keep arriving after lift; total ≈ ground truth |
| D horizontal | carousel | slow drag and fling on the horizontal row | measured ≈ ground truth |
| E reversal | down 1000 then up 1000 | two opposite slow drags | ≈ 2000 px, not 0 |

**Chrome** has no ground truth of its own. The check is internal consistency (its two streams must agree)
plus finger geometry: a slow drag moves the page by *finger − toolbar hide (168 px) − touch slop (24 px)*.

**Third-party apps** — manual run by Honza, one debug CSV per app (Debug měření → Vymazat → scroll → Export
CSV). Pull with `adb pull /sdcard/Android/data/com.scrollmeter.app.debug/files/debug/` and run
`python3 tools/analyze_debug_csv.py <dir>`. CSV columns: `timestamp, uptime_ms, package, window_id,
class_name, dx_px, dy_px, scroll_x, scroll_y, max_scroll_x, max_scroll_y, used_dx_px, used_dy_px,
distance_mm, source, status`.

## Devices

| Device | OS / API | Resolution | xdpi / ydpi | densityDpi | Manual card mm/px | Notes |
|---|---|---|---|---|---|---|
| OnePlus CPH2399 (Nord 2T) | Android 14 / 34 | 1080×2400 | 403.411 / 401.052 | 480 | | 60/90 Hz; primary test phone |

## App compatibility matrix (Phase 1)

| App | Package | `TYPE_VIEW_SCROLLED` emitted? | `scrollDelta` usable? | Fallback needed? | Outliers | Duplicates (≤ 5 ms) | Subjective coverage | Notes |
|---|---|---|---|---|---|---|---|---|
| Chrome | `com.android.chrome` | yes, without `canRetrieveWindowContent` | yes (`FrameLayout`) | no — WebView positions are a duplicate stream, superseded (ADR-020) | 0 (max ~1117 px) | | full | automated runs; ADR-013 settled |
| Instagram | `com.instagram.android` | | | | | | | |
| Facebook | `com.facebook.katana` | | | | | | | includes in-app browser (WebView) |
| Reddit | `com.reddit.frontpage` | | | | | | | install first |
| YouTube | `com.google.android.youtube` | | | | | | | |
| TikTok | `com.zhiliaoapp.musically` | | | | | | | vertical paging feed |
| X | `com.twitter.android` | | | | | | | |
| Google Play | `com.android.vending` | | | | | | | |
| Mapy.com | `cz.seznam.mapy` | | | | | | | map panning + result list |
| In-app browser | (host app) | | | | | | | link opened inside Facebook / Instagram |
| Settings | `com.android.settings` | | | | | | | RecyclerView |
| ScrollMeter test list | `com.scrollmeter.app.debug` | yes | View: yes · Compose: no (-1, -1) | Column: yes, exact · Lazy: estimate only | 0 | | View / Column full, Lazy none | ground truth available |

## Accuracy runs

All on the OnePlus CPH2399, calibration DISPLAY_METRICS (xdpi / ydpi), 2026-09-23.

| Surface | Source | Runs | MAE mm | MAPE % | Verdict |
|---|---|---|---|---|---|
| View (`ScrollView` + `HorizontalScrollView`) | direct delta | 9 + 3 fling reruns | 0.11 / 0.00 | 0.05 / 0.00 | pass |
| Compose `verticalScroll` / `horizontalScroll` | position fallback | 9 + 3 fling reruns | 3.85 / 4.94 | 3.13 / 2.60 | pass (< 5 %), always under |
| Compose `LazyColumn` / `LazyRow` | none (index estimate, ADR-019) | 9 | — | 100 (0 counted) | unmeasurable by design |
| Chrome 153 (Wikipedia article) | direct delta, WebView positions superseded (ADR-020) | 7 | — | 0.00 vs own position stream | pass |

### Own test list — run 1 (all surfaces)

| Surface | Test | GT px | Measured px | GT mm | Measured mm | Error % | Events |
|---|---|---|---|---|---|---|---|
| view | A500 | 784 | 784 | 49.65 | 49.65 | +0.00 | 6 |
| view | A1000 | 1666 | 1666 | 105.51 | 105.51 | +0.00 | 8 |
| view | A5000 | 6820 | 6820 | 431.93 | 431.93 | +0.00 | 27 |
| view | B20 | 28642 | 28642 | 1814.00 | 1814.00 | +0.00 | 126 |
| view | C_fling | 2784 | 2784 | 176.32 | 176.32 | +0.00 | 7 |
| view | C_fling3 | 9236 | 9236 | 584.95 | 584.95 | +0.00 | 18 |
| view | D_slow | 726 | 726 | 45.71 | 45.71 | +0.00 | 4 |
| view | D_fling | 4348 | 4348 | 273.76 | 273.76 | +0.00 | 9 |
| view | E_reversal | 3552 | 3536 | 224.96 | 223.95 | -0.45 | 14 |
| column | A500 | 476 | 458 | 30.15 | 29.01 | -3.78 | 4 |
| column | A1000 | 976 | 916 | 61.81 | 58.01 | -6.15 | 5 |
| column | A5000 | 4880 | 4820 | 309.07 | 305.27 | -1.23 | 24 |
| column | B20 | 21366 | 21322 | 1353.17 | 1350.39 | -0.21 | 146 |
| column | C_fling | 2282 | 2186 | 144.51 | 138.45 | -4.19 | 10 |
| column | C_fling3 | 5895 | 5779 | 373.36 | 366.00 | -1.97 | 20 |
| column | D_slow | 726 | 687 | 45.71 | 43.26 | -5.37 | 4 |
| column | D_fling | 2523 | 2468 | 158.83 | 155.39 | -2.16 | 10 |
| column | E_reversal | 1952 | 1892 | 123.63 | 119.83 | -3.07 | 9 |
| lazy | A500 | 476 | 0 | 30.15 | 0.00 | -100.00 | 0 |
| lazy | A1000 | 976 | 0 | 61.81 | 0.00 | -100.00 | 0 |
| lazy | A5000 | 4880 | 0 | 309.07 | 0.00 | -100.00 | 0 |
| lazy | B20 | 21403 | 0 | 1355.53 | 0.00 | -100.00 | 0 |
| lazy | C_fling | 1945 | 0 | 123.18 | 0.00 | -100.00 | 0 |
| lazy | C_fling3 | 5672 | 0 | 359.23 | 0.00 | -100.00 | 0 |
| lazy | D_slow | 726 | 0 | 45.71 | 0.00 | -100.00 | 0 |
| lazy | D_fling | 2514 | 0 | 158.28 | 0.00 | -100.00 | 0 |
| lazy | E_reversal | 1952 | 0 | 123.63 | 0.00 | -100.00 | 0 |

Column is always *under*: the first event of a gesture after > 2 s idle has no previous position to diff
against (spec §6 B gap rule) — ~20–60 px per gesture. Lazy: +110…+115 % before ADR-019, 0 counted after.

### Own test list — fling rerun (events after the finger lifts)

| Surface | Test | GT px | Measured px | Error % | Events | After lift (events / px) |
|---|---|---|---|---|---|---|
| view | C_fling | 3439 | 3439 | +0.00 | 8 | 7 / 2945 |
| view | C_fling3 | 9126 | 9126 | +0.00 | 19 | 8 / 3083 |
| view | D_fling | 4398 | 4398 | +0.00 | 9 | 8 / 3797 |
| column | C_fling | 2232 | 2146 | −3.86 | 10 | 10 / 2146 |
| column | C_fling3 | 6054 | 5969 | −1.41 | 21 | 9 / 1850 |
| column | D_fling | 2486 | 2423 | −2.53 | 10 | 10 / 2423 |

Fling is measured: most of a fling's distance arrives after the finger lifts, and it is all counted.

### Chrome — two streams, counted once

`FrameLayout` sends direct deltas, the `WebView` node sends positions of the same scroll. Before ADR-020 both
were counted (slow 1000 px drag → 1558 px). After, from the top of the article each time:

| Test | FrameLayout events | Σ |dy| | WebView events | WebView scrollY | Counted px | vs FrameLayout |
|---|---|---|---|---|---|---|
| slow_1000 | 3 | 808 | 3 | 60 → 808 | 808 | +0.00% |
| slow_600 | 4 | 408 | 2 | 26 → 408 | 408 | +0.00% |
| slow_1000x3 | 10 | 2760 | 8 | 60 → 2760 | 2760 | +0.00% |
| fling | 13 | 4925 | 4 | 159 → 4925 | 4925 | +0.00% |
| fling3 | 20 | 12845 | 6 | 139 → 12845 | 12845 | +0.00% |
| D_slow_600_30steps | 10 | 634 | 4 | 16 → 408 | 634 | +0.00% |
| E_down_up_1000 | 7 | 1616 | 4 | 60 → 0 | 1616 | +0.00% |

Finger geometry: 1000 px drag → 808 px (= 1000 − 168 − 24), 600 → 408, 3 × 1000 → 2760, 1000 down + 1000 up
→ 808 + 808. `D_slow_600_30steps`: Chrome itself reported +544 then −137 px while the finger held still
(the toolbar snapping back?) — the engine counts what the app reports; not verified visually.
Largest single Chrome event seen: ~1117 px against an outlier limit of 10 527 px (4 × diagonal).

## Battery (Phase 8)

| Date | Device | Scenario | Duration | Events | Room writes | CPU / wakeups | Battery % | Notes |
|---|---|---|---|---|---|---|---|---|
| | | | | | | | | |
