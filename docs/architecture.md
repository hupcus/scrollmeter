# Architecture

Single `app` module, package `com.scrollmeter.app`, Kotlin + Jetpack Compose + Material 3.
No DI framework: `ScrollMeterApplication` owns an `AppGraph` of lazy singletons and the
`AccessibilityService` reaches it through `application as ScrollMeterApplication`.

## Event pipeline

```text
Android
  ↓ AccessibilityEvent (TYPE_VIEW_SCROLLED only)
accessibility/ScrollAccessibilityService.onAccessibilityEvent()
  ↓ AccessibilityEventParser → ScrollSample(eventTime, packageName, windowId, className, dx, dy, scrollX, scrollY)
  ↓ ScrollPipeline.offer → Channel<ScrollSample>(capacity = 4096, onBufferOverflow = DROP_OLDEST)   ← the callback returns here
aggregation/ScrollPipeline.run (pure Kotlin, one consumer on Dispatchers.Default.limitedParallelism(1))
measurement/ScrollMeasurementEngine
  ↓ ScrollEventValidator     → EXCLUDED (null / own / excluded package), OUTLIER_REJECTED (hypot > 4 × screen diagonal)
  ↓ ScrollFallbackTracker    → dx/dy from scrollX/scrollY difference when deltas are 0 and the SPEC §6 B conditions hold
  ↓ PhysicalScaleProvider    → mmPerPxX, mmPerPxY, CalibrationMethod, confidence, calibrationVersion
                               (stored card calibration where it applies, else xdpi/ydpi — ADR-024)
  ↓ ScrollDistanceCalculator → distanceMm = hypot(dx·mmPerPxX, dy·mmPerPxY), plus horizontal / vertical components
  ↓ MeasurementResult(sample, source ∈ {DIRECT_DELTA, FALLBACK_POSITION, SUPERSEDED_BY_DIRECT, UNMEASURABLE, OUTLIER_REJECTED, EXCLUDED}, distanceMm, calibrationVersion)
aggregation/ScrollAccumulator  (in-memory map (date, packageName) → AggregateDelta, activeScrollMs; own package never stored)
aggregation/ScrollSessionManager (gap > 60 s = new session)
  ↓ flush: every 10 s while pending / 50th event / new local day / onInterrupt / end of the pipeline (unbind, destroy, reconnect)
  ↓ ScrollRepository.write → ScrollDao.addFlush: one transaction of INSERT OR IGNORE + UPDATE x = x + :x (ADR-026)
data/repository/ScrollRepository → Room Flows (day / week / month / lifetime, per app, per day) → Compose UI

usage/UsageEventsSource (UsageStatsManager.queryEvents; needs Usage access — ADR-021)
  ↓ UsageEventSample(timestamp, package, activity, kind)
usage/ForegroundTimeAggregator (pure Kotlin: foreground intervals per local day — ADR-025)
  ↓ UsageSyncer (window from the last sync, replace whole days) → ScrollDao.replaceUsage → daily_app_usage
  triggers: MainActivity.onResume · service connect when the last sync is ≥ 6 h old · local day change in the pipeline
debug-only: DebugEventLog (ring buffer of 100 MeasurementResults in RAM) → DebugMeasurementScreen, debug CSV
```

Threading rules:

- `onAccessibilityEvent()` parses primitives and offers to the channel. Nothing else. It is wrapped in
  `try/catch`; a failure increments a diagnostics counter and the event is dropped.
- One pipeline coroutine in `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` on a
  single-threaded view of `Dispatchers.Default`: engine, accumulator, sessions, the 10 s ticker and the
  `onInterrupt` flush never run in parallel. It first loads the stored calibration and settings (DataStore) —
  samples wait in the channel meanwhile — and child coroutines follow both (`engine.display`,
  `MeasurementSettings.excludedPackages`); they are cancelled when the pipeline ends.
- `onUnbind` / `onDestroy` close the channel: the pipeline drains it and writes a last flush; `onDestroy`
  cancels the scope only after that. A killed process loses at most the unflushed 10 s (ADR-026).
- Room is reached only through the pipeline's flush (writes; Room runs them on its own executor, `NonCancellable`),
  the usage sync on `Dispatchers.IO`, and the UI's Flows.
- `PackageManager` lookups (labels, icons) happen in the UI layer with caching, never in the pipeline.

## Package layout (spec §4)

```text
com.scrollmeter.app
├── accessibility   ScrollAccessibilityService, AccessibilityEventParser, AccessibilityStatusChecker
├── measurement     ScrollSample, MeasurementConfig, MeasurementResult, ScrollEventValidator,
│                   ScrollFallbackTracker, PhysicalScaleProvider, ScrollDistanceCalculator,
│                   ScrollMeasurementEngine, MeasurementQuality, DeviceGeometry        (pure Kotlin)
├── calibration     CalibrationRepository (DataStore), Calibration (ManualCalibration, CalibrationState,
│                   CardCalibration), CalibrationMethod, DisplaySnapshot, DisplayMetricsProvider
├── data            local (ScrollDatabase, ScrollDao, entities), repository (ScrollRepository),
│                   model (DateRange, AppSummary, DaySummary — pure Kotlin)
├── aggregation     ScrollPipeline, ScrollAccumulator, ScrollSessionManager, AggregateDelta   (pure Kotlin)
├── usage           ForegroundTimeAggregator, UsageSyncer, UsageEvents (pure Kotlin);
│                   UsageEventsSource, UsageAccessChecker (the only Android parts)
├── settings        Settings, SettingsRepository (DataStore)
├── format          DistanceFormatter, TimeFormatter (pure Kotlin — ADR-028)
├── insights        DistanceComparisonProvider, TopApps, HistorySeries, ChartScale, AppRanking,
│                   ScrollShare (pure Kotlin — ADR-028, ADR-029)
├── apps            AppInfoProvider (labels + icons via PackageManager, cached; UI only — ADR-008)
├── ui              navigation (routes + bottom bar), dashboard, history, apps (list + detail), calibration,
│                   usage, settings, components (BarChart, PeriodSelector, AppIcon, LiveState), theme;
│                   onboarding and about come in Phases 6–7
├── export          CsvExporter
├── AppGraph.kt, ScrollMeterApplication.kt, MainActivity.kt
└── src/debug/…     DebugEventLog, DebugMeasurementScreen, TestListScreen (never in release)
```

## Data model

- `daily_app_aggregate` (PK `date`, `packageName`): `distanceMm`, `horizontalDistanceMm`, `verticalDistanceMm`,
  `rawDeltaXPx`, `rawDeltaYPx`, `measuredEventCount`, `fallbackEventCount`, `unmeasurableEventCount`,
  `rejectedOutlierCount`, `firstEventTimestamp?`, `lastEventTimestamp?`, `calibrationVersion` (the newest one used),
  `activeScrollMs` (ADR-022). Every flush adds; nothing is overwritten.
- `scroll_session`: `id`, `packageName`, `startTimestamp`, `endTimestamp`, `distanceMm`, `eventCount` — written when a session closes; no UI in MVP.
- `daily_app_usage` (PK `date`, `packageName`): `foregroundMs`, `launchCount`, `lastEventTimestamp?`, `syncedAt` —
  replaced per date range by each usage sync (ADR-021, ADR-025). Per-app and per-day summaries join it with
  `daily_app_aggregate` through `UNION ALL … GROUP BY` (SQLite has no FULL JOIN); a missing side is NULL → "—".
- Calibration (DataStore Preferences file `calibration`, Phase 2): `method`, `reference_px`, `mm_per_px_x`, `mm_per_px_y`,
  `calibrated_at_ms`, `device_manufacturer`, `device_model`, `xdpi_at_calibration`, `ydpi_at_calibration`,
  `panel_short_px`, `panel_long_px`, `calibration_version` (ADR-024).
- Settings (DataStore Preferences file `settings`, Phase 3; screens in Phase 6): `daily_goal_mm` (default 500 m),
  `excluded_packages`, `unit_preference` (AUTOMATIC / METRES / KILOMETRES), `theme`, `show_comparisons`,
  `onboarding_completed`, `privacy_disclosure_accepted`, `usage_time_card_dismissed`, and `usage_sync_last_ms`
  (state for the usage sync). An unreadable value reads as its default (spec §61).
- Per-package compatibility (spec §64) is derived by `SUM` over `daily_app_aggregate`; no extra table.
- `date` is `LocalDate.now(ZoneId.systemDefault())` as an ISO string; weeks are Monday–Sunday (`WeekFields.ISO`).
- Room `version = 1`, `exportSchema = true` (`app/schemas/` in git); destructive migration only in debug builds.

## Build variants

- `debug`: `applicationIdSuffix ".debug"`, debug screens, logcat tag `ScrollMeter`, debug CSV export to `getExternalFilesDir()` for `adb pull`.
- `release`: R8, no debug sources (they live under `src/debug/`), no logging of event content.
- Own package is excluded at runtime via `context.packageName`; the debug test mode lifts the exclusion while the test screen is open.

## Navigation

Bottom bar: Přehled · Historie · Aplikace · Nastavení (Navigation Compose 2.9.8, `@Serializable` routes — D14,
ADR-029), in `ui/navigation/ScrollMeterNavHost`. The bar shows only on those four; switching tabs saves and restores
each tab's state. Below them, without the bar:

- Aplikace (or a top-app row on Přehled) → detail `AppDetailRoute(packageName)`,
- Přehled or Nastavení → Přesnost měření → Kalibrace displeje (saving or skipping lands back on Přesnost),
- Přehled card or Nastavení → Čas v aplikacích (the Usage-access disclosure; closes itself once access is granted),
- Nastavení → developer screens (`DevToolRoute`, registered only when `DevTools.entries` is non-empty — debug).

A "Zpět" tap leaves a screen only while it is the resumed one, so a double tap cannot pop the screen below.
Onboarding (Phase 7) will be a separate graph shown until `onboardingCompleted`.
