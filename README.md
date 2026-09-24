# ScrollMeter

ScrollMeter is an Android app that measures how far content moves while you
scroll across your phone and converts that movement into real-world distance.

It uses Android Accessibility scroll events and processes all measurement data
locally on the device.

ScrollMeter measures scroll distance, not the raw physical trajectory of a
finger across the touchscreen.

## Status

Project founded 2026-09-23. Merged: Phase 0 (bootstrap, `v0.0`), Phase 1 (measurement proof of concept,
GO/NO-GO gate, `v0.1`), Phase 2 (calibration: card calibration, accuracy screen, accuracy tooling, `v0.2`),
Phase 3 (persistence: Room, flushes, sessions, settings, optional time in app, `v0.3`), Phase 4 (dashboard, `v0.4`),
Phase 5 (history, apps, app detail, bottom navigation, `v0.5`).
Manual on-phone checks still owed are listed under "Dluh ověření" in `handoff.md`. See `PLAN.md` (phases,
gates) and `handoff.md` (live state).

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
| `tools/` | `analyze_debug_csv.py` (per-app analysis of debug CSVs), `device_accuracy.py` (adb-driven accuracy tests), `accuracy.py` (ground truth CSV vs measured CSV → MAE, MAPE) |
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

## Calibration (Phase 2)

Home → *Zkalibrovat displej* (or *Přesnost měření* → *Zkalibrovat platební kartou*). Hold the phone upright,
lay a payment card on the screen right of the blue vertical line with its top edge on the top line, and
move the bottom line to the card's bottom edge (slider, then − / + for single pixels) → *Uložit kalibraci*.
*Přesnost měření* then shows *Platební karta · 1 pixel = … mm* and the date. The card applies only to the
phone and display resolution it was made on (ADR-024); *Přeskočit – použít automatický odhad* goes back to
`xdpi`/`ydpi`. New calibration applies to new events only.

Accuracy with the calibration in force: `python3 tools/device_accuracy.py --surface view,column --csv-out out/`
then `python3 tools/accuracy.py out/ground_truth.csv out/measured.csv`. The test list itself shows MAE / MAPE
over the runs of a series (*Vynulovat* closes a run, *Nová série* starts over).

## Dashboard (Phase 4)

*Přehled* shows today's distance in a ring against the daily goal (500 m by default, set in *Nastavení*),
this week / month / in total, the top apps today with their names and icons, and one comparison ("To je
přibližně délka jednoho běžeckého okruhu."). Every number is live — stored plus what the service has not
written yet. When the service is off, a red banner with *Zapnout měření* comes first. With Usage access the
app rows add time in app and m/min; without it they show scroll time, and a card offers the feature once
(*Ukázat jak* → a disclosure screen → Android settings).

## History and apps (Phase 5)

A bottom bar switches between *Přehled*, *Historie*, *Aplikace* and *Nastavení*. *Historie* charts the
distance per day (7 or 30 days) or per month (12 months); tap a bar for its value; below it the average,
highest and lowest day and the total, counted per day from the first measured day. *Aplikace* lists the apps
of *Dnes / 7 dní / 30 dní / Celkem* by distance or time, with each app's share and a measurement-quality word
(never an accuracy percentage — ADR-029); apps with time but no scroll data (YouTube) show "—". A row opens
the app's detail with its 30-day charts.

## Settings, export and notifications (Phase 6)

*Nastavení* holds the service and Usage-access status, *Přesnost měření*, the daily goal (100 m – 5 km or a
custom 10 m – 100 km), *Vyloučené aplikace* (launcher, keyboard and System UI are suggested; an excluded app
disappears from every total, history and export and comes back when switched on again), units, theme,
the three opt-in notifications (goal reached, new record, yesterday's summary — each at most once a day; on
Android 13+ the system asks for permission when one is switched on), *Exportovat CSV* (save `per_app.csv` /
`daily_summary.csv` through the system file picker, or share both), *Smazat všechna data* (optionally with the
settings and the card calibration), *Soukromí* and *O aplikaci*; in debug builds also the developer screens.
The CSV format is in ADR-031: comma separated, dot decimals, UTF-8 with BOM, an empty field means unknown.

## Stored data and time in app (Phase 3)

Distances are stored per day and app in an app-private Room database (`scrollmeter.db`) and survive app
restarts; Home shows *Dnes* from it (at most 10 s behind the live counter). Time in app is optional: grant
*Přístup k údajům o využití* to ScrollMeter in Android settings (on an emulator:
`adb shell appops set com.scrollmeter.app.debug GET_USAGE_STATS allow`) and open the app — it snapshots the
last days into `daily_app_usage`. Inspect a debug build's data with
`adb shell "run-as com.scrollmeter.app.debug sqlite3 databases/scrollmeter.db 'select * from daily_app_aggregate'"`.

Note: `adb shell am force-stop` turns the accessibility service off (Android removes it from the enabled list);
turn it on again in Usnadnění.

The debug CSV holds numbers and identifiers only (time, package, window id, view class, deltas, scroll
positions, distance, source, accepted/rejected) — never text from the screen.
