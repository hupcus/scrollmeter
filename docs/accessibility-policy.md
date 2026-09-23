# Accessibility policy

## What we use

One `AccessibilityService`, `com.scrollmeter.app.accessibility.ScrollAccessibilityService`, declared with:

```xml
<accessibility-service
    android:accessibilityEventTypes="typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="0"
    android:canRetrieveWindowContent="false"
    android:isAccessibilityTool="false"
    android:description="@string/accessibility_service_description" />
```

- Event type: `TYPE_VIEW_SCROLLED` only.
- Fields read: `eventTime`, `packageName`, `windowId`, `className`, `scrollDeltaX`, `scrollDeltaY`, `scrollX`, `scrollY`.
- Stored: aggregated distance and counters per day and package; in debug builds a RAM ring buffer of the fields above.

## What we never do

- Read or store text, messages, passwords, URLs, form values, content descriptions or the node tree.
- Use `canRetrieveWindowContent="true"`, `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, `FLAG_SEND_MOTION_EVENTS`,
  touch exploration, gesture capture or `performGlobalAction`.
- Click, type, scroll or change settings on the user's behalf.
- Take screenshots, run OCR, draw overlays, block apps, intercept touches.
- Request `INTERNET`, `QUERY_ALL_PACKAGES` or any storage permission.

A JVM test (`PolicyGuardTest`) fails the build if any of the forbidden APIs or manifest entries appear in the sources.

`Do not expand requested accessibility capabilities without a documented product need and privacy/policy review.`
(the comment lives above the service class; ADR-013 records the only open question, Chrome/WebView coverage).

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
