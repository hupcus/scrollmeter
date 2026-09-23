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
  ↓ Channel<ScrollSample>(capacity = 4096, onBufferOverflow = DROP_OLDEST)      ← the callback returns here
measurement/ScrollMeasurementEngine (pure Kotlin, single consumer on Dispatchers.Default)
  ↓ ScrollEventValidator     → EXCLUDED (null / own / excluded package), OUTLIER_REJECTED (hypot > 4 × screen diagonal)
  ↓ ScrollFallbackTracker    → dx/dy from scrollX/scrollY difference when deltas are 0 and the SPEC §6 B conditions hold
  ↓ PhysicalScaleProvider    → mmPerPxX, mmPerPxY, CalibrationMethod, confidence
  ↓ ScrollDistanceCalculator → distanceMm = hypot(dx·mmPerPxX, dy·mmPerPxY), plus horizontal / vertical components
  ↓ MeasurementResult(sample, source ∈ {DIRECT_DELTA, FALLBACK_POSITION, UNMEASURABLE, OUTLIER_REJECTED, EXCLUDED}, distanceMm)
aggregation/ScrollAccumulator  (in-memory map (date, packageName) → deltas; flush every 10 s / 50 events / day change / onUnbind / onInterrupt)
aggregation/ScrollSessionManager (gap > 60 s = new session)
  ↓ Room UPSERT-add on Dispatchers.IO (adds deltas and counters to the existing row, never overwrites)
data/repository/ScrollRepository → Flow → Compose UI
debug-only: DebugEventLog (ring buffer of 100 MeasurementResults in RAM) → DebugMeasurementScreen, debug CSV
```

Threading rules:

- `onAccessibilityEvent()` parses primitives and offers to the channel. Nothing else. It is wrapped in
  `try/catch`; a failure increments a diagnostics counter and the event is dropped.
- One consumer coroutine in `serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`,
  cancelled in `onDestroy()` after a final flush.
- Room access only from the accumulator's flush on `Dispatchers.IO`.
- `PackageManager` lookups (labels, icons) happen in the UI layer with caching, never in the pipeline.

## Package layout (spec §4)

```text
com.scrollmeter.app
├── accessibility   ScrollAccessibilityService, AccessibilityEventParser, AccessibilityStatusChecker
├── measurement     ScrollSample, MeasurementConfig, MeasurementResult, ScrollEventValidator,
│                   ScrollFallbackTracker, PhysicalScaleProvider, ScrollDistanceCalculator,
│                   ScrollMeasurementEngine, MeasurementQuality, DeviceGeometry        (pure Kotlin)
├── calibration     CalibrationRepository, CalibrationMethod, DisplayMetricsProvider
├── data            local (ScrollDatabase, dao, entity), repository, model
├── aggregation     ScrollAccumulator, ScrollSessionManager, (DailyAggregationWorker only if needed)
├── ui              onboarding, dashboard, history, apps, calibration, settings, about, components, theme
├── export          CsvExporter
├── AppGraph.kt, ScrollMeterApplication.kt, MainActivity.kt
└── src/debug/…     DebugEventLog, DebugMeasurementScreen, TestListScreen (never in release)
```

## Data model

- `daily_app_aggregate` (PK `date`, `packageName`): `distanceMm`, `horizontalDistanceMm`, `verticalDistanceMm`,
  `rawDeltaXPx`, `rawDeltaYPx`, `measuredEventCount`, `fallbackEventCount`, `unmeasurableEventCount`,
  `rejectedOutlierCount`, `firstEventTimestamp?`, `lastEventTimestamp?`, `calibrationVersion`.
- `scroll_session`: `id`, `packageName`, `startTimestamp`, `endTimestamp`, `distanceMm`, `eventCount` — written when a session closes; no UI in MVP.
- Calibration and settings: DataStore Preferences (`calibrationMethod`, `mmPerPxX`, `mmPerPxY`, `calibratedAt`,
  `deviceManufacturer`, `deviceModel`, `xdpiAtCalibration`, `ydpiAtCalibration`, `calibrationVersion`;
  `dailyGoalMm`, `excludedPackages`, `unitPreference`, `theme`, `showComparisons`, `onboardingCompleted`, `privacyDisclosureAccepted`).
- Per-package compatibility (spec §64) is derived by `SUM` over `daily_app_aggregate`; no extra table.
- `date` is `LocalDate.now(ZoneId.systemDefault())` as an ISO string; weeks are Monday–Sunday (`WeekFields.ISO`).
- Room `version = 1`, `exportSchema = true` (`app/schemas/` in git); destructive migration only in debug builds.

## Build variants

- `debug`: `applicationIdSuffix ".debug"`, debug screens, logcat tag `ScrollMeter`, debug CSV export to `getExternalFilesDir()` for `adb pull`.
- `release`: R8, no debug sources (they live under `src/debug/`), no logging of event content.
- Own package is excluded at runtime via `context.packageName`; the debug test mode lifts the exclusion while the test screen is open.

## Navigation

Bottom bar: Přehled · Historie · Aplikace · Nastavení (Navigation Compose 2.9, type-safe routes).
Onboarding is a separate graph shown until `onboardingCompleted`.
