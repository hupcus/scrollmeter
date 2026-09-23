# ScrollMeter

ScrollMeter is an Android app that measures how far content moves while you
scroll across your phone and converts that movement into real-world distance.

It uses Android Accessibility scroll events and processes all measurement data
locally on the device.

ScrollMeter measures scroll distance, not the raw physical trajectory of a
finger across the touchscreen.

## Status

Project founded 2026-09-23. Phase 0 (bootstrap): buildable Compose skeleton with the
pinned toolchain and CI — see `PLAN.md` (phases, gates) and `handoff.md` (live state).

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

## How to test the measurement POC

Filled in at the end of Phase 1 (see `PLAN.md`, Phase 1 DoD).
