# Measurement decisions (ADR log)

One entry per non-obvious Android or measurement decision (spec §72). Newest at the bottom; never delete,
supersede with a new entry that references the old one.

| ADR | Date | Decision | Why | Status |
|---|---|---|---|---|
| ADR-001 | 2026-09-23 | The metric is **scroll distance** (content displacement, fling included, direction changes add up), not the finger's path. | Raw `MotionEvent`s are not available across apps on a stock device; scroll deltas are. Spec §1, §7, §12. | accepted |
| ADR-002 | 2026-09-23 | `minSdk = 28`. | `AccessibilityRecord.getScrollDeltaX/Y()` exist from API 28 and are the primary source. | accepted |
| ADR-003 | 2026-09-23 | No Hilt; manual `AppGraph` of lazy singletons. | Fewer moving parts; Hilt 2.58+ requires AGP 9; pure-Kotlin engine is testable through constructors. | accepted |
| ADR-004 | 2026-09-23 | Toolchain pinned to JDK 21 / Gradle 8.14.3 / AGP 8.13.2 / Kotlin 2.3.21 / Compose BOM 2026.06.01. | Same pins build on this Mac in DETECT (08/2026); AGP 8 does not support JDK 25; AGP 9 breaks the KSP/Room set-up. | accepted |
| ADR-005 | 2026-09-23 | All tunables in `measurement/MeasurementConfig.kt` (outlier factor 4×, fallback gap 2 s, dedupe window 5 ms, flush 10 s / 50 events, session gap 60 s, card 85.60 mm). | Spec §11, §16, §18, §70: one place to retune after real data. | accepted |
| ADR-006 | 2026-09-23 | Duplicate suppression is **off** by default; enabling requires evidence from debug CSVs (same package + windowId + dx + dy within ≤ 5 ms). | Over-eager dedupe under-measures; spec §70. | accepted |
| ADR-007 | 2026-09-23 | Historical `distanceMm` is never recomputed after recalibration; aggregates carry `calibrationVersion`. | Stable history, no raw event retention. Spec §65. | accepted |
| ADR-008 | 2026-09-23 | App labels via `PackageManager` with a `<queries>` element for the launcher intent (`MAIN`/`LAUNCHER`); no `QUERY_ALL_PACKAGES`. | Package visibility on API 30+; the launcher-intent query is Play-compliant and covers the apps people scroll in. Missing label → show the package name. | accepted |
| ADR-009 | 2026-09-23 | No foreground service, no WorkManager for capture. | The accessibility service is system-managed; spec §40, §41. | accepted |
| ADR-010 | 2026-09-23 | Debug screen, test-list screen and raw event logging live in `src/debug/` only. | Never ship raw event handling; spec §34, §66. | accepted |
| ADR-011 | 2026-09-23 | Sessions are persisted (one row per closed session) but have no UI in the MVP. | Cheap to store, enables Phase 1.1 features without re-collecting; spec §18. | accepted |
| ADR-012 | 2026-09-23 | Charts are a custom Compose `Canvas` bar chart; no chart library. | Spec §23; no dependency, no network. | accepted |
| ADR-013 | — | `canRetrieveWindowContent`: stays `false` unless Phase 1 shows Chrome/WebView emits nothing without it — then a **product decision by Honza**. | Spec §5 allows it only when proven necessary; privacy and Play review cost. | open (Phase 1) |
