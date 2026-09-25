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
(Σ consumed) for the Compose surfaces. The screen logs one `TESTLIST` line per settled change (since
Phase 2 with the calibration in force: method, version, mm/px); the script compares it with the engine's
own-package totals. `--csv-out DIR` writes `ground_truth.csv` + `measured.csv`, and `tools/accuracy.py`
turns such a pair into per-run error, MAE and MAPE (spec §38). On the phone, the test list shows MAE / MAPE
over the runs of the current series ("Vynulovat" closes a run).

Ground truth mm and measured mm use the same scale, so the percentages above measure the *pixel* pipeline.
The scale itself is checked separately: the card calibration's mm/px against the display's `xdpi`/`ydpi`
(Devices table) — a deviation above 5 % would point at a raw-pixel error in the calibration bar.

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
| Emulators `scrollmeter28/30/33/34/35/36` (`google_apis` arm64-v8a, AVD device `pixel_6`) | Android 9–16 / 28–36 | 1080×2400 | 420 / 420 | 420 | — | Phase 8 matrix; only `scrollmeter34` is kept (the others were measured one at a time and deleted for disk space, images stay) |

## App compatibility matrix (Phase 1)

OnePlus CPH2399, Android 14, 2026-09-23. Sources: Honza's manual run (one recording, 497 events, 21:58–22:01,
foreground apps reconstructed from `wm_set_resumed_activity`), an earlier 26 s sample (H&M, Edge) and
automated adb probes. The §70 duplicate rule found **0** candidates in every package.

| App | Package | `TYPE_VIEW_SCROLLED`? | `scrollDelta` usable? | Fallback? | Outliers | Dup. (≤ 5 ms) | Coverage | Verdict | Notes |
|---|---|---|---|---|---|---|---|---|---|
| Chrome 153 | `com.android.chrome` | yes, without `canRetrieveWindowContent` | yes (`FrameLayout`) | WebView positions = duplicate stream, superseded (ADR-020) | 0 (max ~1117 px) | 0 | full | **supported** | automated; 0.00 % vs its own position stream |
| Edge | `com.microsoft.emmx` | yes | yes (`FrameLayout`, `RecyclerView`) | 6 superseded (0.61 m) | 0 (max 2566 px) | 0 | 77 % | **supported** | same Chromium dual stream as Chrome |
| Facebook — feed, stories | `com.facebook.katana` | yes | yes (`RecyclerView`, `ViewPager`) | no | 0 (max 3194 px) | 0 | 96 % | **supported** | 306 events (293 direct), 4.2 m in ~50 s |
| Facebook — in-app browser | `com.facebook.katana` | yes | yes (`WebView`, single stream) | no | 0 | 0 | 96 % | **supported** | `BrowserLiteDIActivity`, 26 events (25 direct), 1.47 m |
| Google Maps — result list | `com.google.android.apps.maps` | yes | yes (`RecyclerView`) | no | 0 | 0 | 100 % | **supported** | map panning itself is not a scroll event |
| Settings | `com.android.settings` | yes | yes (`RecyclerView`) | no | 0 | 0 | 94 % | **supported** | |
| OnePlus launcher | `com.android.launcher` | yes | no (-1, -1) | yes (`ListView`, exact) | 0 | 0 | 74 % | supported | counts app-drawer scrolling — whether it belongs in totals is a product call |
| Google Play | `com.android.vending` | yes | no (-1, -1) | Compose lazy estimate | — | 0 | 0 % | **limited** | 33/34 events show `max − scroll = 100` (ADR-019) |
| H&M | `com.hm.goe` | yes | no (-1, -1) | Compose lazy estimate | — | 0 | 0 % | **limited** | 67/69 events are the lazy estimate |
| YouTube | `com.google.android.youtube` | **no** — silent after a cold start | — | — | — | — | 0 % | **unsupported** | see below |
| Gboard (glide typing, space-bar swipe) | `com.google.android.inputmethod.latin` | no | — | — | — | — | — | does not distort | 5 swipes on the open keyboard → 0 events |
| Instagram | `com.instagram.android` | not tested | | | | | | ⏳ | login screen, no account on the test phone |
| TikTok | `com.zhiliaoapp.musically` | not tested | | | | | | ⏳ | sign-up screen only (2 WebView events, no data) |
| X | `com.twitter.android` | not tested | | | | | | ⏳ | no account |
| Reddit | `com.reddit.frontpage` | not tested | | | | | | ⏳ | not installed |
| ScrollMeter test list | `com.scrollmeter.app.debug` | yes | View: yes · Compose: no (-1, -1) | Column: yes, exact · Lazy: estimate only | 0 | 0 | View / Column full, Lazy none | reference | ground truth available |

### Manual run — analyser output

```text
Device `OnePlus_CPH2399_API34` · 1080×2400 px · xdpi 403.411 / ydpi 401.052 · outlier limit 10527.2 px · 2 file(s)

| Package | Events | Direct | Fallback | Superseded (m) | Unmeasurable | Outlier | Excluded | (-1,-1) | Coverage | Counted m | Median / p95 / max event px | Max / limit | Dup. candidates (≤5 ms) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `com.facebook.katana` | 332 | 318 | 0 | 0 | 14 | 0 | 0 | 1 | 95.8 % | 5.70 | 162 / 843 / 3194 | 0.30 | 0 |
| `com.hm.goe` | 69 | 0 | 0 | 0 | 69 | 0 | 0 | 69 | 0.0 % | 0.00 | — | — | 0 |
| `com.google.android.apps.maps` | 58 | 58 | 0 | 0 | 0 | 0 | 0 | 0 | 100.0 % | 0.88 | 182 / 702 / 882 | 0.08 | 0 |
| `com.android.settings` | 48 | 45 | 0 | 0 | 3 | 0 | 0 | 0 | 93.8 % | 1.08 | 312 / 1327 / 1492 | 0.14 | 0 |
| `com.microsoft.emmx` | 37 | 24 | 0 | 6 (0.61) | 7 | 0 | 0 | 10 | 77.4 % | 1.35 | 937 / 1449 / 2566 | 0.24 | 0 |
| `com.android.vending` | 34 | 0 | 0 | 0 | 34 | 0 | 0 | 34 | 0.0 % | 0.00 | — | — | 0 |
| `com.android.launcher` | 23 | 0 | 17 | 0 | 6 | 0 | 0 | 23 | 73.9 % | 0.72 | 678 / 778 / 778 | 0.07 | 0 |
| `com.oppo.quicksearchbox` | 2 | 0 | 0 | 0 | 2 | 0 | 0 | 0 | 0.0 % | 0.00 | — | — | 0 |

View classes emitting scroll events:
- `com.android.launcher`: `android.widget.ListView`, `android.widget.ScrollView`
- `com.android.settings`: `androidx.recyclerview.widget.RecyclerView`
- `com.android.vending`: `android.view.View`
- `com.facebook.katana`: `android.webkit.WebView`, `androidx.recyclerview.widget.RecyclerView`, `androidx.viewpager.widget.ViewPager`
- `com.google.android.apps.maps`: `android.support.v7.widget.RecyclerView`
- `com.hm.goe`: `android.view.View`
- `com.microsoft.emmx`: `android.webkit.WebView`, `android.widget.FrameLayout`, `androidx.recyclerview.widget.RecyclerView`
- `com.oppo.quicksearchbox`: `androidx.recyclerview.widget.RecyclerView`
```

### YouTube stays silent

Honza had YouTube in the foreground for ~14 s: 0 events. Automated, from a cold start (force-stop → launch →
3 slow drags + 1 fling): 0 events, with `canRetrieveWindowContent` **false and true** (throwaway branch
`throwaway/can-retrieve-window-content`, local only). In the *same* YouTube process, right after one
`uiautomator dump`, its `RecyclerView` does send direct deltas (575 px, 400 px). YouTube evidently turns its
scroll events on only when a richer accessibility client (UiAutomation, presumably TalkBack) is present —
nothing ScrollMeter can switch on without pretending to be one. Facebook on the same throwaway build was
unaffected (14 direct events).

### The system kills the service

In the first manual attempt ColorOS's memory guard (`OsenseKillAction … mem guard`, free RAM < 2 GB, kill
reason `o-kill(4010)`) killed ScrollMeter's process three times in 45 s while heavy apps ran; the system
restarted the service after 4–10 s each time. The RAM-only debug log lost that run; since ADR-017's
amendment the recording is written through and survives. `adb shell dumpsys activity exit-info
com.scrollmeter.app.debug` lists every kill with its reason.

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

## Emulator matrix (Phase 8)

2026-09-25, signed release build (`tools/build_release.sh`, R8) for onboarding, measuring, export and delete; the
debug build for the test list. Every emulator is a fresh AVD (`pixel_6`, 1080 × 2400, 420 dpi); the service is
switched on over adb (`settings put secure …`) at onboarding step 4, Usage access with `appops set … allow`.
"Measuring" = the release service scrolling Android Settings (`input swipe`, 6 swipes) and the rows read from the
app's database as root — no `uiautomator` in between (see the note below).

| API | Onboarding (release) | Measuring (release) | Test list View · Column MAPE (9 runs each) | Export · Delete (release) | Other |
|---|---|---|---|---|---|
| 28 (Android 9) | done (en) | **no** — see "Android 9" below | **0 events in 18 of 18 runs** | not run | minSdk raised to 29 (ADR-033) |
| 30 (Android 11) | en: all 6 steps; step 4 moves on by itself; Usage access page opens | Settings 426.7 mm, 30 direct events; a foreign app's View list 260.7 mm, 27 direct | 0.00 % · 3.85 % | SAF save (BOM, CRLF, empty = unknown), share sheet, delete → 0 rows in all 3 tables | PIN + reboot: before unlock no process and no bound service (`RUNNING_LOCKED`); after unlock the service binds by itself, data kept (71.1 → 142.2 mm) |
| 33 (Android 13) | cs (per-app language) | Settings 438.2 mm, 37 direct | 0.18 % · 4.95 % | as API 30, in Czech | `POST_NOTIFICATIONS`: switching "Denní cíl dosažen" on shows the system prompt, *Allow* grants it |
| 34 (Android 14) | cs + en (Phase 7) | Phase 3 table below | phone, Phase 1: 0.05 % · 3.13 % | Phase 6 | battery protocol below |
| 35 (Android 15) | en | Settings 423.3 mm, 34 direct | 0.00 % · 4.99 % | as API 30; share sheet is `com.android.intentresolver` | edge-to-edge enforced (targetSdk 36): no content under the status or navigation bar, light and dark |
| 36 (Android 16) | cs | Settings 575.9 mm, 47 direct | 0.33 % · 4.32 % | as API 30, in Czech | edge-to-edge as API 35; Usage access revoked → the app detail switches from "Čas v aplikaci" to "Čas scrollování", no crash |

- **Compose `verticalScroll` under-counts slow drags** on every API: −6 to −11 % for A500 / A1000 / D_slow with only
  3 events per drag on API 33–36 (11 on API 30), −0.1 to −3 % for flings and long runs. The column's positions come
  in throttled scroll events and the last movement before the lift is not always reported. MAPE stays under the
  5 % target (4.3–5.0 %), just barely on API 33 and 35 — the same pattern as Phase 1 on the phone (3.1 %).
- Compose `LazyColumn` (the debug list's default surface) stays unmeasurable on every API (ADR-019): 31–32
  UNMEASURABLE events, 0 mm.
- **Language found on API 33 / 36:** the toast after *Smazat všechna data* was English in a Czech app — texts built
  from the application context ignore the per-app language. Fixed and re-checked on API 36 (ADR-035); the
  notifications had the same cause and are pinned by a unit test.
- Screenshots (onboarding, export / delete, the four main screens, dark mode; API 30 en, 33 cs, 35 en, 36 cs) were
  checked by subagents: no clipped text, nothing under the system bars, no untranslated string besides the one fixed.

### Android 9 (API 28) is not supported

With ScrollMeter the only accessibility service, Android 9 delivered no scroll event of our own test list (18 of 18
runs, View and Column, ground truth 0.5–30 k px each) and Settings' events only sometimes (one swipe gave 0 events,
the next 1, an earlier round dozens). AOSP 9 forwards `TYPE_VIEW_SCROLLED` only from the *active* window, and
without a window-tracking service that window moves only on a `TYPE_WINDOW_STATE_CHANGED` — which apps send only
when a bound service asks for it. Android 10 makes apps send it for every service (without delivering it), so from
API 29 our scroll-only service gets every app's scrolling. Details and sources: ADR-033. Android 9's Settings also
reports its (support-library) `RecyclerView` scrolling with dx = dy = 0 and no positions — unmeasurable even when
delivered. Test harness notes from the run: `uiautomator dump` fails outright ("null root node") on API 28 while any
accessibility service is on, and `input motionevent` does not exist before API 29.

### Test harness notes (all APIs)

- `uiautomator dump` reads the *active* window. With no accessibility service bound (e.g. after `am force-stop`,
  which also switches the service off), apps do not report window changes and the dump returns a screen that is
  no longer in front — taps by its coordinates land elsewhere. Navigate with a service on.
- `dumpsys activity top` times out (API 35) when a cached app is frozen; `tools/device_accuracy.py` reads only our
  package (`dumpsys activity <package>`) and finds *Vynulovat* before every run (the lines above it wrap as numbers
  grow).

## Persistence and time in app (Phase 3)

Emulator `scrollmeter34` (AVD, API 34, google_apis arm64, pixel_6 1080 × 2400), debug build, Settings scrolled
with `adb shell input swipe`; stored rows read with `run-as … sqlite3`, compared with the logcat sum of counted
events (`adb logcat -s ScrollMeter:D`).

| Check | Result |
|---|---|
| Flush after a quiet 12 s | DB 242.026 mm / 22 events = logcat 242.027 mm / 22 (display rounding) |
| Idle session (> 60 s) written by the ticker | 1 session, 242.026 mm, 21 counted events |
| `kill -9` right after scrolling | the unflushed ~4 s lost (463.85 mm); the system restarted the service, next flush added up (704.19 mm) |
| `am force-stop` right after scrolling | the unflushed ~3 s lost (94.58 mm); **Android also removes the service from `enabled_accessibility_services`** — measurement is off until the user turns it on again (Home shows *Měření je vypnuté*) |
| Screen off → on | measurement continues, nothing lost |
| Two apps | one row per package; debug test mode measures ScrollMeter live but never stores it |
| Reinstall (`adb install -r`) | service stays enabled, data kept |
| Reboot (`adb reboot`) | data kept (1876.274 mm, 4 usage rows); the service rebinds by itself; the next flush adds up (+90.774 mm) |
| Release build (R8, signed locally with the debug key, never distributed) | runs; Room works under R8; Home shows the measured 0.47 m; no developer tools; no crash |
| Time in app vs the system (`dumpsys usagestats`, today) | launcher 13:34.4 vs 13:34 · Settings 3:19.7 vs 3:20 · googlesdksetup 1.1 s vs 0:01 |

Owed on a real phone (handoff.md "Dluh ověření"): a restart of the ColorOS test phone (does the service come back
there?), and time in app for today and yesterday against Digital Wellbeing (± 5 %).

## Battery (Phase 8)

Emulator `scrollmeter34` (API 34, `google_apis` arm64 on an Apple-silicon Mac), signed release build, the service on.
`dumpsys battery unplug` + `dumpsys batterystats --reset`, screen kept on (`svc power stayon true`), Android Settings
in the foreground; figures for the app's uid from `dumpsys batterystats` and `--checkin`. Room writes are counted from
`dumpsys dbinfo` (the last 20 statements per connection, sampled every 4 s): one flush = one timestamp of
`UPDATE daily_app_aggregate SET distanceMm …`.

**An emulator has no power profile** (capacity 3000 mAh, discharge 0 mAh, the mAh batterystats estimates are a model
with placeholder currents), so battery % is not measured here. What is meaningful: CPU time, wakelocks, alarms, jobs and
writes — as absolute counts, and CPU relative to the app being scrolled. SPEC §39's 1 h / 8 h / 24 h on real phones
is verification debt V8 (handoff.md).

| Date | Device | Scenario | Duration | Events | Room writes | CPU / wakeups | Battery % | Notes |
|---|---|---|---|---|---|---|---|---|
| 2026-09-25 | emulator API 34 | scroll: `input swipe` down + up every ~1.2 s in Settings | 10 min 24 s | 3 463 (123.2 m) | 115 flushes (11 / min, ~30 events each), every statement 0–1 ms | CPU 1.60 s (1.02 s user + 0.58 s kernel), of it ~0.25 s the sampler's own `dumpsys dbinfo` → ≈ 1.35 s, 0.39 ms per event; Settings itself used 7 min 9 s. Wakelocks 0, alarms 0, jobs 0 | n/a (emulator) | notes 1–3 |
| 2026-09-25 | emulator API 34 | idle: same, no input | 10 min 20 s | 0 | 0 | CPU 0.67 s (0.56 s user + 0.11 s kernel), of it ~0.25 s the sampler → ≈ 0.4 s. Wakelocks 0, alarms 0, jobs 0 | n/a (emulator) | notes 3–4 |
| 2026-09-25 | emulator API 34 | time-in-app sync (D19): 10 app opens (`am start`, 4 s, Home) with Usage access, then 10 without; one warm-up open before each | 2 × ~70 s | — | — | CPU 1.27 s with access vs 1.15 s without → the sync ≈ 12 ms per open (a single run each, so within noise) | n/a (emulator) | note 5 |

1. batterystats lists the app under "Fg Service" (process state `BFGS`). That is the system's binding, not a foreground
   service of ours: `AccessibilityServiceConnection` binds every accessibility service with
   `BIND_FOREGROUND_SERVICE_WHILE_AWAKE`. The manifest declares no foreground service (`check_manifest_policy.py`).
2. 115 flushes where the 50-event trigger alone would need ~70: the 10 s ticker does not restart after a count
   flush, so under continuous scrolling a small second flush often follows 1–2 s later (53 of the 114 gaps ≤ 2 s, the
   rest 7–10 s). Both are SPEC §16 triggers and each write costs ~0 ms; left as is (handoff.md, open points).
3. The sampler costs the app CPU: `dumpsys dbinfo` runs inside the app's process — 50 dumps in a row took 86 ms of
   the app's CPU, so the ~150 samples of a run account for ~0.25 s. The figures after "→" subtract that.
4. Idle, the pipeline's 10 s ticker still runs (it flushes only when something is pending). It wakes a thread,
   not the phone: no alarm and no wakelock, so it cannot keep a sleeping phone awake — the idle CPU above is that
   ticker plus the runtime's own housekeeping.
5. The emulator has a day of usage history with few apps; a real phone's day has far more usage events, so the
   sync's cost on a phone belongs to V8 as well. It runs only when the app opens, when the service connects with
   the last sync older than 6 h, and when the day changes — never on a timer.
