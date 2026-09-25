# ScrollMeter

ScrollMeter is an Android app that measures how far content moves while you
scroll across your phone and converts that movement into real-world distance.

It uses Android Accessibility scroll events and processes all measurement data
locally on the device.

ScrollMeter measures scroll distance, not the raw physical trajectory of a
finger across the touchscreen.

## Status

Project founded 2026-09-23. Phases 0–7 merged (`v0.0` … `v0.7`): bootstrap, measurement proof of concept
(GO/NO-GO gate), calibration, persistence with optional time in app, dashboard, history and apps, export and
settings, onboarding with the Play disclosure and English. Phase 8 (release hardening, `v0.8`) adds the signed
release build, the emulator matrix API 28–36, the battery protocol and the first release `v0.1.0`. Phase 9
(`v0.9`, release `v0.2.0`) replaces the goal with a daily limit in colour and History + Apps with one
*Statistiky* screen (ADR-036). The app is for family phones, installed from the APK — not Google Play.
**Requires Android 10 (API 29) or newer** — Android 9 does not deliver the scroll events (ADR-033).
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
| `docs/play-listing.md` | Draft Play listing, Accessibility declaration answers, Data safety, privacy policy text, video scenario |
| `docs/accuracy-testing.md` | Test protocol and the device / app compatibility tables |
| `docs/architecture.md` | Event pipeline, package layout, threading, data model |
| `docs/measurement-decisions.md` | ADR log for non-obvious decisions |
| `docs/prompts/` | Kick-off prompts for coding sessions |
| `tools/` | `analyze_debug_csv.py` (per-app analysis of debug CSVs), `device_accuracy.py` (adb-driven accuracy tests), `accuracy.py` (ground truth CSV vs measured CSV → MAE, MAPE), `check_manifest_policy.py` / `check_release_apk.py` (what a build may ship), `build_release.sh` (signed release) |
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

## Release build (Phase 8)

```bash
tools/build_release.sh     # clean tree only: signed APK + AAB, both checked against the upload certificate, APK checked
```

Signing comes only from environment variables (`SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`, ADR-034); without them `assembleRelease` builds an unsigned APK
(CI does). The script takes the upload key from `~/.android-keystores/scrollmeter-upload.jks` and its password
from the macOS Keychain item `scrollmeter-upload-keystore` — neither is in the repository. Outputs:
`app/build/outputs/apk/release/app-release.apk` (what gets installed — the app is distributed as an APK to family
phones, not through Google Play) and `app/build/outputs/bundle/release/app-release.aab` (kept for a store, none planned).

Release APKs are signed by `CN=ScrollMeter, O=Honza Hubka, C=CZ`, certificate SHA-256
`04:6F:8C:D0:B0:73:23:F7:07:12:E1:12:53:CA:D7:FB:82:03:F2:68:C8:D8:10:53:78:3D:9C:1A:66:9C:0E:88` —
check a downloaded APK with `apksigner verify --print-certs app-release.apk`.

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

## Přehled and Statistiky (Phases 4, 5, 9)

*Přehled* is the one main screen — no bottom bar; *Nastavení* sits behind the gear. The *Dnes* card shows
today's distance against the **daily limit** (500 m by default, or *Bez limitu*): green, orange from 70 %, red
over it, and always in words too ("Zbývá 80 m z limitu 500 m", "Překročeno o 120 m (limit 500 m)"). Below it
*Tento týden* and *Tento měsíc* with their total and average per day, the three apps scrolled most today and
one comparison ("To je přibližně délka jednoho běžeckého okruhu."). Every number is live — stored plus what
the service has not written yet. When the service is off, a red banner with *Zapnout měření* comes first. It
is not a competition: no goals, no records (ADR-036).

Tapping *Dnes*, a week or a month opens **Statistiky**: *Den / Týden / Měsíc* with ‹ ›, for a week or month a
bar per day in its limit colour with the limit as a dashed line (tap a bar to open that day), and every app
of the period from the longest distance down, with the time in the app on the same row — "Chrome
1,21 km (3 h 40 min)". Apps with time but no scroll data (YouTube) show "— (2 h 15 min)" at the end. An app
opens its detail for that period: distance, share, time in app, scroll time, pace, a measurement-quality word
(never an accuracy percentage — ADR-029) and 30-day charts. Durations roll over from minutes to hours to days
("1 d 1 h 1 min"). Time in app needs Usage access; until it is granted a card offers it once (*Ukázat jak* →
a disclosure screen → Android settings), and the rows show no brackets.

## Settings, export and notifications (Phase 6)

*Nastavení* holds the service and Usage-access status, *Přesnost měření*, the daily limit (100 m – 5 km, a
custom 10 m – 100 km, or *Bez limitu*), *Vyloučené aplikace* (launcher, keyboard and System UI are suggested; an
excluded app disappears from every total, history and export and comes back when switched on again), units,
theme, the two opt-in notifications (over the limit, yesterday's summary — each at most once a day; on
Android 13+ the system asks for permission when one is switched on), *Exportovat CSV* (save `per_app.csv` /
`daily_summary.csv` through the system file picker, or share both), *Smazat všechna data* (optionally with the
settings and the card calibration), *Soukromí* and *O aplikaci* (version, device, the total measured since the first day); in debug builds also the
developer screens.
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
