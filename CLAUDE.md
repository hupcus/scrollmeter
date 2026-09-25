# CLAUDE.md — ScrollMeter

Rules for every coding agent in this repo. Loaded into every session — keep it short.
The binding product spec is `docs/SPEC.md`; the phase plan is `PLAN.md`; live state is `handoff.md`.

## What this is

ScrollMeter is an Android app (Kotlin, Jetpack Compose, Material 3) that measures how far
content moves while the user scrolls in other apps and shows it as metres / kilometres.
Data source: an `AccessibilityService` that receives **only** `TYPE_VIEW_SCROLLED`.
Metric: **scroll distance** = physical equivalent of content displacement (fling included),
**not** the path of the finger. Local-only: no backend, no account, no `INTERNET` permission.

## Documents and where things go

| File | Role |
|---|---|
| `docs/SPEC.md` | The binding spec (v1.0, 2026-09-23). When PLAN and SPEC disagree, SPEC wins unless `docs/measurement-decisions.md` records why. |
| `PLAN.md` | Phases 0–8 with scope, steps, verification, Definition of Done. Work in phase order; **Phase 1 is a GO/NO-GO gate** — nothing from Phase 2 on starts before Honza says GO. |
| `handoff.md` | Live state: toolchain, phase status, decisions log, open points. **Update at the end of every phase and whenever a pin changes.** |
| `docs/measurement-decisions.md` | ADR log for every non-obvious Android / measurement decision (spec §72). |
| `docs/accuracy-testing.md` | Device / app compatibility tables and accuracy runs. Numbers come only from measurements, never estimates. |
| `docs/measurement-model.md`, `docs/accessibility-policy.md`, `docs/architecture.md` | Reference docs (spec §73). Change them in the same PR as the code they describe. |

## Toolchain (pinned — verified on this Mac in the DETECT project, 08/2026)

| Thing | Version | Note |
|---|---|---|
| JDK for Gradle | **21** — `/opt/homebrew/opt/openjdk@21` | System Java 25 is not supported by AGP 8. Pin via `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`); `~/.gradle/gradle.properties` already lists Homebrew 21 in `org.gradle.java.installations.paths`. Fallback: `org.gradle.java.home` in `gradle.properties`. |
| Gradle wrapper | **8.14.3** | Gradle 9 breaks AGP 8.x. Homebrew `gradle` 9.6.1 is used only for `gradle wrapper --gradle-version 8.14.3`. |
| AGP | **8.13.2** | Not AGP 9 (built-in Kotlin plugin; KSP/Room ecosystem not ready). |
| Kotlin | 2.3.21 | Compose compiler ships with Kotlin (`org.jetbrains.kotlin.plugin.compose`). |
| KSP | 2.3.11 | Room only. |
| Compose BOM | **2026.06.01** | 2026.08 needs compileSdk 37 + AGP 9.1. |
| AndroidX | Room 2.8.4 · DataStore 1.1.7 · Navigation Compose 2.9.8 · Lifecycle 2.9.4 · Activity Compose 1.13.0 · core-ktx 1.18.0 · WorkManager 2.10.5 (only if used) · coroutines 1.10.2 · kotlinx-serialization 1.9.0 | |
| Tests | JUnit 4.13.2 · Robolectric 4.16 · Truth 1.4.5 · androidx.test junit 1.3.0 · Compose ui-test | |
| SDK | compileSdk 36 · targetSdk 36 · **minSdk 29** (ADR-033; SPEC says 28) | `~/Library/Android/sdk` has platforms 31–36, build-tools 34–36, cmdline-tools 19. |
| DI | **none** — manual `AppGraph` on `ScrollMeterApplication` | No Hilt in MVP (ADR-003). |

Bumping a pin is allowed only when the build demands it; record old → new and why in `handoff.md` in the same PR.

## Commands

```bash
./gradlew assembleDebug                 # build
./gradlew testDebugUnitTest             # JVM unit tests (the measurement engine is pure Kotlin)
./gradlew lintDebug                     # Android lint
./gradlew installDebug                  # install on the connected phone (applicationId com.scrollmeter.app.debug)
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell settings put secure enabled_accessibility_services com.scrollmeter.app.debug/com.scrollmeter.app.accessibility.ScrollAccessibilityService
$ADB shell settings put secure accessibility_enabled 1          # POC only — real users go through Settings + disclosure
$ADB shell settings delete secure enabled_accessibility_services # disable again (an empty "put" fails with "Bad arguments")
$ADB shell input swipe 540 1800 540 600 800                      # slow swipe — still lifts with velocity, may fling a little
python3 tools/device_accuracy.py --surface view,column          # repeatable Tests A–E on the debug test list (no-fling drags: motionevent + 0.6 s hold)
$ADB shell input swipe 540 1800 540 600 150                      # fast swipe → fling
$ADB logcat -s ScrollMeter:D                                     # one line per event in debug builds
$ADB exec-out run-as com.scrollmeter.app.debug cat files/debug/recording.csv > run.csv  # the debug recording, survives process kills
$ADB shell dumpsys activity exit-info com.scrollmeter.app.debug  # why/when the system killed the process
$ADB exec-out screencap -p > "$SCRATCH/shot.png"                 # screenshots: let a subagent look; keep images out of the main context
```

Device gotchas (OnePlus CPH2399, Android 14):
- `settings put secure …` needs Developer options → **Zakázat sledování oprávnění** (very bottom); it survives a reboot.
- `uiautomator dump` unbinds every accessibility service while it runs — the home screen then says "not running". Read our own layout with `dumpsys activity top` instead (it also sees Views inside a Compose `AndroidView`).
- The debug app opens a dev tool directly: `am start -n com.scrollmeter.app.debug/com.scrollmeter.app.MainActivity --es devtool testlist` (or `debug`).

Before calling a phase done: `assembleDebug` + `testDebugUnitTest` + `lintDebug` green, the on-device check from that phase's DoD in `PLAN.md`, `handoff.md` updated. Gradle output goes to a file in the scratchpad and is grepped, never read whole.

## Test device

OnePlus **CPH2399** (Nord 2T), Android 14 / API 34, serial `W84LFE856LTWKNMN`, 1080×2400 px,
physical `xdpi 403.411 / ydpi 401.052`, logical `densityDpi 480` (19 % above the physical value —
exactly why `densityDpi` is never used for distance), 60/90 Hz. Installed for tests: Chrome,
Instagram, Facebook, Messenger, YouTube, TikTok, X, Play Store, Google Maps, Seznam Mapy.
**Reddit is not installed** — Honza installs it before the Phase 1 app matrix. Launcher
`com.android.launcher`, keyboard `com.google.android.inputmethod.latin`. No emulator is installed
(Phase 8 adds them via `sdkmanager`); no Android Studio — everything runs from the CLI.

## Hard rules (do not relax without an ADR and Honza's explicit OK)

- The service receives **only `typeViewScrolled`**; `canRetrieveWindowContent="false"`, `isAccessibilityTool="false"`, no `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, no `FLAG_SEND_MOTION_EVENTS`, no touch exploration.
- Never read or store text, content descriptions, node trees, URLs or form values from events. Per event we keep: timestamp, package, window id, class name, deltas, derived distance.
- No `INTERNET`, no `QUERY_ALL_PACKAGES`, no foreground service, no overlay, no analytics SDK.
- `onAccessibilityEvent()` does only: parse primitives → push to a channel → return. No Room, no I/O, no `PackageManager` there.
- One Room write per event is forbidden; the accumulator flushes (constants in `MeasurementConfig`).
- Debug screen, measurement-test screen and raw-event logging live under `src/debug/` — never in release.
- Historical `distanceMm` is never recomputed after a recalibration (spec §65).
- Every tunable constant lives in `measurement/MeasurementConfig.kt`.
- Never claim an accuracy number that was not measured.

## Conventions

- Code, identifiers, comments, commits, branches: English. UI strings: Czech (`values/`) first, English (`values-en/`) in Phase 7; tykání as in the spec's examples.
- Package `com.scrollmeter.app`, single `app` module, layout per spec §4.
- `measurement/` is pure Kotlin — no `android.*` imports — so it runs as JVM unit tests. Only `accessibility/AccessibilityEventParser` touches `AccessibilityEvent`.
- Git: this repo has one session at a time, so committing in this checkout is fine (the hh-main worktree rule is about that shared tree). Branch per phase `phase-N-<slug>`, conventional commits, trailer `Co-Authored-By: <agent> <noreply@anthropic.com>`. Never commit to `main` directly — `main` moves only by PR merge, and the merge is Honza's word („mergni"). Tag `v0.N` after each merged phase.
- Review: `/topshit` once per phase over the cumulative diff. Stage 2 before "done" = unit tests + the on-device DoD + a security read of the manifest, the accessibility config and every exported component.
- Machine specifics (`local.properties`, keystores, `.env*`) stay out of git.
