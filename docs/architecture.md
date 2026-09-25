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
  ↓ after a committed write: NotificationWatcher.check (opt-in goal / record / summary, once a day each — ADR-031)
data/repository/ScrollRepository → Room Flows (day / week / month / lifetime, per app, per day) → Compose UI

usage/UsageEventsSource (UsageStatsManager.queryEvents; needs Usage access — ADR-021)
  ↓ UsageEventSample(timestamp, package, activity, kind)
usage/ForegroundTimeAggregator (pure Kotlin: foreground intervals per local day — ADR-025)
  ↓ UsageSyncer (window from the last sync, replace whole days) → ScrollDao.replaceUsage → daily_app_usage
  triggers: MainActivity.onResume · service connect when the last sync is ≥ 6 h old · local day change in the pipeline
data/DataEraser ("Smazat všechna data"): pauses the usage sync, stores the data floor, then under the pipeline's
  write lock bumps the data epoch and clears the tables — the pipeline drops what it held from before (ADR-031)
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
├── format          DistanceFormatter, TimeFormatter, GoalInput (pure Kotlin — ADR-028, ADR-031)
├── insights        DistanceComparisonProvider, Period, DailyLimit, DaySeries, ChartScale, AppRanking,
│                   ScrollShare (pure Kotlin — ADR-028, ADR-029)
├── apps            AppInfoProvider (labels + icons via PackageManager, cached; UI only — ADR-008),
│                   SuggestedExclusions (launcher / keyboard / System UI — ADR-030), PackageNames
├── notifications   NotificationRules, NotificationWatcher (pure Kotlin — ADR-031);
│                   AndroidNotificationPoster (the only Android part — ADR-030)
├── onboarding      OnboardingFlow, AccessibilityGate (pure Kotlin — ADR-032)
├── ui              navigation (routes + bottom bar), onboarding (steps + the disclosure screen), dashboard,
│                   history, apps (list + detail), calibration, usage, settings (+ excluded apps, export,
│                   privacy, about), components (BarChart, PeriodSelector, AppIcon, LiveState, SettingsParts,
│                   SafeWrites), theme
├── export          CsvExporter (pure Kotlin), CsvExportWriter (SAF + share), ExportFileProvider — ADR-030/031
├── data/DataEraser "Smazat všechna data" (write lock + data epoch + usage floor — ADR-031)
├── AppGraph.kt, ScrollMeterApplication.kt, MainActivity.kt
└── src/debug/…     DebugEventLog, DebugMeasurementScreen, TestListScreen (never in release)
```

## Data model

- `daily_app_aggregate` (PK `date`, `packageName`): `distanceMm`, `horizontalDistanceMm`, `verticalDistanceMm`,
  `rawDeltaXPx`, `rawDeltaYPx`, `measuredEventCount`, `fallbackEventCount`, `unmeasurableEventCount`,
  `rejectedOutlierCount`, `firstEventTimestamp?`, `lastEventTimestamp?`, `calibrationVersion` (the newest one used),
  `activeScrollMs` (ADR-022). Every flush adds; nothing is overwritten.
- `scroll_session`: `id`, `packageName`, `startTimestamp`, `endTimestamp`, `distanceMm`, `eventCount` — written when a session closes; no UI in MVP;
  pruned after 90 days (`SESSION_RETENTION_DAYS`, on service connect and at midnight — ADR-030).
- `daily_app_usage` (PK `date`, `packageName`): `foregroundMs`, `launchCount`, `lastEventTimestamp?`, `syncedAt` —
  replaced per date range by each usage sync (ADR-021, ADR-025). Per-app and per-day summaries join it with
  `daily_app_aggregate` through `UNION ALL … GROUP BY` (SQLite has no FULL JOIN); a missing side is NULL → "—".
- Calibration (DataStore Preferences file `calibration`, Phase 2): `method`, `reference_px`, `mm_per_px_x`, `mm_per_px_y`,
  `calibrated_at_ms`, `device_manufacturer`, `device_model`, `xdpi_at_calibration`, `ydpi_at_calibration`,
  `panel_short_px`, `panel_long_px`, `calibration_version` (ADR-024).
- Settings (DataStore Preferences file `settings`, Phase 3; screens in Phase 6): `daily_goal_mm` (default 500 m),
  `excluded_packages`, `unit_preference` (AUTOMATIC / METRES / KILOMETRES), `theme`, `show_comparisons`,
  `onboarding_completed`, `privacy_disclosure_accepted`, `usage_time_card_dismissed`, `notify_goal` /
  `notify_record` / `notify_summary` (opt-in, off) with `notified_<kind>_date` (once a day — ADR-031), and state
  for the usage sync: `usage_sync_last_ms` and `data_floor_ms` (set by "Smazat všechna data": time in app is never
  re-imported from before it). An unreadable value reads as its default (spec §61).
- Excluded packages are filtered out of every read (`packageName NOT IN (:excluded)` in the DAO, the unflushed map
  in `ScrollRepository`) — their rows stay and come back when the exclusion is lifted (ADR-031).
- Per-package compatibility (spec §64) is derived by `SUM` over `daily_app_aggregate`; no extra table.
- `date` is `LocalDate.now(ZoneId.systemDefault())` as an ISO string; weeks are Monday–Sunday (`WeekFields.ISO`).
- Room `version = 1`, `exportSchema = true` (`app/schemas/` in git); destructive migration only in debug builds.

## Build variants

- `debug`: `applicationIdSuffix ".debug"`, debug screens, logcat tag `ScrollMeter`, debug CSV export to `getExternalFilesDir()` for `adb pull`.
- `release`: R8, no debug sources (they live under `src/debug/`), no logging of event content.
- Own package is excluded at runtime via `context.packageName`; the debug test mode lifts the exclusion while the test screen is open.

## Navigation

Přehled is the one top-level screen — no bottom bar since Phase 9 (ADR-036); everything else opens over it
with a way back (Navigation Compose 2.9.8, `@Serializable` routes — D14), in `ui/navigation/ScrollMeterNavHost`:

- Přehled (*Dnes*, *Tento týden*, *Tento měsíc*, *Všechny aplikace*) → *Statistiky* `PeriodRoute(kind, anchor)`
  (`ui/period/PeriodScreen`; Den / Týden / Měsíc and ‹ › are state inside it, a bar opens that day as a new
  `PeriodRoute` on top — `openFrom`, since `launchSingleTop` would replace the week),
- Statistiky or a Přehled app row → detail `AppDetailRoute(packageName, kind, anchor)` for that period; both
  routes validate their arguments on arrival (`PackageNames.isValid`, `Period.parse`) and leave otherwise,
- Přehled gear → Nastavení → Vyloučené aplikace · Export CSV · Soukromí · O aplikaci (limit, units, theme and
  delete are dialogs); Nastavení → developer screens (`DevToolRoute`, only when `DevTools.entries` is non-empty — debug),
- Přehled or Nastavení → Přesnost měření → Kalibrace displeje (saving or skipping lands back on Přesnost),
- Přehled card or Nastavení → Čas v aplikacích (the Usage-access disclosure; closes itself once access is granted),
- Přehled banner or Nastavení → the accessibility settings, through `DisclosureRoute` while the disclosure has not
  been accepted (`AccessibilityGate`, ADR-032).

A "Zpět" tap leaves a screen only while it is the resumed one, so a double tap cannot pop the screen below.

**Onboarding** (spec §31, ADR-032): `MainActivity` shows `OnboardingScreen` instead of the NavHost until
`Settings.onboardingCompleted` (nothing while the settings load, so neither flashes). Its six steps are one
composable with a saved step index — not routes: "Zpět" walks back a step, the calibration screen is reused
inline, Usage access is the existing screen with a step header. A debug developer screen asked for by the launch
intent skips the onboarding. *Smazat data i nastavení* clears the flag, so the onboarding returns.

**Languages** (D17, ADR-032): Czech in `values/` is the default, English in `values-en/`; `number_locale` in each
sets how numbers are written. `generateLocaleConfig` lists cs + en for Android 13+'s per-app language, and
`localeFilters` keeps library translations to those two.
