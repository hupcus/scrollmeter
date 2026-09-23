# ScrollMeter — plán vývoje

> Verze plánu 1.0 · 23. 9. 2026 · plán: Fable 5.1, realizace: Opus v novém okně
> Zadání: `docs/SPEC.md` (závazné). Tento plán ho převádí na fáze, hotová rozhodnutí, brány a testy.
> Živý stav (co je hotové, co se změnilo): `handoff.md`. Pravidla pro agenta: `CLAUDE.md`.

## 0. Shrnutí

ScrollMeter měří **scroll distance** — fyzický ekvivalent posunu obsahu na displeji (včetně flingu), ne dráhu prstu. Zdrojem je `AccessibilityService` poslouchající jen `TYPE_VIEW_SCROLLED`, převod px → mm dělá kalibrace (platební karta, jinak `xdpi/ydpi`), data se agregují v paměti a bufferovaně ukládají do Room. Aplikace je offline, bez účtu, bez `INTERNET`. Největší riziko není UI ani databáze, ale to, **jak různě aplikace scroll eventy hlásí** — proto se nejdřív staví měřicí jádro s debug obrazovkou a Phase 1 je brána GO/NO-GO. Teprve pak kalibrace, persistence, dashboard, historie, export, onboarding a release.

## 1. Hotová rozhodnutí (nepřehodnocovat, jen zapsat změnu do ADR)

| # | Rozhodnutí | Volba | Proč |
|---|---|---|---|
| D1 | Metrika | scroll distance = Σ hypot(dx·mmPerPxX, dy·mmPerPxY) přes eventy; fling se počítá; směr se nevyrušuje | SPEC §1, §7, §12; jediné, co jde na nerootovaném Androidu měřit poctivě |
| D2 | Package, moduly | `com.scrollmeter.app`, jeden modul `app`, balíčky podle SPEC §4 | SPEC §4; multi-module by teď jen zdržoval |
| D3 | DI | **bez Hiltu**, ruční `AppGraph` (lazy singletony) v `ScrollMeterApplication`; služba si graf vezme přes `application as ScrollMeterApplication` | Hilt 2.58+ chce AGP 9; méně pohyblivých částí; čisté konstruktory jdou testovat bez frameworku |
| D4 | Toolchain | pinovaný v `CLAUDE.md` (JDK 21, Gradle 8.14.3, AGP 8.13.2, Kotlin 2.3.21, Compose BOM 2026.06.01, Room 2.8.4) | tytéž verze buildí na tomhle Macu v projektu DETECT (08/2026); systémová Java 25 AGP 8 nepodporuje |
| D5 | Vlákna | `onAccessibilityEvent` → `ScrollSample` (jen primitiva) → `Channel(capacity 4096, DROP_OLDEST)` → jeden consumer na `Dispatchers.Default` v `serviceScope` → engine → accumulator → Room flush na `Dispatchers.IO` | SPEC §62; callback musí skončit okamžitě, ztráta při přetečení je lepší než blokování systému |
| D6 | Konstanty | vše v `measurement/MeasurementConfig.kt`: `MAX_EVENT_DISTANCE_FACTOR = 4.0`, `FALLBACK_MAX_GAP_MS = 2_000`, `DEDUP_WINDOW_MS = 5` (dedupe **vypnutý**), `FLUSH_INTERVAL_MS = 10_000`, `FLUSH_EVENT_COUNT = 50`, `SCROLL_SESSION_GAP_MS = 60_000`, `CARD_WIDTH_MM = 85.60`, `MM_PER_INCH = 25.4` | SPEC §11, §16, §18, §70 — laditelné po reálných datech na jednom místě |
| D7 | Engine je čistý Kotlin | `measurement/` bez `android.*` importů; jen `AccessibilityEventParser` sahá na `AccessibilityEvent` | unit testy běží na JVM bez Robolectricu, engine jde testovat proti syntetickým datům |
| D8 | Persistence | Room v1 s `exportSchema = true` (adresář `app/schemas/` v gitu); `daily_app_aggregate` PK (`date`, `packageName`) + sloupec `calibrationVersion`; flush dělá **přičtení** delt (`UPDATE … SET x = x + :dx`), ne přepis; `fallbackToDestructiveMigration` jen v debugu | SPEC §16, §17, §60, §65 |
| D9 | Sessions | `ScrollSessionManager` v paměti + tabulka `scroll_session` (start, end, package, distanceMm, eventCount) zapisovaná při uzavření session; **bez UI v MVP** | SPEC §18: schéma teď, UI až Phase 1.1; řádek na session je levný |
| D10 | Datum a týden | klíč `date` = `LocalDate.now(ZoneId.systemDefault())` jako ISO string; týden Po–Ne (`WeekFields.ISO`); změna pásma historii nepřepisuje | SPEC §19 |
| D11 | Debug jen v debugu | debug obrazovka, Measurement Test screen, ring buffer 100 eventů v RAM, debug CSV export → vše pod `src/debug/`; release je nemá | SPEC §34, §35, §66 |
| D12 | Názvy aplikací | `PackageManager.getApplicationLabel` v try/catch; v manifestu jen `<queries>` na launcher intent (`MAIN`/`LAUNCHER`), **ne** `QUERY_ALL_PACKAGES` | SPEC §15 + package visibility; launcher-intent dotaz je Play-kompatibilní a stačí na Instagram/YouTube |
| D13 | Grafy | vlastní Compose `Canvas` bar chart, žádná knihovna | SPEC §23 |
| D14 | Navigace | Navigation Compose 2.9 s type-safe routami (`@Serializable`), bottom bar 4 položky | SPEC §43; osvědčené v DETECT |
| D15 | Export | dva CSV přes SAF `CreateDocument("text/csv")` + share sheet přes `FileProvider` (cache dir); žádné storage permission | SPEC §28 |
| D16 | Foreground service, WorkManager | žádná foreground service; WorkManager jen pokud se objeví konkrétní údržbová práce (widget refresh, cleanup) | SPEC §40, §41 |
| D17 | Jazyk UI | `values/` česky (tykání dle příkladů ve SPEC), `values-en/` v Phase 7 | produkt je česky-first |
| D18 | Build varianty | debug `applicationIdSuffix ".debug"`; vlastní package se vylučuje za běhu přes `context.packageName` (funguje pro obě varianty); v test režimu se výluka dočasně vypne | SPEC §14, §35 |

## 2. Prostředí (ověřeno 23. 9. 2026)

- **Mac**: Android SDK `~/Library/Android/sdk` (platformy 31–36, build-tools 34/35/36, platform-tools 36, cmdline-tools 19). Bez emulátoru, bez Android Studia — vše z CLI. Homebrew `gradle` 9.6.1 slouží jen k vygenerování wrapperu. JDK 21 na `/opt/homebrew/opt/openjdk@21` (v `~/.gradle/gradle.properties` už je v `org.gradle.java.installations.paths`).
- **Telefon (USB, adb)**: OnePlus CPH2399 (Nord 2T), Android 14 / API 34, 1080×2400, xdpi 403,411 / ydpi 401,052 (fyzické, nečtvercové → reálně otestuje oddělené X/Y měřítko), densityDpi 480 (logické, +19 % proti fyzickému), 60/90 Hz. Accessibility zatím žádná zapnutá.
- **Aplikace na telefonu**: Chrome, Instagram, Facebook, Messenger, YouTube, TikTok, X, Play Store, Google Maps, Seznam Mapy. **Chybí Reddit** (je v DoD) — Honza nainstaluje.
- **GitHub**: `hupcus/scrollmeter` ještě neexistuje; založí se privátní v Phase 0 (`gh repo create hupcus/scrollmeter --private --source=. --remote=origin --push`).
- **Testování na emulátorech** (Phase 8): `sdkmanager "emulator" "system-images;android-XX;google_apis;arm64-v8a"` pro API 28/30/33/35/36 — ověřit dostupnost arm64 obrazů pro API 28.

Odvozené hodnoty pro testovací telefon (do unit testů a sanity checků):

```text
mmPerPxX = 25.4 / 403.411 = 0.062963 mm   mmPerPxY = 25.4 / 401.052 = 0.063333 mm
karta 85.60 mm ≈ 1359.5 px (osa X)       šířka panelu 1080 px ≈ 68.0 mm
diagonála = hypot(1080, 2400) = 2631.8 px → MAX_EVENT_DISTANCE = 4× = 10 527 px
1200 px svisle = 76.0 mm  (přes densityDpi 480 by vyšlo 63.5 mm → −16 %: proto densityDpi ne)
```

## 3. Architektura (detail `docs/architecture.md`)

```text
AccessibilityEvent (TYPE_VIEW_SCROLLED)
  → AccessibilityEventParser      jen primitiva: time, package, windowId, className, dx, dy, scrollX, scrollY
  → Channel<ScrollSample>         DROP_OLDEST, callback končí
  → ScrollMeasurementEngine       (čistý Kotlin)
      ScrollEventValidator        null package / vlastní package / vyloučené → EXCLUDED; hypot > 4×diag → OUTLIER_REJECTED
      ScrollFallbackTracker       klíč package+windowId+className; dx/dy = 0 → rozdíl scrollX/Y za podmínek SPEC §6 B
      PhysicalScaleProvider       mmPerPxX/Y + metoda + confidence (kalibrace > xdpi/ydpi)
      ScrollDistanceCalculator    hypot(dx·mmX, dy·mmY); H/V složky zvlášť
      → MeasurementResult(source = DIRECT_DELTA | FALLBACK_POSITION | UNMEASURABLE | OUTLIER_REJECTED | EXCLUDED)
  → ScrollAccumulator             mapa (date, package) → delty; flush 10 s / 50 ev / změna dne / onUnbind / onInterrupt
  → ScrollSessionManager          gap > 60 s = nová session
  → Room (UPSERT-add)  → Repository → Flow → Compose UI
  → DebugEventLog (jen debug)     ring buffer 100 → debug screen, CSV
```

Balíčky podle SPEC §4. Datový model SPEC §17 + `scroll_session` + `calibrationVersion`. Kompatibilita per package (SPEC §64) se **odvozuje** SUMou z denních agregátů, není to další tabulka.

## 4. Fáze

Každá fáze = větev `phase-N-<slug>` → PR → Honza merguje → tag `v0.N`. Na konci fáze: `/topshit` nad kumulativním diffem, Stage 2 (unit testy + on-device DoD + bezpečnostní čtení manifestu/konfigurace), zápis do `handoff.md`.

### Phase 0 — Bootstrap (větev `phase-0-bootstrap`)

**Cíl:** buildovatelný prázdný Compose projekt s pinovaným toolchainem, nainstalovatelný na telefon, s CI.

**Kroky**
1. `gh repo create hupcus/scrollmeter --private --source=. --remote=origin --push`.
2. `gradle wrapper --gradle-version 8.14.3` (homebrew gradle jen na tohle), `gradle/gradle-daemon-jvm.properties` s `toolchainVersion=21`.
3. `settings.gradle.kts`, kořenový `build.gradle.kts`, `gradle/libs.versions.toml` (verze z `CLAUDE.md`; vzor struktury `/Users/hupcus/Documents/VIBE-CODE/DETECT/gradle/libs.versions.toml` — převzít jen to, co ScrollMeter potřebuje: žádný Hilt, MapLibre, CameraX, Coil), `gradle.properties` (jvmargs, parallel, caching, configuration-cache, `android.useAndroidX`, `android.nonTransitiveRClass`), `local.properties` (`sdk.dir`, gitignored).
4. `app/build.gradle.kts`: namespace `com.scrollmeter.app`, compileSdk 36, minSdk 28, targetSdk 36, `compose = true`, `buildConfig = true`, debug suffix `.debug`, JVM target 17, KSP `room.schemaLocation`.
5. Manifest bez `INTERNET`; `ScrollMeterApplication` + `AppGraph`; `MainActivity` s Material 3 theme (dynamic color + fallback paleta) a placeholder obrazovkou.
6. `.github/workflows/ci.yml`: ubuntu-latest, Temurin 21, `gradle/actions/setup-gradle`, `./gradlew testDebugUnitTest lintDebug assembleDebug`.
7. Jeden triviální unit test, ať je test task živý.

**Ověření:** `assembleDebug`, `testDebugUnitTest`, `lintDebug` zelené; `installDebug` + `adb shell am start -n com.scrollmeter.app.debug/com.scrollmeter.app.MainActivity`; screenshot přes subagenta; CI na PR zelené.

**DoD:** vše výše + `handoff.md` má tabulku toolchainu s **reálně použitými** verzemi.

**Rizika:** JDK/AGP nesoulad → držet piny; když daemon-jvm criteria selže, dočasně `org.gradle.java.home` a zapsat.

**Odhad:** 1 session.

### Phase 1 — Measurement proof of concept (větev `phase-1-measurement-poc`) — **brána GO/NO-GO**

**Cíl:** odpovědět na jedinou otázku: *dostaneme na reálném telefonu z hlavních aplikací konzistentní a použitelné scroll delta?* Bez dashboardu, bez Room.

**Rozsah (soubory)**
- `res/xml/accessibility_service_config.xml`: `typeViewScrolled`, `feedbackGeneric`, `notificationTimeout=0`, `canRetrieveWindowContent=false`, `isAccessibilityTool=false` (atribut od API 30 → `tools:ignore="UnusedAttribute"`), `description` string.
- `accessibility/ScrollAccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`, `serviceScope`, channel, defensivní `onAccessibilityEvent` v try/catch s diagnostickým čítačem, `onInterrupt`, `onUnbind`), `AccessibilityEventParser` → `ScrollSample`, `AccessibilityStatusChecker` (`AccessibilityManager.getEnabledAccessibilityServiceList` + `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`).
- `measurement/`: `ScrollSample`, `MeasurementConfig`, `MeasurementResult` + `MeasurementSource`, `ScrollEventValidator`, `ScrollFallbackTracker`, `PhysicalScaleProvider` (v Phase 1 jen xdpi/ydpi), `ScrollDistanceCalculator`, `ScrollMeasurementEngine`, `DeviceGeometry` (šířka/výška/diagonála px).
- `src/debug/`: `DebugEventLog` (ring buffer 100, `StateFlow`), `DebugMeasurementScreen` (řádek = čas, package, dx, dy, mm, source, accepted/rejected; Clear / Pause / Export CSV do `getExternalFilesDir` + share), `TestListScreen` — `LazyColumn` 500 položek + horizontální `LazyRow`, ground truth = Σ|consumed| z `NestedScrollConnection`, vedle hodnota z engine pro vlastní package (test režim vypíná výluku vlastního package).
- Hlavní obrazovka (main): karta stavu **Měření je zapnuté / vypnuté** + tlačítko „Zapnout měření" (`Settings.ACTION_ACCESSIBILITY_SETTINGS`), živý součet mm od startu služby (jen RAM).
- Logcat tag `ScrollMeter`, jeden řádek na event, jen v debugu.
- `tools/analyze_debug_csv.py`: součty per package, podíl DIRECT/FALLBACK/UNMEASURABLE/OUTLIER, kandidáti na duplicitu (same package+windowId+dx+dy, Δt ≤ 5 ms), maximum hypot(dx,dy) vs 4×diag.
- Policy guard test (JVM): projde zdroje a selže na `getText(`, `.text`, `contentDescription`, `getSource(`, `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, `canRetrieveWindowContent="true"`, `QUERY_ALL_PACKAGES`, `android.permission.INTERNET`.

**Unit testy (SPEC §57–§59, povinné):** kalkulátor (dx=0/dy=1000, 1000/0, 1000/1000, záporné, nula), příklad §58 (420 dpi, dy 1200 → 72.5714 mm; 100× → 7.257 m), §59 (300/400 px × 0.06 → 30 mm, ne 42), validator (normální přijat, obří odmítnut, nulový → fallback), fallback tracker (stejný klíč/krátký gap OK; jiný window / dlouhý gap / nesmyslný skok NE), engine end-to-end na syntetické sekvenci, parser guard.

**Postup ověření na telefonu**
1. `installDebug`, zapnout službu přes adb (příkazy v `CLAUDE.md`), ověřit v `adb shell dumpsys accessibility`, že služba běží jen s `typeViewScrolled`.
2. **Test list (ground truth):** `adb shell input swipe 540 1800 540 600 800` ×5 (pomalu, bez flingu) a ×5 rychle (fling) → porovnat Σ ground truth vs Σ engine, spočítat MAPE (Test A/C/E ze SPEC §36). Horizontální `LazyRow`: `input swipe 900 1200 100 1200 800` (Test D).
3. **Chrome automaticky:** `adb shell am start -a android.intent.action.VIEW -d https://cs.wikipedia.org/wiki/Android_(opera%C4%8Dn%C3%AD_syst%C3%A9m) com.android.chrome`, swipes, `logcat -s ScrollMeter:D` → hlásí Chrome deltas? Pokud Chrome mlčí, viz R2 — testovací větev s `canRetrieveWindowContent=true` **jen jako měření**, rozhodnutí je Honzovo.
4. **Ruční matice (Honza, ~30 min):** Instagram, Facebook, YouTube, TikTok, X, Reddit, Play Store, Google Maps (seznamy/panely), Nastavení, WebView (Instagram/Facebook in-app browser) — v každé 20–30 s scrollování, včetně flingu a vodorovných carouselů. Session předem napíše přesný scénář a po každé aplikaci exportuje debug CSV.
5. `tools/analyze_debug_csv.py` nad všemi CSV → tabulka do `docs/accuracy-testing.md` (App · eventy emitovány? · scrollDelta použitelné? · fallback nutný? · outliers · duplicity · coverage · poznámky).

**DoD (SPEC §80 + brána)**
- [ ] služba jde aktivovat, přijímá `TYPE_VIEW_SCROLLED`, package + `scrollDeltaX/Y` čte
- [ ] debug obrazovka ukazuje živě package / dx / dy / mm / source / accepted-rejected, Clear / Pause / Export fungují
- [ ] xdpi/ydpi načteny, 1200 px na testovacím telefonu ≈ 76 mm
- [ ] test list: Test A (500/1000/5000 px), C (fling), D (horizontal), E (down+up = 2×, ne 0) změřeny, MAPE zapsána
- [ ] Chrome + Instagram + Reddit v matici s výsledkem; ostatní aplikace z matice alespoň jednou
- [ ] unit testy + policy guard zelené; `dumpsys accessibility` potvrzuje jen scroll eventy
- [ ] README má návod „jak POC ručně otestovat"
- [ ] `handoff.md` má **exit report**: per-app tabulka, verdikt GO/NO-GO, dedupe ano/ne (s daty), vyřazuje 4× diagonála validní flingy?, Chrome bez `canRetrieveWindowContent` ano/ne, seznam aplikací „limited/unsupported"

**Rizika:** R1–R5 (oddíl 6). **Nepokračovat do Phase 2 bez Honzova GO.**

**Odhad:** 1–2 sessions + 30 min ručního scrollování.

### Phase 2 — Kalibrace (větev `phase-2-calibration`)

**Cíl:** nejpřesnější dostupné měřítko px → mm s uloženou metodou a confidence.

**Rozsah:** `calibration/CalibrationRepository` (DataStore: method, mmPerPxX/Y, calibratedAt, manufacturer, model, xdpi/ydpi při kalibraci, `calibrationVersion`), `CalibrationMethod` (MANUAL_CARD=HIGH, DISPLAY_METRICS=MEDIUM, UNKNOWN=LOW), `DisplayMetricsProvider` (`resources.displayMetrics.xdpi/ydpi` + reálná velikost okna přes `WindowMetrics`), `PhysicalScaleProvider` čte kalibraci s fallbackem; obrazovka **Kalibrace displeje** (pruh 85,60 mm, slider, délka v raw px z `onSizeChanged` — Compose layout px = raw px, výchozí délka = 85.6/mmPerPxX z xdpi, tlačítka Uložit / Přeskočit); obrazovka **Přesnost** (metoda, 1 px = x mm, datum, Překalibrovat); Measurement Test screen dotažen (reset, přesná ground truth, MAE/MAPE přímo na obrazovce); `tools/accuracy.py` (ground truth CSV vs measured CSV → MAE, MAPE).

**Unit testy:** 85.6 mm / známá px šířka, xdpi/ydpi převod, výběr metody a confidence, kalibrace neovlivní starou hodnotu (§65 — verze).

**Ověření na telefonu:** ruční kalibrace kartou → mmPerPx by měl vyjít blízko 0,0630 (odchylka > 5 % = chyba v raw px); Test A/E znovu s manuální kalibrací.

**DoD:** manuální kalibrace uložená a použitá; automatický fallback; obrazovka Přesnost; MAPE na vlastním test listu < 5 % (cíl < 2 %) zapsaná v `docs/accuracy-testing.md`.

**Odhad:** 1 session.

### Phase 3 — Persistence (větev `phase-3-persistence`)

**Cíl:** data přežijí kill, restart, změnu aplikace i změnu dne; ztráta max. sekundy.

**Rozsah:** `data/local/ScrollDatabase` v1 (`exportSchema`, `app/schemas/`), `DailyAppAggregateEntity` (SPEC §17 + `calibrationVersion`), `ScrollSessionEntity`, DAO s `@Transaction` „insert-or-add" (přičítání delt a čítačů, `firstEventTimestamp` = min, `lastEventTimestamp` = max), `aggregation/ScrollAccumulator` (flush tick 10 s / 50 eventů / změna dne / `onUnbind` / `onInterrupt` / `onDestroy`; `Clock` injektovaný kvůli testům přechodu dne), `ScrollSessionManager`, `data/repository/ScrollRepository` (Flows: today, week, month, lifetime, per-app, per-day), DataStore settings (`dailyGoalMm`, `excludedPackages`, `unitPreference`, `onboardingCompleted`, `privacyDisclosureAccepted`, `showComparisons`, `theme`).

**Testy:** Robolectric + in-memory Room: dva eventy stejná app, dvě app, přechod dne, přičítání při opakovaném flushi, týdenní/měsíční SUM, sessionizace (gap < 60 s stejná, > 60 s nová); accumulator s fake Clock.

**Ověření na telefonu:** scroll → `adb shell am force-stop com.scrollmeter.app.debug` do 10 s → data ≤ 10 s ztráta; vypnout/zapnout obrazovku; přepnout aplikace; Honza: restart telefonu.

**DoD:** vše výše zelené, schéma JSON v gitu, po restartu aplikace i telefonu data zůstávají.

**Odhad:** 1 session.

### Phase 4 — Dashboard (větev `phase-4-dashboard`)

**Rozsah:** `ui/dashboard` — header Dnes + velká hodnota + „z cíle X" + progress ring; sekundární statistiky (týden, měsíc, celkem); top aplikace dnes (label + ikona přes `PackageManager`, `<queries>` launcher intent, fallback package name, „Ostatní"); jedna comparison card (`DistanceComparisonProvider` — čistý Kotlin, výběr reference podle rozsahu); výrazný banner **Měření je vypnuté** + „Zapnout měření", když služba neběží (nikdy netvrdit sběr bez služby); `DistanceFormatter` (mm → „428 m" / „2,84 km", české desetinné čárky, unit preference).

**Testy:** formatter (hranice m/km, zaokrouhlení), comparison provider (428 m → běžecký okruh, 4,2 km → skoro 5k, 42,3 km → maraton), repository agregace.

**DoD:** SPEC §21 kompletně; screenshot ověřený subagentem ve světlém i tmavém motivu.

**Odhad:** 1 session.

### Phase 5 — Historie + Aplikace (větev `phase-5-history-apps`)

**Rozsah:** `ui/components/BarChart` (Canvas, osy, popisky, prázdný stav); `ui/history` (7 dní / 30 dní / 12 měsíců; průměr, max, min, celkem); `ui/apps` (Dnes / 7 dní / 30 dní / Celkem, řazení, podíl %, `MeasurementQuality` HIGH/MEDIUM/LOW z čítačů + confidence kalibrace — čistý Kotlin), detail aplikace (období, graf); bottom navigation Přehled / Historie / Aplikace / Nastavení.

**Testy:** `MeasurementQuality` prahy; agregace 12 měsíců; chart data mapping.

**DoD:** SPEC §23, §24; žádné falešné procento přesnosti v UI.

**Odhad:** 1–2 sessions.

### Phase 6 — Export, nastavení, cíl, notifikace (větev `phase-6-export-settings`)

**Rozsah:** `export/CsvExporter` (`per_app.csv` + `daily_summary.csv` podle SPEC §28, SAF + share); `ui/settings` sekce Měření / Jednotky / Notifikace / Data / Soukromí / O aplikaci; **Vyloučené aplikace** (seznam viděných package s přepínači; navržené výluky detekované za běhu: launcher přes `resolveActivity(HOME)`, klávesnice přes `Settings.Secure.DEFAULT_INPUT_METHOD`, `com.android.systemui` — nic dalšího natvrdo); denní cíl (100 m … 5 km, vlastní); **Smazat všechna data** (dialog, volba ponechat/smazat kalibraci a nastavení); notifikace „Denní cíl dosažen" a „Nový rekord" (`POST_NOTIFICATIONS` runtime na 33+, kanál, max. 1× denně na typ, nikdy per event).

**Testy:** CSV formát (hlavička, escape, čísla), výluky (vyloučený package = EXCLUDED), throttle notifikací.

**DoD:** SPEC §25, §26, §28, §44, §45.

**Odhad:** 1 session.

### Phase 7 — Onboarding a policy (větev `phase-7-onboarding-policy`)

**Rozsah:** 5 kroků onboardingu (SPEC §31): úvod → jak měření funguje → **prominent disclosure** (text SPEC §30, tlačítko „Rozumím a chci pokračovat", až pak „Otevřít nastavení zpřístupnění") → detekce služby po návratu (`onResume`) → kalibrace (karta / automatický odhad); obrazovka **Soukromí** (SPEC §29); `accessibility_service_config` description; `values-en/`; `docs/accessibility-policy.md` finální; `docs/play-listing.md` (popis, Accessibility declaration odpovědi, scénář videa).

**DoD:** SPEC §29–§32 splněny; onboarding nejde přeskočit před zapnutím služby; texty česky i anglicky.

**Odhad:** 1 session.

### Phase 8 — Release hardening (větev `phase-8-release`)

**Rozsah:** release build s R8 (keep rules pro Room), podpis přes env proměnné (vzor DETECT — keystore nikdy v gitu), `versionName 0.1.0`; emulátory API 28/30/33/35/36 (`sdkmanager`), na nich build + onboarding + vlastní test list; fyzicky OnePlus (API 34) + alespoň jeden další telefon (Honza); **battery protokol** (`dumpsys batterystats --reset`, 1 h aktivního scrollování, 8 h běžného dne, 24 h; sledovat CPU, wakeupy, Room writes, počet eventů); kontrola, že release neobsahuje debug obrazovky ani logování; APK do GitHub Release (sideload) a příprava Play internal testing.

**DoD:** SPEC §54 kompletně odškrtnuto; battery čísla v `docs/accuracy-testing.md`; tag `v0.1.0`.

**Odhad:** 1–2 sessions + Honzovy testy.

### Backlog Phase 1.1 / Phase 2 (až po stabilním měření)

Kompatibilitní test v nastavení (SPEC §48), Accuracy panel (§49), sessions v UI + scroll speed (§18, §50), widget (Glance, §27), gamifikace a challenges (§47), lokální JSON backup. Databázi modelů telefonů **nedělat** (§10).

## 5. Testovací strategie

- **JVM unit testy** (`testDebugUnitTest`): celý `measurement/`, formatter, comparison, quality, CSV. Povinné případy v SPEC §57–§59 — každý má vlastní test s číselnou tolerancí.
- **Robolectric**: Room DAO a accumulator (in-memory DB, fake Clock).
- **Compose UI testy**: jen smoke (obrazovky se vykreslí, navigace) — UI se ověřuje screenshotem přes subagenta.
- **On-device automatizace přes adb** (bez Honzy): zapnutí služby, `input swipe`, `am start`, `logcat`, `force-stop`, `screencap`. Ground truth dává vlastní test list.
- **Ruční matice** (Honza): aplikace vyžadující přihlášení; session napíše přesný scénář (co, jak dlouho, co exportovat).
- **Přesnost**: MAE, MAPE podle SPEC §38 přes `tools/accuracy.py`; cíl MAPE < 5 % na vlastním listu; u cizích aplikací **žádné** tvrzení o přesnosti bez ground truth.
- **Baterie**: Phase 8 protokol.
- **Policy guard test** hlídá, že do zdrojů nepronikne čtení textu, `INTERNET`, `QUERY_ALL_PACKAGES` ani rozšířené accessibility flagy.

## 6. Rizika a co s nimi

| # | Riziko | Dopad | Řešení |
|---|---|---|---|
| R1 | Android 14+ `accessibilityDataSensitive`: aplikace může eventy před ne-tool službou skrýt (banky, možná části Facebooku/Instagramu) | některé aplikace nezměřitelné | neobcházet; označit **limited/unsupported** (SPEC §67); ukázat v kvalitě měření |
| R2 | **Chrome/WebView** hlásí scroll jen přes vlastní accessibility vrstvu (`WebContentsAccessibility`) a ta se může aktivovat jen pro služby s `canRetrieveWindowContent` / bohatšími flagy | Chrome = nejčastější cíl, bez něj produkt kulhá | Phase 1 změří obě konfigurace; pokud Chrome bez flagu mlčí, je to **produktové rozhodnutí Honzy** (soukromí + Play review vs pokrytí), ne technická samozřejmost |
| R3 | Compose `LazyColumn` a RecyclerView: delty by měly přijít z `View.onScrollChanged` → `SendViewScrolledAccessibilityEvent` (akumulace, ~100 ms), `scrollX/Y` u RecyclerView zůstává 0 (fallback nepoužitelný, ale delta ano) | pokud delta chybí, měření podměřuje | ověřit v Phase 1 na vlastním listu (Compose) a Play Store/Settings (RecyclerView) |
| R4 | Nested scroll → duplicitní eventy | nadměření | dedupe **vypnutý**, zapnout jen s důkazem z debug CSV (SPEC §70) |
| R5 | 4× diagonála vyřadí validní flingy | podměření | analýza max hypot v CSV; konstanta v `MeasurementConfig` |
| R6 | OnePlus/ColorOS optimalizace baterie zabíjí službu nebo ji nespouští po restartu | výpadky měření | ověřit po restartu; dokumentovat OEM nastavení, žádná foreground service |
| R7 | Google Play policy pro AccessibilityService | zamítnutí | `isAccessibilityTool=false`, prominent disclosure, declaration form, video — Phase 7 |
| R8 | Drift verzí AGP/JDK/Compose | rozbitý build | piny v `CLAUDE.md`; každý bump do `handoff.md` |
| R9 | Lokální týden/měsíc při změně pásma | posunuté součty | ISO datum v lokálním pásmu, historie se nepřepisuje, test |

## 7. Git a proces

- Repo `hupcus/scrollmeter` (privátní), `main` chráněný procesem: **jen PR merge**, merge je Honzovo slovo.
- Větev na fázi, konvenční commity anglicky, trailer `Co-Authored-By`. PR popis = DoD checklist fáze.
- CI na každý PR (`testDebugUnitTest lintDebug assembleDebug`).
- Konec fáze: `/topshit` jednou nad kumulativním diffem, Stage 2, zápis do `handoff.md` (stav fáze, rozhodnutí, otevřené body), tag `v0.N` po merge.
- Změna pinu, konstanty nebo přístupu k Androidu = záznam v `docs/measurement-decisions.md` (ADR).

## 8. Co dělá Honza (ručně)

1. Nainstalovat **Reddit** na testovací telefon (před maticí Phase 1).
2. Nechat telefon připojený přes USB s USB debugging během sessions.
3. Phase 1: ~30 min ručního scrollování podle scénáře od session (Instagram, Facebook, YouTube, TikTok, X, Reddit, Play, Mapy, Nastavení, in-app browser).
4. Rozhodnout **GO/NO-GO** po Phase 1 a případně R2 (Chrome vs `canRetrieveWindowContent`).
5. Mergovat PR po každé fázi („mergni").
6. Phase 2: kalibrace kartou na telefonu. Phase 3: restart telefonu. Phase 8: 24 h battery test + druhý telefon.

## 9. Odhad

| Fáze | Sessions | Ruční čas Honzy |
|---|---|---|
| 0 Bootstrap | 1 | — |
| 1 Measurement POC | 1–2 | 30 min + rozhodnutí |
| 2 Kalibrace | 1 | 5 min |
| 3 Persistence | 1 | restart telefonu |
| 4 Dashboard | 1 | — |
| 5 Historie + Aplikace | 1–2 | — |
| 6 Export + Nastavení | 1 | — |
| 7 Onboarding + Policy | 1 | čtení textů |
| 8 Release | 1–2 | 24 h test, druhý telefon |
| **Celkem** | **9–12** | |

Pořadí priorit při konfliktu (SPEC §71): správnost měření → stabilita → soukromí → spotřeba → UX → vizuál → gamifikace.
