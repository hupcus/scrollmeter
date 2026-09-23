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
4. **Own list with ground truth:** *Testovací seznam* → *Vynulovat* → scroll the list and the carousel by
   hand. Each axis shows ground truth px, measured px and the error. Automated version (Tests A, B, C, D, E
   of spec §36): `python3 tools/device_accuracy.py --markdown`.
5. **Another app:** *Debug měření* → *Vymazat* → switch to the app and scroll for 20–30 s (slow scrolls,
   flings, horizontal carousels) → back to ScrollMeter → *Debug měření* → *Export CSV* (the snackbar shows
   the file name; *Sdílet* sends it elsewhere) → *Vymazat* before the next app. Each export is named after
   the app with the most events.
6. **Collect and analyse:**
   ```bash
   $ADB pull /sdcard/Android/data/com.scrollmeter.app.debug/files/debug/ runs/
   python3 tools/analyze_debug_csv.py runs/debug          # per-app table: coverage, fallback, outliers, duplicates
   $ADB logcat -s ScrollMeter:D                           # one line per event, live
   ```
7. **Switch the service off** when done: `$ADB shell settings put secure enabled_accessibility_services ""`
   or in Accessibility settings.

The debug CSV holds numbers and identifiers only (time, package, window id, view class, deltas, scroll
positions, distance, source, accepted/rejected) — never text from the screen.
