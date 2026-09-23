# Accuracy testing

Numbers in this file come from measurements only. An empty cell means "not measured yet".

## Metrics (spec §38)

```text
absoluteErrorMm  = |measuredMm − groundTruthMm|
MAE              = mean(absoluteErrorMm)
percentageError  = |measured − groundTruth| / groundTruth × 100
MAPE             = mean(percentageError)
```

Target on the app's own test list: MAPE < 5 % (ideal < 2 %). Third-party apps have no ground truth → no
accuracy claim, only compatibility.

## Protocol

Preparation: `./gradlew installDebug`, enable the service via adb (commands in `CLAUDE.md`), open the debug
build's **Test list** screen (ground truth = Σ|consumed| of the list's own scroll), or the target app.

| Test | What | How (adb, phone in portrait, 1080×2400) | Pass |
|---|---|---|---|
| A linear | known pixel distance | slow swipes, no fling: `input swipe 540 1800 540 600 800` (≈ 1200 px − touch slop); repeat for ≈ 500 / 1000 / 5000 px totals | MAPE < 5 % |
| B manual | 20 similar swipes by hand | Honza scrolls; compare Σ ground truth vs Σ measured | MAPE < 5 % |
| C fling | short fast swipe, let it coast | `input swipe 540 1800 540 1200 120` | events keep arriving after the finger lifts; total ≈ ground truth |
| D horizontal | carousel | `input swipe 900 1200 100 1200 800` on the `LazyRow` | measured ≈ ground truth |
| E reversal | down 1000 px then up 1000 px | two opposite slow swipes | ≈ 2000 px distance, not 0 |

Export the debug CSV (`timestamp,package,dx_px,dy_px,distance_mm,source,status`), pull it with
`adb pull /sdcard/Android/data/com.scrollmeter.app.debug/files/debug/`, run `python3 tools/accuracy.py`.

## Devices

| Device | OS / API | Resolution | xdpi / ydpi | densityDpi | Manual card mm/px | Notes |
|---|---|---|---|---|---|---|
| OnePlus CPH2399 (Nord 2T) | Android 14 / 34 | 1080×2400 | 403.411 / 401.052 | 480 | | 60/90 Hz; primary test phone |

## App compatibility matrix (Phase 1)

| App | Package | `TYPE_VIEW_SCROLLED` emitted? | `scrollDelta` usable? | Fallback needed? | Outliers | Duplicates (≤ 5 ms) | Subjective coverage | Notes |
|---|---|---|---|---|---|---|---|---|
| Chrome | `com.android.chrome` | | | | | | | R2: also test `canRetrieveWindowContent=true` on a throwaway branch if silent |
| Instagram | `com.instagram.android` | | | | | | | |
| Facebook | `com.facebook.katana` | | | | | | | includes in-app browser (WebView) |
| Reddit | `com.reddit.frontpage` | | | | | | | install first |
| YouTube | `com.google.android.youtube` | | | | | | | |
| TikTok | `com.zhiliaoapp.musically` | | | | | | | vertical paging feed |
| X | `com.twitter.android` | | | | | | | |
| Google Play | `com.android.vending` | | | | | | | |
| Google Maps | `com.google.android.apps.maps` | | | | | | | lists / bottom panels, not the map |
| Settings | `com.android.settings` | | | | | | | RecyclerView |
| ScrollMeter test list | `com.scrollmeter.app.debug` | | | | | | | Compose, ground truth available |

## Accuracy runs

| Date | Device | Calibration | Test | Ground truth mm | Measured mm | MAE mm | MAPE % | Notes |
|---|---|---|---|---|---|---|---|---|
| | | | | | | | | |

## Battery (Phase 8)

| Date | Device | Scenario | Duration | Events | Room writes | CPU / wakeups | Battery % | Notes |
|---|---|---|---|---|---|---|---|---|
| | | | | | | | | |
