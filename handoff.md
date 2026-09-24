# handoff.md — ScrollMeter

> Živý předávací dokument. Sem se zapisují **rozhodnutí, stav a otevřené body** — po každé fázi,
> a hned při každé změně pinu nebo konstanty. Zadání = `docs/SPEC.md`, plán = `PLAN.md`.

## Identita projektu

- Lokální cesta: `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER`
- GitHub: [`hupcus/scrollmeter`](https://github.com/hupcus/scrollmeter) (privátní), založen 2026-09-23 v Phase 0
- Package: `com.scrollmeter.app` (debug: `com.scrollmeter.app.debug`)
- Cíl: veřejná aplikace na Google Play (po Phase 8); do té doby sideload APK z GitHub Releases

## Toolchain (závazný — ověřeno reálným buildem v Phase 0, 2026-09-23)

| Věc | Pin | Reálně použito (Phase 0) | Pozn. |
|---|---|---|---|
| JDK pro build | **21** | daemon: Homebrew OpenJDK **21.0.11** (`/opt/homebrew/Cellar/openjdk@21/21.0.11`); launcher běží na systémové Temurin 25.0.1 a to nevadí | pin `gradle/gradle-daemon-jvm.properties` `toolchainVersion=21`; `org.gradle.java.home` **není potřeba** (najde ho `org.gradle.java.installations.paths` v `~/.gradle/gradle.properties`); CI: Temurin 21 přes `setup-java` |
| Gradle wrapper | **8.14.3** | 8.14.3 (bin); wrapper prvně vygenerovaný homebrew gradlem 9.6.1, pak přegenerovaný `./gradlew wrapper` samotnou 8.14.3 | Gradle 9 rozbíjí AGP 8.x |
| AGP | **8.13.2** | 8.13.2 | AGP 9 ne (built-in Kotlin, KSP/Room ekosystém) |
| Kotlin / KSP | 2.3.21 / 2.3.11 | 2.3.21 / 2.3.11 (stdlib 2.3.21) | Compose compiler v Kotlinu |
| Compose BOM | **2026.06.01** | → compose-ui/foundation/runtime **1.11.4**, material3 **1.4.0** | 2026.08 chce compileSdk 37 + AGP 9.1 |
| Room / DataStore | 2.8.4 / 1.1.7 | Room 2.8.4 (runtime + KSP compiler zapojené, zatím bez `@Database`); DataStore Preferences 1.1.7 zapojený v Phase 2 (kalibrace) — pin beze změny, tranzitivně přibylo `com.squareup.okio:okio 3.4.0`, coroutines zůstávají 1.10.2 | |
| Navigation / Lifecycle / Activity / core-ktx | 2.9.8 / 2.9.4 / 1.13.0 / 1.18.0 | Lifecycle 2.9.4, Activity Compose 1.13.0, core-ktx 1.18.0; Navigation až Phase 4 | |
| coroutines | 1.10.2 | 1.10.2 | |
| Testy | JUnit 4.13.2 · Truth 1.4.5 · Robolectric 4.16 | JUnit 4.13.2 + Truth 1.4.5; Robolectric až s Room (Phase 3) | |
| compileSdk / target / min | 36 / 36 / **28** | 36 / 36 / 28 | `scrollDeltaX/Y` od API 28 |
| DI | žádné (ruční `AppGraph`) | `ScrollMeterApplication.graph` | ADR-003 |

## Testovací zařízení

| Zařízení | OS | Displej | Pozn. |
|---|---|---|---|
| OnePlus CPH2399 (Nord 2T), serial `W84LFE856LTWKNMN` | Android 14 / API 34 | 1080×2400, xdpi 403,411 / ydpi 401,052, densityDpi 480, 60/90 Hz | Chrome, Instagram, Facebook, Messenger, YouTube, TikTok, X, Play, Maps, Seznam Mapy; **Reddit chybí**; Instagram / TikTok / X bez účtu (přihlašovací obrazovka) |

## Stav fází

| Fáze | Stav | Větev / PR | Poznámka |
|---|---|---|---|
| 0 Bootstrap | hotovo, mergnuto (tag `v0.0`) | `phase-0-bootstrap` / [#1](https://github.com/hupcus/scrollmeter/pull/1) | build/test/lint zelené lokálně i v CI; `installDebug` + spuštění na OnePlus OK |
| 1 Measurement POC | **GO (Honza, 2026-09-23)** | `phase-1-measurement-poc` / [#2](https://github.com/hupcus/scrollmeter/pull/2) | bez doměření Instagramu / TikToku — přijaté riziko |
| 2 Kalibrace | **rozpracováno** — kód hotový, CI zelené; chybí kalibrace kartou + MAPE s ní (viz „Phase 2 — stav“) | `phase-2-calibration` / [#4](https://github.com/hupcus/scrollmeter/pull/4) | pokračovat promptem `docs/prompts/continue-next-phase.md` na téže větvi |
| 3 Persistence | nezačato | — | + čas v aplikaci (D19) |
| 4 Dashboard | nezačato | — | |
| 5 Historie + Aplikace | nezačato | — | |
| 6 Export + Nastavení | nezačato | — | |
| 7 Onboarding + Policy | nezačato | — | |
| 8 Release | nezačato | — | |

## Phase 2 — stav (2026-09-24, přerušeno na Honzovo přání)

**Hotovo a ověřené** (větev `phase-2-calibration`, PR #4, CI zelené, 4 commity):
- Kalibrace kartou: `CalibrationRepository` (DataStore), `calibrationVersion`, `PhysicalScaleProvider.resolve` (karta přes xdpi/ydpi jen na stejném telefonu a rozlišení — ADR-024), obrazovky **Kalibrace displeje** (svislá čára — ADR-023) a **Přesnost měření** (SPEC §33), karta na domovské obrazovce.
- Služba čeká na uloženou kalibraci před první událostí a změny přebírá živě; každý výsledek nese svou verzi kalibrace (SPEC §65).
- Testovací seznam: MAE / MAPE přes sérii běhů; `TESTLIST` a hlavička debug CSV nesou kalibraci; logcat řádek `cv=`.
- `tools/accuracy.py` + `device_accuracy.py --csv-out`.
- Brány: 91 JVM testů, lint 0 chyb (6 warningů jsou staré `SetTextI18n` v debug View ploše z Phase 1), debug + release build, 19 Python testů, manifest policy. Release APK bez debug tříd, jediné oprávnění `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Na telefonu: nakreslená čára má na screenshotu přesně tolik px, kolik ukazuje (1356, 1359); engine s kartou `544 px → 34,265 mm` = 85,60 / 1359, `cv=1`; „Přeskočit – použít automatický odhad“ přepnul běžící službu bez reconnectu (`cv=2`, 25,4 / ydpi); na šířku nejde uložit; Zpět vede tam, odkud se přišlo; dvojí tap uloží jednou.
- `/topshit` (7/10) nad kumulativním diffem — tři nálezy opravené a ověřené na telefonu (čára se na OnePlusu nevešla: 1219 px místo ~1356; na šířku šla uložit nesmyslná kalibrace; Zpět a dvojí tap). Bezpečnostní čtení (Stage 2): manifest ani accessibility config se nezměnily, nové soubory jen app-private DataStore (`rw-------`), žádné logy / intenty / síť v novém kódu, uložené hodnoty se validují — bez nálezu.

**Zbývá (v tomhle pořadí):**
1. Připravit telefon z větve `phase-2-calibration`: `installDebug`, `stay_on_while_plugged_in 7`. Debug build byl odinstalovaný, takže kalibrace je pryč (verze 0).
2. Otevřít **Kalibrace displeje** (Domů → *Zkalibrovat displej*) — naviguj s **vypnutou** službou (uiautomator výpis ji odpojí a domovská obrazovka se přeskládá), službu zapni až na obrazovce kalibrace.
3. **Honza — kalibrace kartou** (přesný postup mu napsat znovu): telefon na výšku na stole; karta velikosti platební karty (i občanka, řidičák) na výšku hned vpravo od svislé modré čáry, horní hranou na horní vodorovnou linku; posuvníkem spodní linku ke spodní hraně karty, doladit − / +; kartu sundat → *Uložit kalibraci*; pak ~3 min na telefon nesahat.
4. Zkontrolovat mm/px proti 0,0630 (xdpi) — odchylka > 5 % = chyba v raw px. Zapsat do `docs/accuracy-testing.md` (tabulka Devices, sloupec „Manual card mm/px“).
5. `python3 tools/device_accuracy.py --surface view,column --markdown --csv-out <scratch>` + `tools/accuracy.py` → MAPE < 5 % (cíl < 2 %) s `MANUAL_CARD` zapsat do „Accuracy runs“.
6. Odškrtnout DoD v popisu PR #4, dopsat tenhle oddíl a log, stav fáze → „hotovo, čeká na merge“, push, CI zelené, napsat „mergni?“ a čekat. `/topshit` a bezpečnostní review neopakovat, pokud se nezměnil kód.

## Phase 1 — exit report (2026-09-23)

**Otázka brány:** dostaneme na reálném telefonu z hlavních aplikací konzistentní a použitelné scroll delta?

**Verdikt: podmíněné GO.** Tam, kde aplikace scroll hlásí, je měření přesné:
- klasické View: MAPE 0,05 %,
- Compose Column: 3,1 %, vždy trochu méně,
- Chrome: 0,00 % proti vlastnímu proudu polohy.

Funguje Facebook včetně in-app prohlížeče, Chrome, Edge, Google Mapy a Nastavení. **Ale:**
- YouTube mlčí.
- Aplikace na Compose `LazyColumn` (Obchod Play, H&M) jsou neměřitelné.
- Instagram, TikTok, X a Reddit se nepodařilo ověřit (bez účtu / nenainstalováno).

Instagram a TikTok jsou pro scroll-metr nejdůležitější aplikace.

**Doporučení bylo:** před Phase 2 doměřit Instagram a TikTok s účtem.

**Rozhodnutí (Honza, 2026-09-23): GO bez doměření** — „věřím, že to bude fungovat“. Přijaté riziko: kdyby Instagram nebo TikTok mlčely jako YouTube, ukáže se to až při prvním reálném používání. Ověřit je při první příležitosti s účtem, nejpozději v Phase 5 (seznam aplikací).

| Aplikace | Výsledek | Stav |
|---|---|---|
| Chrome 153 | přímé delty z `FrameLayout`; WebView posílá tentýž posun podruhé jako polohu → potlačeno (ADR-020) | ✅ podporováno |
| Edge | stejné jako Chrome; potlačeno 0,61 m duplicit | ✅ podporováno |
| Facebook (feed, příběhy, in-app prohlížeč) | 318 z 332 událostí přímé delty, 5,7 m | ✅ podporováno |
| Google Mapy (seznam výsledků) | 58/58 přímé | ✅ podporováno |
| Nastavení | 45/48 přímé | ✅ podporováno |
| Launcher OnePlus | 17 fallback (ListView, přesné polohy) | ✅ — počítat do součtu? produktové rozhodnutí |
| Obchod Play, H&M | 100 % „bez dat" — Compose lazy odhad (ADR-019) | ⚠️ limited |
| YouTube | 0 událostí; posílá jen s bohatším a11y klientem (uiautomator), `canRetrieveWindowContent=true` nepomáhá | ❌ unsupported |
| Gboard (psaní tahem, tah po mezerníku) | 0 událostí z klávesnice | ✅ nezkresluje |
| Instagram, TikTok, X, Reddit | přihlašovací obrazovky / nenainstalováno | ⏳ neověřeno |

- **Dedupe (§70) — ne.** 0 kandidátů (stejné package + window + dx + dy do 5 ms) ve všech datech (600+ událostí z ruční matice, automatické běhy). ADR-006 zůstává OFF. Jiný druh duplicity, dva proudy Chromia, řeší ADR-020.
- **Vyřazuje 4× diagonála validní flingy? — ne.** 0 outlierů. Největší událost 3194 px (Facebook) = 0,30 limitu 10 527 px, Chrome ~1117 px.
- **Chrome bez `canRetrieveWindowContent` — ano.** ADR-013 uzavřeno: zůstává `false`. Throwaway měření s `true` (větev `throwaway/can-retrieve-window-content`, jen lokálně, `3b0e9af`) nepomohlo ani YouTube.
- **Limited / unsupported:**
  - YouTube,
  - každá aplikace, jejíž hlavní seznam je Compose `LazyColumn`/`LazyRow` (Obchod Play, H&M, časem přibude další),
  - obsah, který aplikace nehlásí jako scroll (posouvání mapy).
- **Nové riziko R6 — systém zabíjí službu.**
  - Paměťová ochrana ColorOS zabila proces ScrollMeteru 3× za 45 s, když běžely těžké aplikace.
  - Služba se sama vrací za 4–10 s.
  - Dopad na Phase 3: flush po ≤ 10 s znamená ztrátu max. ~10 s scrollování na jedno zabití.
  - Dopad na Phase 7: onboarding by měl navést na výjimku z optimalizace baterie. Neověřeno, že proti paměťové ochraně pomáhá.
- **Odchylky od PLAN:** testovací seznam má 300 položek a tři plochy (View / Column / Lazy) místo 500 položek `LazyColumn`. Lazy se ukázal jako neměřitelný, proto přibyly plochy s přesnou ground truth. Test B (20 ručních tahů) nahradilo 20 automatických tahů.
- **Detail:** `docs/accuracy-testing.md` (matice, přesnost, YouTube, zabíjení služby), ADR-013 až ADR-020 v `docs/measurement-decisions.md`.

## Otevřené body

- [x] **OnePlus blokoval `settings put` přes adb** (`WRITE_SECURE_SETTINGS` denied — ColorOS „sledování oprávnění“). Vyřešeno 2026-09-23: Možnosti pro vývojáře → úplně dole **„Zakázat sledování oprávnění“** zapnuto (bez restartu), `settings put` funguje. Zároveň zapnuto „Při dobíjení nevypínat obrazovku“ (`stay_on_while_plugged_in=7`). Po resetu telefonu / aktualizaci OS zkontrolovat znovu.
- [ ] Přenos dat na nový telefon (device-to-device): `allowBackup="false"` vypíná cloud backup, D2D transfer zůstává na výchozím chování platformy — rozhodnout v Phase 6 (export/nastavení).
- [ ] **Instagram + TikTok (+ X, Reddit) doměřit s účtem** — GO dané bez nich (přijaté riziko); ověřit při první příležitosti, nejpozději v Phase 5. Reddit na telefonu chybí.
- [ ] YouTube mlčí (ADR-013 poznámka). Sledovat, jestli se chování změní s novou verzí YouTube; jinak „unsupported" v Phase 5 seznamu aplikací.
- [ ] Compose lazy seznamy neměřitelné (ADR-019) — přibývá jich. Hledat zdroj bez čtení obsahu až po GO (backlog).
- [ ] Launcher počítat do součtu, nebo vyloučit? (Phase 5/6, výchozí výluky)
- [ ] R6: zabíjení procesu ColorOS — Phase 3 flush ≤ 10 s, Phase 7 onboarding (výjimka z optimalizace baterie — ověřit, že pomáhá).
- [ ] GitHub Actions jsou připnuté na SHA tagů `v4`; bump na aktuální major (checkout v7, setup-java v6, gradle/actions v6, upload-artifact v7) je samostatné rozhodnutí.
- [ ] Služba nemá instrumentovaný test životního cyklu (reconnect, `onUnbind`) — zbytkové riziko do Phase 3, kdy začne zapisovat do Room.
- [ ] Debug CSV export leží v app-specific external storage — na API 28/29 čitelný aplikacemi s `READ_EXTERNAL_STORAGE`. Jen debug, testovací telefon je API 34; přijato.
- [x] R2: Chrome bez `canRetrieveWindowContent` hlásí — ADR-013 uzavřeno (2026-09-23).
- [ ] **Čas v aplikaci (D19, ADR-021/022)** — naplánováno do Phase 3–8 podle Honzova přání „kolik času a kolik metrů v které aplikaci". Výchozí volby (Honza může změnit):
  - funkce je volitelná, nabízí se v onboardingu (krok 6), na kartě dashboardu a v Nastavení,
  - YouTube a další aplikace s časem, ale bez scrollu se ukazují s „—",
  - tempo m/min se počítá z času v aplikaci, když je oprávnění, jinak z času scrollování; vždy je u něj popsané, ze kterého,
  - vlastní aplikace a launcher se vylučují i z času,
  - žádný WorkManager.
  **Otevřené pro Honzu:**
  - tagline SPEC §55 „Metry místo minut" → nechat, nebo „metry i minuty"? Rozhodne se ve Phase 7.
  - denní pojistný sync přes WorkManager pro někoho, kdo 10+ dní neotevře aplikaci? Návrh: ne.
- [ ] **`calibrationVersion` po ztrátě dat začne znovu od 0** (poškozený soubor DataStore → prázdný, v Phase 6 „Smazat všechna data“). Phase 3 musí rozhodnout, jestli má být verze unikátní napříč historií agregací (např. držet maximum i v Room).
- [ ] Repo nemá Gradle dependency verification (`gradle/verification-metadata.xml`) — supply-chain pojistka nad piny; samostatné rozhodnutí.
- [ ] Testovací telefon: USB spojení dnes 2× na chvíli vypadlo — zkontrolovat kabel / port.
- [ ] Tagy: fáze se tagují `v0.N` (v0.0, v0.1 …), ale Phase 8 plánuje release tag `v0.1.0` — kolize názvů, přejmenovat release tag (např. `v1.0.0-rc1`) nejpozději ve Phase 8.
- [x] Testovací telefon vrácen do původního stavu (2026-09-24 ráno a znovu 2026-09-24 po přerušení Phase 2, na Honzovo přání):
  - služba Usnadnění vypnutá,
  - `accessibility_enabled 0`,
  - `stay_on_while_plugged_in 0`,
  - debug build odinstalovaný (i s kalibrací a záznamem),
  - výpisy `uiautomator` smazané,
  - automatické otáčení vrácené na původní hodnotu (`accelerometer_rotation 1`, `user_rotation 0`; Phase 2 testovala landscape).
  Přepínač **Zakázat sledování oprávnění** vrací Honza ručně. Další session si telefon připraví znovu, postup je v `docs/prompts/continue-next-phase.md`.
- [ ] Emulátory: ověřit, že pro API 28 existuje arm64 systémový obraz (`sdkmanager --list | grep android-28`).
- [ ] Ikona a barva aplikace — až Phase 4 (SPEC §42 nechává na implementaci).
- [ ] Podpisový keystore pro release — Phase 8, přes env proměnné, nikdy v gitu.

## Log rozhodnutí (nejnovější nahoře)

### 2026-09-24 — Phase 2 kalibrace, rozpracováno (Opus 5.5)
- PR #3 (plán „čas v aplikaci“) mergnutý jako první krok (`e7f761f`), po schválení v promptu.
- Stage 0 (`/impact`): riziko HIGH — měřítko násobí každou vzdálenost a mění se start pipeline ve službě.
- ADR-023: kalibrační čára je **svislá** (odchylka od SPEC §8 „vodorovný pruh“) — 85,60 mm se na šířku telefonu nevejde (1080 px ≈ 68 mm). Start délky = průměr os z xdpi/ydpi.
- ADR-024: kalibrace v DataStore; `calibrationVersion` počítá změny uživatele; karta platí jen pro stejného výrobce + model + rozlišení panelu (jinak xdpi/ydpi a Přesnost to řekne); `DisplayMetricsProvider` zůstává na `getRealMetrics` (PLAN zmiňoval `WindowMetrics`).
- Bez nových konstant v `MeasurementConfig`; rozsah čáry vychází z existujícího 100–1000 dpi (ADR-016). Piny beze změny, DataStore zapojen (okio 3.4.0 tranzitivně).
- Nálezy z telefonu (opraveno): čára se nevešla (max 1219 px, karta ~1356 px) → nadpis a text vedle čáry; na šířku šla uložit +222 % → čára na okraji displeje zakáže Uložit.
- Přerušeno na Honzovo přání před kalibrací kartou; telefon vrácen. Co zbývá: oddíl „Phase 2 — stav“.

### 2026-09-23 — plán: čas v aplikaci (Opus 5.5, návrh Fable 5.1)
- Honza chce kompletní dashboard: kolik času a kolik metrů v které aplikaci. Prověřeno z primárních zdrojů (AOSP, Google Play policy):
  - `UsageStatsManager` + volitelné `PACKAGE_USAGE_STATS`; Google Play pro ně nemá deklarační formulář, platí prominent disclosure,
  - systém drží eventy ~10 dní, proto vlastní denní snapshot,
  - `TYPE_WINDOW_STATE_CHANGED` zamítnut, protože by rozšířil službu Usnadnění.
- Zapsáno: PLAN D19 + rozsah a DoD ve Phase 3–8, riziko R10, ADR-021 (čas v aplikaci), ADR-022 (čas scrollování, SPEC §50), `docs/accessibility-policy.md`.
- Merge Phase 1 (PR #2 → `6f02b25`, tag `v0.1`) po Honzově „go".

### 2026-09-23 — Phase 1 measurement POC (Opus 5.5)
- Engine podle SPEC §6: přímé delty → fallback z polohy → bez dat. Nová rozhodnutí z měření na telefonu:
  - ADR-014: (-1,-1) = aplikace deltu neposlala.
  - ADR-015: klíč fallbacku nese osy, protože `windowId` je bez `canRetrieveWindowContent` vždy -1.
  - ADR-019: Compose lazy odhad se neměří.
  - ADR-020: přímé delty potlačí fallback jiné třídy téže aplikace — Chrome by se jinak počítal 2×.
- Nová konstanta `COMPOSE_LAZY_MAX_SCROLL_MARGIN_PX = 100` (ADR-019) a `DIRECT_SUPERSEDES_FALLBACK_MS = 5 000` (ADR-020). Nový zdroj `SUPERSEDED_BY_DIRECT`. Piny beze změny.
- Debug log se od ADR-017 dodatku zapisuje průběžně do `files/debug/recording.csv` — první ruční kolo zmizelo se zabitým procesem.
- Review (/topshit, 7/10) — opraveno:
  - debug řádky ukazovaly surové -1/-1,
  - chyběl guard na opakovaný connect,
  - lazy detekce mohla vypnout Column.
- Bezpečnostní review (Fable): žádný HIGH. Opraveno:
  - díry v PolicyGuardTest (`//` v řetězci, `.java`, `setServiceInfo`),
  - nový CI krok nad merged manifesty (`tools/check_manifest_policy.py`),
  - CSV injection přes `className`,
  - Actions připnuté na SHA.
- Release APK ověřen `apkanalyzer`em: žádné devtools třídy, žádné logy událostí, jediné oprávnění je `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

### 2026-09-23 — Phase 0 bootstrap (Opus 5.5)
- Repo `hupcus/scrollmeter` založené (privátní), `main` pushnutý, práce na `phase-0-bootstrap`.
- Toolchain přesně podle pinů, žádný bump (tabulka výše = reálně resolvnuté verze).
- Room runtime + KSP compiler zapojené už teď, i když databáze přijde až v Phase 3 — ověřuje, že pinovaná sada Kotlin 2.3.21 / KSP 2.3.11 / Room 2.8.4 spolu buildí. `room.schemaLocation` = `app/schemas`.
- `android:allowBackup="false"`: lokální aplikace nemá posílat data do Google cloud backupu (SPEC §29 duch „žádný cloud"). Lint `DataExtractionRules` vypnutý v `app/lint.xml` — `allowBackup=false` to na všech podporovaných API pokrývá.
- `app/lint.xml` vypíná `GradleDependency` / `NewerVersionAvailable` / `AndroidGradlePluginVersion`: verze jsou pinované záměrně, bump = záznam tady. Lint report je jinak čistý (0 issues), `abortOnError` zůstává výchozí (true).
- Merged manifest: žádná `uses-permission` kromě `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature-level, přidává ji androidx.core). Exportované: `MainActivity` (launcher), `ProfileInstallReceiver` (chráněný `android.permission.DUMP`), v debugu navíc `PreviewActivity` z ui-tooling.
- CI: `.github/workflows/ci.yml` — Temurin 21, wrapper-validation, setup-gradle, `./gradlew testDebugUnitTest lintDebug assembleDebug` (~4 min). Bez `android-actions/setup-android@v3` — padal na odstraněném balíčku `tools`; runner má SDK předinstalované.
- On-device: `installDebug` na CPH2399 OK, `am start -W` → `Status: ok`, TotalTime 1121 ms, žádný crash v logcatu; screenshot ověřený subagentem (správný text s diakritikou, obsah pod status barem, tmavé téma přes dynamic color).

### 2026-09-23 — založení projektu (Fable 5.1)
- Projekt založen ze zadání `docs/SPEC.md` v1.0. Plán `PLAN.md`, hotová rozhodnutí D1–D18 (PLAN §1), ADR-001…ADR-012 v `docs/measurement-decisions.md`.
- Toolchain převzat z DETECT (jediný Android projekt na tomhle Macu, který v 08/2026 buildil): JDK 21, Gradle 8.14.3, AGP 8.13.2, Compose BOM 2026.06.01. Bez Hiltu.
- Ověřeno prostředí: SDK 36, JDK 21 přítomné, telefon OnePlus CPH2399 připojený přes adb, žádná accessibility služba zapnutá, žádný emulátor, žádné Android Studio.
- Pořadí práce je záměrně obrácené proti „nejdřív hezké UI": Phase 1 = měřicí jádro + debug obrazovka + brána GO/NO-GO.

## Jak navázat

Nová session: vlož prompt z `docs/prompts/continue-next-phase.md` — přečte `CLAUDE.md`, tenhle soubor a první nedokončenou fázi z `PLAN.md` a jede fázi po fázi s „mergni?“ na konci každé. Rozpracovanou fázi (teď Phase 2) dokončí na její větvi od oddílu „Zbývá“. Historický kickoff Phase 0+1: `docs/prompts/kickoff-phase-0-1.md`.
