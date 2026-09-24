# Google Play — listing, declarations, Data safety (draft)

Draft for the first Play upload (Phase 8 prepares internal testing; **nothing is submitted without Honza**).
Source of truth for the behaviour described here: `docs/accessibility-policy.md`, ADR-021, ADR-027, ADR-030 –
ADR-032. Every claim below must stay true for the build that is uploaded — re-read this file before each release.

## Store listing

**App name:** ScrollMeter

**Claim** (spec §55, decided in Phase 7 — ADR-032): *Zjisti, kolik toho skutečně nascrolluješ.* / *See how far you
scroll.* Secondary: *Screen time ti řekne jak dlouho. ScrollMeter ti ukáže jak daleko.* "Metry místo minut" is
**not** used any more: since D19 the app shows time in apps as well, so the line would be false.

**Short description** (≤ 80 characters)
- cs: `Zjisti, kolik metrů denně nascrolluješ. Bez účtu, bez internetu.` (63)
- en: `See how many metres you scroll every day. No account, no internet.` (66)

**Full description — cs**

> Screen time ti řekne jak dlouho. ScrollMeter ti ukáže jak daleko.
>
> ScrollMeter měří, o kolik se při scrollování posune obsah na displeji — v sociálních sítích, v prohlížeči i jinde
> — a převádí to na metry a kilometry podle fyzické velikosti pixelu tvého displeje.
>
> • Dnes, týden, měsíc i celkem, s denním cílem a srovnáním („přibližně délka běžeckého okruhu“)
> • Historie za 7 dní, 30 dní a 12 měsíců
> • Kolik v které aplikaci, volitelně i kolik času v ní trávíš a kolik metrů za minutu
> • Kalibrace platební kartou pro přesnější měření
> • Export do CSV, smazání všech dat jedním tlačítkem
>
> Soukromí na prvním místě: ScrollMeter nemá oprávnění k internetu, nemá účet, reklamy ani analytiku. Používá
> službu Usnadnění (Accessibility API) jen k tomu, aby se dozvěděl, že a o kolik se v jiné aplikaci posunul obsah,
> a v které aplikaci. Nečte ani neukládá text, zprávy, hesla ani obsah obrazovky. Všechna data zůstávají v telefonu.
>
> Některé aplikace scrollování systému nehlásí (například YouTube) — v nich ScrollMeter nic nenaměří a řekne to.

**Full description — en**

> Screen time tells you how long. ScrollMeter shows you how far.
>
> ScrollMeter measures how far content moves on your screen while you scroll — in social apps, in the browser and
> elsewhere — and turns it into metres and kilometres using the physical pixel size of your display.
>
> • Today, this week, this month and all time, with a daily goal and a comparison ("about one lap of a running track")
> • History for 7 days, 30 days and 12 months
> • How far in which app, and optionally how much time you spend there and how many metres a minute
> • Calibration with a payment card for more accurate measuring
> • CSV export, delete all data with one button
>
> Privacy first: ScrollMeter has no internet permission, no account, no ads and no analytics. It uses the
> Accessibility service (Accessibility API) only to learn that — and how far — content moved in another app, and
> in which app. It does not read or store text, messages, passwords or screen content. All data stays on the phone.
>
> Some apps do not report scrolling to the system (YouTube, for example) — ScrollMeter measures nothing there and
> says so.

**Category:** Lifestyle (alternative: Health & Fitness) — Honza's choice.
**Screenshots:** Přehled with a goal ring, Historie 30 dní, Aplikace with time, app detail, onboarding disclosure,
Soukromí. Czech and English sets (the app has both, ADR-032).

## Accessibility API declaration (Play Console → App content → Sensitive permissions and APIs)

- **Is the app an accessibility tool (`isAccessibilityTool`)?** No. `isAccessibilityTool="false"` in the service
  config.
- **Core functionality that needs the AccessibilityService API:** measuring the scroll distance the user covers in
  other apps. Android offers no other API that reports how far content moved in another app; the service receives
  only `TYPE_VIEW_SCROLLED` events with `canRetrieveWindowContent="false"`.
- **Data accessed:** per scroll event the package name of the app, the view class name, the event time and the
  scroll offsets / deltas (numbers). Never text, content descriptions, the node tree, URLs or form values.
- **Use of the data:** summed on the device into distance per day and app, shown to the user, exportable by the
  user as CSV. Not transmitted — the app has no `INTERNET` permission.
- **Usage statistics (optional, separate permission):** "UsageStats dává jen čas, ne scroll delta" — Usage access
  provides only when which app was in the foreground, used for time in app; it is not used for scroll measuring
  and the app works fully without it (ADR-021).
- **Prominent disclosure:** in-app, before Android's accessibility settings open, on its own screen, with the
  button *Rozumím a chci pokračovat* (spec §30; `docs/accessibility-policy.md` → Google Play).
- **Video** (required): see the scenario below; upload unlisted, link in the declaration.

## Data safety form

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share any of the required user data types? | **No** | "Collected" means transmitted off the device; ScrollMeter has no network access, no SDK, no backup (ADR-027). On-device processing is not collection. |
| Is all data encrypted in transit? | n/a (nothing is transmitted) | |
| Can users request that data be deleted? | Yes — in the app: Nastavení › Data › Smazat všechna data | ADR-031 |
| Independent security review | No | |

Note for the reviewer: app activity (which apps were scrolled, time in app) is processed and stored **only on the
device**; the user exports it by their own action through the system share sheet / file picker.

## Permissions and why

| Permission | Why | Runtime |
|---|---|---|
| Accessibility service binding (`BIND_ACCESSIBILITY_SERVICE` on our service) | scroll events | user switches it on in Android's settings after the disclosure |
| `PACKAGE_USAGE_STATS` | optional time in app (ADR-021) | user grants Usage access in Android's settings after its own disclosure |
| `POST_NOTIFICATIONS` | optional goal / record / yesterday notifications (ADR-030) | asked when a notification switch is turned on |
| `<queries>` LAUNCHER + HOME | app names and icons; the launcher as a suggested exclusion (ADR-008, ADR-030) | — |

No `INTERNET`, no `QUERY_ALL_PACKAGES`, no foreground service, no overlay, no storage permission.

## Privacy policy

Play requires a privacy-policy URL for apps using the Accessibility API. The app itself cannot host one (no
internet); a public page is needed — **Honza decides where** (e.g. a page on honzahubka.cz). Text to publish:

> **ScrollMeter — zásady ochrany soukromí**
>
> ScrollMeter nesbírá, neodesílá ani nesdílí žádná osobní data. Aplikace nemá oprávnění k internetu.
>
> Služba Usnadnění (Accessibility API) se používá jen k detekci scrollování v jiných aplikacích: ScrollMeter z ní
> čte název aplikace a číselné údaje o posunu obsahu. Nečte ani neukládá text, zprávy, hesla, adresy stránek,
> obsah obrazovky ani vyplněné formuláře.
>
> Volitelný „Přístup k údajům o využití“ slouží jen k výpočtu času stráveného v aplikacích (součet minut na
> aplikaci a den).
>
> Všechna data zůstávají v zařízení: nezálohují se do cloudu a nepřenášejí se na nový telefon. Z telefonu odejdou
> jen exportem CSV, který spustí uživatel. Smazat je lze v aplikaci: Nastavení › Data › Smazat všechna data.
>
> Kontakt: <e-mail doplní Honza>.

## Video scenario for the Accessibility declaration (≈ 60 s, screen recording)

1. Fresh install, open ScrollMeter → onboarding step 1 and 2 (what is measured, "not the screen content").
2. Step 3: the prominent disclosure, read for two seconds, tap *Rozumím a chci pokračovat*.
3. Step 4: *Otevřít nastavení zpřístupnění* → Android's accessibility settings → ScrollMeter → switch on →
   Android's warning dialog → back in the app, the step says *Měření je zapnuté* and moves on.
4. Step 5 calibration → *Použít automatický odhad*; step 6 → *Teď ne*.
5. Scroll a feed in a browser for ~10 s, return to ScrollMeter → *Dnes* has grown, the app appears in *Top aplikace*.
6. Nastavení › Soukromí (what is and is not read), then Nastavení › Data › Smazat všechna data.

## Content rating and audience

- Content rating questionnaire: no violence, no user-generated content, no communication, no location, no
  purchases → expected rating "Everyone / PEGI 3".
- Target audience: 16+ (Honza's choice; below 13 would bring the Families policy, which the Accessibility API does
  not fit).
