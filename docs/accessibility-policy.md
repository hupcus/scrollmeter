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
- Stored: aggregated distance and counters per day and package; in debug builds a RAM ring buffer of the fields above.

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
  accessibility types, and the XML pins of the service config.
- `tools/check_manifest_policy.py` — the **merged** debug and release manifests, so a permission a library
  adds is caught too; release must carry no debug `FileProvider`.

`Do not expand requested accessibility capabilities without a documented product need and privacy/policy review.`
(the comment lives above the service class; ADR-013 — Chrome/WebView coverage — was settled by measurement: the
flag stays `false`).

## Separate, optional permission: Usage access (ADR-021, from Phase 3)

Time spent in each app comes from Android's usage statistics (`PACKAGE_USAGE_STATS`, granted by the user in
*Přístup k údajům o využití*), **not** from the accessibility service. It adds no accessibility event type, no
content access and no network; ScrollMeter stores only the minutes per app and day. The app works fully without
it (time then shows as "—"). Disclosure and onboarding step: Phase 7.

## Why minimal

Privacy, user trust, battery, Google Play review, stability, fewer edge cases (spec §5).
Android 14+ may hide `accessibilityDataSensitive` views from a non-tool service; ScrollMeter marks such apps as
limited instead of working around it (spec §67).

## Google Play

- `isAccessibilityTool="false"` — ScrollMeter is not an assistive tool for people with disabilities.
- Prominent disclosure **before** sending the user to Android Accessibility Settings, on its own screen, with an
  explicit affirmative button. Text (Czech, from the spec §30):

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

- Play Console: Accessibility declaration form (use case: measuring scroll distance for the user's own insight;
  core functionality depends on the API; no alternative API provides scroll deltas across apps), a video of the
  onboarding + usage, listing text that names the AccessibilityService use.
- Target API 36 (required for new apps and updates from 2026-08-31).
- Privacy screen in the app (spec §29):

  > ScrollMeter používá službu zpřístupnění pouze k detekci scrollovacích událostí. Nečte ani neukládá text, zprávy, hesla nebo obsah obrazovky. Naměřená data zůstávají v telefonu.

## References

- https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent
- https://developer.android.com/reference/android/view/accessibility/AccessibilityRecord
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://support.google.com/googleplay/android-developer/answer/10964491
- https://developer.android.com/training/package-visibility
