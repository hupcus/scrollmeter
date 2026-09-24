# Accessibility policy

## What we use

One `AccessibilityService`, `com.scrollmeter.app.accessibility.ScrollAccessibilityService`, declared with:

```xml
<accessibility-service
    android:accessibilityEventTypes="typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:notificationTimeout="0"
    android:canRetrieveWindowContent="false"
    android:isAccessibilityTool="false"
    android:description="@string/accessibility_service_description" />
```

The `<service>` is `android:exported="false"` and protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`
(ADR-018): only the system binds it.

- Event type: `TYPE_VIEW_SCROLLED` only.
- Fields read: `eventTime`, `packageName`, `windowId`, `className`, `scrollDeltaX`, `scrollDeltaY`, `scrollX`, `scrollY`,
  `maxScrollX`, `maxScrollY` — in `AccessibilityEventParser`, the only class that touches `AccessibilityEvent`.
- Stored (Room, app-private; no cloud backup and no device-to-device transfer — `allowBackup=false` plus
  `dataExtractionRules` excluding everything, ADR-027): distance, event counters, first/last event time and active
  scroll time per day and package, plus closed scroll sessions (package, start, end, distance, event count).
  Never an individual event. ScrollMeter's own package is never stored. Debug builds additionally keep the
  fields above in a RAM ring buffer and a debug CSV.

## What we never do

- Read or store text, messages, passwords, URLs, form values, content descriptions or the node tree.
- Use `canRetrieveWindowContent="true"`, `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, `FLAG_SEND_MOTION_EVENTS`,
  touch exploration, gesture capture or `performGlobalAction`.
- Click, type, scroll or change settings on the user's behalf.
- Take screenshots, run OCR, draw overlays, block apps, intercept touches.
- Request `INTERNET`, `QUERY_ALL_PACKAGES` or any storage permission.

Two guards fail CI:
- `PolicyGuardTest` (JVM) — forbidden APIs, flags and manifest entries in the app's own sources (Kotlin, Java,
  XML; comments ignored, string literals kept), content reads or runtime `setServiceInfo` in any file touching
  accessibility types, the XML pins of the service config, the two `<queries>` intents, and every `<provider>`
  being a closed FileProvider (not exported, per-URI grants, paths in the manifest meta-data).
- `tools/check_manifest_policy.py` — the **merged** debug and release manifests, so a permission a library
  adds is caught too (`uses-permission` and `uses-permission-sdk-23`, against the allowlist); `allowBackup=false`
  and `dataExtractionRules` present; no exported component without a permission except the launcher activity;
  release providers are an exact allowlist — the CSV share provider (`….exports`, `cache/exports/` only, not
  exported — ADR-030) and androidx.startup's; the debug `FileProvider` (`….devtools.files`) never ships.

`Do not expand requested accessibility capabilities without a documented product need and privacy/policy review.`
(the comment lives above the service class; ADR-013 — Chrome/WebView coverage — was settled by measurement: the
flag stays `false`).

## Separate, optional permission: Usage access (ADR-021, from Phase 3)

Time spent in each app comes from Android's usage statistics (`PACKAGE_USAGE_STATS`, granted by the user in
*Přístup k údajům o využití*), **not** from the accessibility service. It adds no accessibility event type, no
content access and no network; ScrollMeter stores only the foreground milliseconds, the number of launches and the
time of the last event per app and day (`daily_app_usage`). Activity class names are read only to pair an
activity's start with its end and are never stored (ADR-025). `UsageEventsSource` is the only class that touches
`android.app.usage` (`PolicyGuardTest` pins it). The app works fully without it (time then shows as "—").
It is offered as the optional last onboarding step (*Povolit* / *Teď ne*, ADR-032) and later from the dashboard
card and Nastavení; revoking it in Android's settings is picked up on the next resume and time turns back to "—".

App names and icons on the dashboard come from `PackageManager` through a `<queries>` entry for launcher
activities (ADR-008) plus one for the home screen (ADR-030, so the launcher can be suggested as an exclusion) — no
`QUERY_ALL_PACKAGES`; `PolicyGuardTest` allows exactly those two MAIN intents. Before the Usage-access
settings open, the app shows its own disclosure screen (*Čas v aplikacích*): what is read, what is stored, what is
not, and that everything works without it; only the explicit *Povolit* button opens the settings.

Permissions are an **allowlist** in both guards: `PACKAGE_USAGE_STATS` (Phase 3) and `POST_NOTIFICATIONS`
(Phase 6, ADR-030 — asked only when the user switches a notification on; nothing is measured differently without
it). Anything else fails CI and needs an ADR plus Honza's OK.

## Export and deletion (ADR-030, ADR-031)

The CSV export leaves the phone only where the user sends it: *Uložit* opens the system file picker (Storage
Access Framework — no storage permission), *Sdílet* hands two files from the app's cache to the app the user picks
in the share sheet, with read access for that share only. The files hold per day and app what the database holds:
distance, event counts, time in app — never content. Excluded apps are left out. *Smazat všechna data* removes
every measured value, the time in app, sessions and the shared copies; optionally the settings and the card
calibration too.

## Why minimal

Privacy, user trust, battery, Google Play review, stability, fewer edge cases (spec §5).
Android 14+ may hide `accessibilityDataSensitive` views from a non-tool service; ScrollMeter marks such apps as
limited instead of working around it (spec §67).

## Google Play

- `isAccessibilityTool="false"` — ScrollMeter is not an assistive tool for people with disabilities.
- **Prominent disclosure** before Android's accessibility settings open, on its own screen, with an explicit
  affirmative button (spec §30, User Data policy). The Czech text is the spec's word for word and
  `TranslationsTest` pins it against `docs/SPEC.md`; English in `values-en/`:

  > **Povolit měření scrollování**
  >
  > ScrollMeter potřebuje přístup ke službě Zpřístupnění, aby Android aplikaci informoval o scrollování v ostatních aplikacích.
  >
  > ScrollMeter používá pouze scrollovací události a název aplikace, ve které ke scrollování došlo.
  >
  > Nečte ani neukládá text obrazovky, zprávy, hesla ani obsah formulářů.
  >
  > Data jsou zpracována a uložena pouze v tomto zařízení.
  >
  > [Rozumím a chci pokračovat] → then [Otevřít nastavení zpřístupnění]

- **Where it is enforced** (ADR-032):
  - The app starts in the onboarding (spec §31) until it is finished: *Kolik toho nascrolluješ* → *Jak měření
    funguje* → the disclosure → *Zapni měření* (Android's settings, detected on return) → calibration → optional
    *Čas v aplikacích*. `OnboardingFlow` does not let the user past the disclosure without the button, nor past
    the setup while the service is off. "Zpět" or leaving the screen is not consent.
  - Every other way into the accessibility settings — the dashboard's *Zapnout měření*, Nastavení › Služba
    Usnadnění — goes through `AccessibilityGate`: without a stored acceptance the disclosure screen comes first.
    Only `MainActivity` builds the settings intent.
  - *Smazat data i nastavení* clears the acceptance too; the onboarding (and the disclosure) come back.
  - Debug builds only: a developer screen requested by the launch intent skips the onboarding (automation).
- Play Console answers, listing texts, Data safety and the video scenario: `docs/play-listing.md`.
- Target API 36 (required for new apps and updates from 2026-08-31).
- Privacy screen in the app (spec §29), opening with the spec's sentence (pinned by `TranslationsTest`):

  > ScrollMeter používá službu zpřístupnění pouze k detekci scrollovacích událostí. Nečte ani neukládá text, zprávy, hesla nebo obsah obrazovky. Naměřená data zůstávají v telefonu.

  followed by what is stored, what never is, time in app, no internet, no backup / device transfer, and why the
  accessibility service.

## References

- https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent
- https://developer.android.com/reference/android/view/accessibility/AccessibilityRecord
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://support.google.com/googleplay/android-developer/answer/10964491
- https://developer.android.com/training/package-visibility
