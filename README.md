# ScrollMeter

ScrollMeter is an Android app that measures how far content moves while you
scroll across your phone and converts that movement into real-world distance.

It uses Android Accessibility scroll events and processes all measurement data
locally on the device.

ScrollMeter measures scroll distance, not the raw physical trajectory of a
finger across the touchscreen.

## Status

Project founded 2026-09-23. Phase 0 (bootstrap) is merged (`v0.0`). Phase 1 — the measurement
proof of concept and GO/NO-GO gate — is in review: accessibility service, pure-Kotlin measurement
engine, debug screens. See `PLAN.md` (phases, gates) and `handoff.md` (live state).

## Repository map

| Path | What it is |
|---|---|
| `docs/SPEC.md` | The product + technical specification, v1.0 (binding) |
| `PLAN.md` | Development plan by phases 0–8 with Definition of Done (Czech) |
| `handoff.md` | Live state: toolchain pins, phase status, decisions log (Czech) |
| `docs/measurement-model.md` | What scroll distance is, formulas, calibration, fallback, fling |
| `docs/accessibility-policy.md` | Which Accessibility API we use, what we never read, Play disclosure |
| `docs/accuracy-testing.md` | Test protocol and the device / app compatibility tables |
| `docs/architecture.md` | Event pipeline, package layout, threading, data model |
| `docs/measurement-decisions.md` | ADR log for non-obvious decisions |
| `docs/prompts/` | Kick-off prompts for coding sessions |
| `tools/` | `analyze_debug_csv.py` (per-app analysis of debug CSVs), `device_accuracy.py` (adb-driven accuracy tests) |
| `CLAUDE.md` | Rules for coding agents working in this repo |

## Build and run (available from Phase 0)

```bash
./gradlew assembleDebug            # build
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew lintDebug                # Android lint
./gradlew installDebug             # install on the connected phone
```

Requires JDK 21 for Gradle (pinned in `gradle/gradle-daemon-jvm.properties`)
and the Android SDK with platform 36 (`local.properties` → `sdk.dir`).

## How to test the measurement POC (Phase 1)

The debug build (`com.scrollmeter.app.debug`) adds two developer screens to the home screen:
**Debug měření** (the last 100 scroll events live) and **Testovací seznam** (our own list with an
exact ground truth). Release builds contain neither.

1. **Install:** `./gradlew installDebug` with the phone connected over USB (USB debugging on).
2. **Enable the service** — either by hand: Settings → Accessibility → Downloaded / Installed apps →
   *ScrollMeter — měření scrollování* → On; or over adb (POC only):
   ```bash
   ADB=~/Library/Android/sdk/platform-tools/adb
   $ADB shell settings put secure enabled_accessibility_services com.scrollmeter.app.debug/com.scrollmeter.app.accessibility.ScrollAccessibilityService
   $ADB shell settings put secure accessibility_enabled 1
   ```
   OnePlus / ColorOS refuses these writes until Developer options → **Zakázat sledování oprávnění**
   (*Disable permission monitoring*, at the very bottom) is on.
3. **Check it runs:** open ScrollMeter — the card says *Měření je zapnuté* and the total since the
   service started grows while you scroll anywhere. `adb shell dumpsys accessibility` lists the service
   with `eventTypes=TYPE_VIEW_SCROLLED`.
4. **Own list with ground truth:** *Testovací seznam* → pick a surface (*View* = classic Android views,
   *Column* = Compose `verticalScroll`, *Lazy* = Compose `LazyColumn`) → *Vynulovat* → scroll the list and
   the carousel by hand. Each axis shows ground truth px, measured px and the error. *Lazy* is expected to
   measure 0 (ADR-019). Automated version (Tests A–E of spec §36):
   `python3 tools/device_accuracy.py --surface view,column,lazy --markdown`.
5. **Other apps:** *Debug měření* → *Vymazat* (the *v souboru* counter drops to 0) → go through the apps one
   after another, 20–30 s each (slow scrolls, flings, horizontal carousels) — no need to come back in
   between. Every event is written through to the phone's app-private storage, so the run survives the
   system killing ScrollMeter (ColorOS does that under memory pressure; the service restarts by itself).
   Apps behind a login screen (Instagram, TikTok, X) need an account to reach a feed.
6. **Collect and analyse** — no export needed; the analyser splits the run per app:
   ```bash
   $ADB exec-out run-as com.scrollmeter.app.debug cat files/debug/recording.csv > run.csv
   python3 tools/analyze_debug_csv.py run.csv             # per-app table: coverage, fallback, outliers, duplicates
   $ADB logcat -s ScrollMeter:D                           # one line per event, live
   ```
   *Export CSV* on the phone writes the same rows plus a device header (needed for the outlier-limit
   column) to `/sdcard/Android/data/com.scrollmeter.app.debug/files/debug/` (`adb pull` that folder);
   *Sdílet* sends it elsewhere.
7. **Switch the service off** when done: `$ADB shell settings delete secure enabled_accessibility_services` (an empty `put` fails with "Bad arguments")
   or in Accessibility settings.

The debug CSV holds numbers and identifiers only (time, package, window id, view class, deltas, scroll
positions, distance, source, accepted/rejected) — never text from the screen.
