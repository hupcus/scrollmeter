# handoff.md — ScrollMeter

> Živý předávací dokument. Sem se zapisují **rozhodnutí, stav a otevřené body** — po každé fázi,
> a hned při každé změně pinu nebo konstanty. Zadání = `docs/SPEC.md`, plán = `PLAN.md`.

## Identita projektu

- Lokální cesta: `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER`
- GitHub: [`hupcus/scrollmeter`](https://github.com/hupcus/scrollmeter) (privátní), založen 2026-09-23 v Phase 0
- Package: `com.scrollmeter.app` (debug: `com.scrollmeter.app.debug`)
- Cíl (Honza, 2026-09-25): **aplikace pro rodinu**, instalace přes sideload APK; případně volně ke stažení na GitHubu (otevřené rozhodnutí). **Google Play není v plánu.**

## Toolchain (závazný — ověřeno reálným buildem v Phase 0, 2026-09-23)

| Věc | Pin | Reálně použito (Phase 0) | Pozn. |
|---|---|---|---|
| JDK pro build | **21** | daemon: Homebrew OpenJDK **21.0.11** (`/opt/homebrew/Cellar/openjdk@21/21.0.11`); launcher běží na systémové Temurin 25.0.1 a to nevadí | pin `gradle/gradle-daemon-jvm.properties` `toolchainVersion=21`; `org.gradle.java.home` **není potřeba** (najde ho `org.gradle.java.installations.paths` v `~/.gradle/gradle.properties`); CI: Temurin 21 přes `setup-java` |
| Gradle wrapper | **8.14.3** | 8.14.3 (bin); wrapper prvně vygenerovaný homebrew gradlem 9.6.1, pak přegenerovaný `./gradlew wrapper` samotnou 8.14.3 | Gradle 9 rozbíjí AGP 8.x |
| AGP | **8.13.2** | 8.13.2 | AGP 9 ne (built-in Kotlin, KSP/Room ekosystém) |
| Kotlin / KSP | 2.3.21 / 2.3.11 | 2.3.21 / 2.3.11 (stdlib 2.3.21) | Compose compiler v Kotlinu |
| Compose BOM | **2026.06.01** | → compose-ui/foundation/runtime **1.11.4**, material3 **1.4.0** | 2026.08 chce compileSdk 37 + AGP 9.1 |
| Room / DataStore | 2.8.4 / 1.1.7 | Room 2.8.4 s `@Database` od Phase 3 (schéma v `app/schemas/`, R8 release ověřený na emulátoru); DataStore Preferences 1.1.7 zapojený v Phase 2 (kalibrace) a Phase 3 (nastavení) — pin beze změny, tranzitivně přibylo `com.squareup.okio:okio 3.4.0`, coroutines zůstávají 1.10.2 | |
| Navigation / Lifecycle / Activity / core-ktx | 2.9.8 / 2.9.4 / 1.13.0 / 1.18.0 | Navigation Compose **2.9.8 zapojená v Phase 5** (type-safe routes) + plugin `kotlin.plugin.serialization` (= Kotlin 2.3.21) a `kotlinx-serialization-core` **1.9.0** — v katalogu byl od Phase 0 `-json`, vyměněn za `-core` (routy JSON nepotřebují); navigation tranzitivně chce core 1.7.3 → pin ho zvedá na 1.9.0. Lifecycle zůstává 2.9.4, coroutines 1.10.2, Activity Compose 1.13.0, core-ktx 1.18.0 | R8 release s routami ověřený na emulátoru |
| coroutines | 1.10.2 | 1.10.2 | |
| Testy | JUnit 4.13.2 · Truth 1.4.5 · Robolectric 4.16 · coroutines-test 1.10.2 | vše zapojené; Robolectric (`@Config(sdk = [34])`) pro parser a in-memory Room, coroutines-test pro `ScrollPipelineTest` ve virtuálním čase (Phase 3) | |
| compileSdk / target / min | 36 / 36 / **29** | 36 / 36 / 28 (Phase 0–7) → **29** (Phase 8, 2026-09-25) | ADR-033: `scrollDeltaX/Y` jsou od API 28, ale Android 9 službě jen se scrollem nedoručí události (aktivní okno) |
| DI | žádné (ruční `AppGraph`) | `ScrollMeterApplication.graph` | ADR-003 |

## Testovací zařízení

| Zařízení | OS | Displej | Pozn. |
|---|---|---|---|
| OnePlus CPH2399 (Nord 2T), serial `W84LFE856LTWKNMN` | Android 14 / API 34 | 1080×2400, xdpi 403,411 / ydpi 401,052, densityDpi 480, 60/90 Hz | Chrome, Instagram, Facebook, Messenger, YouTube, TikTok, X, Play, Maps, Seznam Mapy; **Reddit chybí**; Instagram / TikTok / X bez účtu (přihlašovací obrazovka) |
| Emulátor `scrollmeter34` (AVD `google_apis` arm64, `pixel_6`), `emulator-5554` | Android 14 / API 34 | 1080×2400, 420 dpi | automatické kontroly; obrazy API 28–36 stažené, ostatní AVD se po měření mažou (disk) |

## Stav fází

| Fáze | Stav | Větev / PR | Poznámka |
|---|---|---|---|
| 0 Bootstrap | hotovo, mergnuto (tag `v0.0`) | `phase-0-bootstrap` / [#1](https://github.com/hupcus/scrollmeter/pull/1) | build/test/lint zelené lokálně i v CI; `installDebug` + spuštění na OnePlus OK |
| 1 Measurement POC | **GO (Honza, 2026-09-23)** | `phase-1-measurement-poc` / [#2](https://github.com/hupcus/scrollmeter/pull/2) | bez doměření Instagramu / TikToku — přijaté riziko |
| 2 Kalibrace | hotovo, mergnuto (tag `v0.2`) | `phase-2-calibration` / [#4](https://github.com/hupcus/scrollmeter/pull/4) | kalibrace kartou + MAPE s ní → „Dluh ověření“ |
| 3 Persistence | hotovo, mergnuto (tag `v0.3`) | `phase-3-persistence` / [#5](https://github.com/hupcus/scrollmeter/pull/5) | + čas v aplikaci (D19); restart telefonu a Digital Wellbeing → „Dluh ověření“ V4, V5 |
| 4 Dashboard | hotovo, mergnuto (tag `v0.4`) | `phase-4-dashboard` / [#6](https://github.com/hupcus/scrollmeter/pull/6) | ADR-028; CI zablokované billingem → brána v čistém checkoutu |
| 5 Historie + Aplikace | hotovo, mergnuto (tag `v0.5`) | `phase-5-history-apps` / [#7](https://github.com/hupcus/scrollmeter/pull/7) | ADR-029; CI zablokované billingem → brána v čistém checkoutu |
| 6 Export + Nastavení | hotovo, mergnuto (tag `v0.6`) | `phase-6-export-settings` / [#8](https://github.com/hupcus/scrollmeter/pull/8) | ADR-030/031; oznámení a export na telefonu → „Dluh ověření“ V6; CI zablokované billingem → brána v čistém checkoutu |
| 7 Onboarding + Policy | hotovo, mergnuto (tag `v0.7`) | `phase-7-onboarding-policy` / [#9](https://github.com/hupcus/scrollmeter/pull/9) | ADR-032; onboarding se sideloadem a volba jazyka na telefonu → „Dluh ověření“ V7; CI zablokované billingem → brána v čistém checkoutu |
| 8 Release | hotovo, mergnuto (tag `v0.8`; GitHub Release `v0.1.0`) | `phase-8-release` / [#10](https://github.com/hupcus/scrollmeter/pull/10) | ADR-033/034/035; baterie na telefonech a ColorOS → „Dluh ověření“ V8, V9; CI zablokované billingem → brána v čistém checkoutu |
| 9 Statistiky + limit | hotovo, mergnuto (tag `v0.9`; GitHub Release `v0.2.0`) | `phase-9-ux-limit` / PR_PLACEHOLDER | ADR-036; Honzův test na telefonech → „Dluh ověření“ V10; CI zablokované billingem → brána v čistém checkoutu |

## Phase 9 — exit report (2026-09-25)

Zadání (Honza, 25. 9.): dnes / tento týden / tento měsíc i kolik na den; klepnutím na období jeho historie s rozpadem po aplikacích (metry od největšího, minuty/hodiny na stejném řádku), bez přepínání Historie ↔ Aplikace; čas min → h → d; **není to soutěž** — cíl je limit, který nechce překročit, a barva jde zelená → oranžová → červená. Návrh `docs/plans/phase-9-ux-limit.md`, rozhodnutí ADR-036.

**Hotovo a ověřené** (větev `phase-9-ux-limit`):
- **Přehled** bez spodní lišty: karta *Dnes* v barvě limitu se slovy („Zbývá 80 m z limitu 500 m“ / „Překročeno o 120 m (limit 500 m)“) a časem v aplikacích; *Tento týden* / *Tento měsíc* s průměrem na den a barevnou tečkou; *Nejvíc dnes* 3 aplikace + *Všechny aplikace*; Nastavení pod ozubeným kolem.
- **Statistiky** (nahrazují Historii i Aplikace): Den / Týden / Měsíc, ‹ › (ne do budoucna, ne před první naměřený den), graf po dnech s každým sloupcem v barvě jeho dne a čárkovanou čarou limitu, klepnutí na sloupec otevře ten den (Zpět vrátí týden), aplikace podle metrů s časem v závorce „888 m (4 h 2 min)“, jen-časové aplikace „— (12 h 23 min)“ na konci.
- **Detail aplikace** za období, ze kterého se otevřel („Včera · čt 24. 9.“), + podíl na vzdálenosti; grafy dál 30 dní.
- **Limit**: 100 m – 5 km, vlastní, nebo *Bez limitu* (bez barev); oranžová od 70 % (`LIMIT_WARN_RATIO`); týden a měsíc podle průměru na den od prvního naměřeného dne. Oznámení „Denní limit překročen“ (1× denně) + shrnutí včerejška; rekord zrušený. Celkem naměřeno se přesunulo do *O aplikaci*.
- Čas: „< 1 min“, „45 min“, „1 h 1 min“, „1 d 1 h 1 min“. Anglické datumy anglicky („21–27 Sep“), české česky („21.–27. 9.“).
- `versionCode 2`, `versionName 0.2.0`.
- **Emulátor (API 34):** instalace 0.1.0 debug s cílem 1 km → nasazená data (32 dní, 6 aplikací) → aktualizace na 0.2.0: data i cíl drží („limit 1.00 km“ v angličtině emulátoru), bez pádu; limit 500 m / 250 m / Bez limitu (oranžová / červená / neutrální); týden, den, měsíc, ‹ › i zákaz před srpnem, sloupec → den → aplikace → Zpět → Zpět → Přehled; cs + en, světlý + tmavý, šířka 360 dp. 19 screenshotů prošel subagent: žádná vada (jediná poznámka: detail aplikace pro dnešek bez času v aplikaci ukazuje „—“ — záměr, čas za dnešek ještě nebyl).
- Brány: 278 unit testů, lint (jen známé varování v debug `TestListScreen`), debug + release build, manifest policy, Python testy.

**Review** (`/topshit` nad celým diffem) — opraveno ve větvi: anglické datumy v českém tvaru; „Dnes zatím žádné scrollování“ a věta o limitu problikly před načtením; přepínač oznámení o limitu bez nastaveného limitu tiše nic nedělal (teď to říká).

**Stage 2 — bezpečnost** (diff čtený bezpečnostní optikou): žádné nové oprávnění ani `INTERNET` (manifest policy zelená), služba beze změny (jen `typeViewScrolled`, žádný text z eventů); nové argumenty tras `kind` / `anchor` se po příchodu validují (`Period.parse`, u detailu i `PackageNames.isValid`) a navíc je čistí `NavIntents`; DAO dotazy dál parametrizované (`daysBefore` smazaný); limit z DataStore přijme jen 0 nebo rozsah 10 m – 100 km, jinak výchozí. Nález žádný.

**Neověřeno / přijato:** jednotlivé sloupce grafu nejsou pro TalkBack samostatné (dny jsou dosažitelné přes Den + ‹ ›); přesně na limitu karta napíše „Překročeno o 0 m“ (vzácné).

## Phase 8 — exit report (2026-09-25)

**Hotovo a ověřené** (větev `phase-8-release`, PR #10):
- **Podepisování (ADR-034):**
  - přes env proměnné podle DETECT;
  - bez nich zůstane release nepodepsaný (CI, brány);
  - build selže při špatné cestě, chybějícím hesle nebo zapnuté configuration cache — ta by hesla uložila čitelně do `.gradle/`.
- **Upload klíč:**
  - `~/.android-keystores/scrollmeter-upload.jks` (PKCS12, RSA 4096, 30 let, alias `scrollmeter-upload`, `CN=ScrollMeter, O=Honza Hubka, C=CZ`, práva 600);
  - certifikát SHA-256 `046f8cd0b07323f70712e11253cad7fb8203f268c8d81053783d9c1a669c0e88`;
  - heslo je v Klíčence (`scrollmeter-upload-keystore`, účet `scrollmeter`) a nikdy nebylo vypsané.
- **`tools/build_release.sh`:**
  - jede jen z čistého stromu, bez configuration cache;
  - APK i AAB musí nést upload certifikát, jinak skript skončí chybou;
  - pak `check_release_apk.py`, vypíše commit a SHA-256;
  - ověřeno end-to-end z čistého worktree; heslo není v logu ani v 1 278 souborech buildu.
- **Release bez logů:**
  - R8 odstraní všechna volání `android.util.Log`, i knihovní (R8 slučuje třídy knihoven s našimi, vlastnictví se z přejmenované třídy nedá poznat);
  - `tools/check_release_apk.py` hlídá deskriptor `Landroid/util/Log;` v bajtech dexu, `debuggable` a literály debug záznamu; dexdump bez kódu = chyba nástroje;
  - běží v CI i v `build_release.sh`.
- **minSdk 29 (ADR-033):**
  - Android 9 doručuje službě jen se `typeViewScrolled` události jen z „aktivního“ okna, které se pro ni skoro neaktualizuje — AOSP 9 `getRelevantEventTypes` nepřidává `TYPE_WINDOW_STATE_CHANGED`;
  - emulátor API 28: 0 událostí testovacího seznamu v 18 z 18 běhů;
  - vrátit Android 9 by chtělo `typeWindowStateChanged` = tvrdé pravidlo → otevřený bod pro Honzu.
- **Jazyk mimo aktivitu (ADR-035):**
  - toast po smazání a oznámení byly v české aplikaci anglicky (API 33, 36) → `Context.inAppLanguage()`;
  - Robolectric testy; na API 36 ověřeno „Všechna naměřená data jsou smazaná.“
- **AAB bez jazykových splitů:** Play by telefonu ve třetím jazyce po přepnutí aplikace na angličtinu nenainstaloval anglické texty (lint `AppBundleLocaleChanges`); v `BundleConfig.pb` ověřeno.
- **Emulátorová matice** (release build, detail v `docs/accuracy-testing.md`):
  - API 30 en, 33 cs, 35 en, 36 cs — onboarding, měření, export (SAF, BOM, CRLF), sdílení, smazání;
  - testovací seznam: MAPE View 0,00–0,33 %, Column 3,85–4,99 %; Compose Column pomalé tahy podměřuje o 6–11 %;
  - API 33: výzva `POST_NOTIFICATIONS`; API 35: edge-to-edge světle i tmavě;
  - API 30: PIN + restart — před odemčením neběží nic, po odemčení se služba sama připojí, data drží;
  - API 36: odvolání Usage access → detail aplikace ukáže „Čas scrollování“, bez pádu;
  - snímky zkontrolovali subagenti; jediná vada byl anglický toast (opraveno).
- **Čas v aplikaci (D19):**
  - na zamčeném zařízení nic nehrozí: aplikace není `directBootAware`, takže před prvním odemčením neběží žádná její komponenta (API 30: `RUNNING_LOCKED`, žádný proces) a `queryEvents` = null nemůže nastat;
  - kontrola událostí 1/2 na API 28 odpadla s minSdk 29.
- **Baterie** (emulátor API 34, jen relativní čísla, `docs/accuracy-testing.md`):
  - scroll 10 min: 3 463 událostí, ≈ 1,35 s CPU (0,39 ms na událost; samotné Nastavení 7 min 9 s), 0 wakelocků / alarmů / jobů, 115 flushů po ~0 ms;
  - klid 10 min: ≈ 0,4 s CPU, 0 zápisů;
  - sync času v aplikaci ≈ 12 ms na otevření.
- **Distribuce:**
  - GitHub Release `v0.1.0` v privátním repu (APK, AAB, `SHA256SUMS`) z merge commitu;
  - Play internal testing bylo připravené (`docs/play-listing.md`); od 2026-09-25 **není v plánu** — distribuce jen sideload.
- **Brány:**
  - 265 JVM testů, lint 0 chyb (6 starých `SetTextI18n` v debug seznamu), debug + release build;
  - manifest policy, kontrola release APK, 44 Python testů — vše v čistém checkoutu (CI stojí na billingu).
- **Review:**
  - `/topshit`: pin certifikátu pro APK i AAB, jen čistý strom a výpis commitu, build-tools na jednom místě;
  - Stage 2 (Fable): 3× MEDIUM opraveno (configuration cache, kontrola APK nesměla projít na prázdném výpisu, pin certifikátu); 1× LOW přijato a popsané v komentáři (heslo v prostředí Gradle daemonu — přečte ho jen týž uživatel, který si může přečíst i položku Klíčenky).

**Rozhodnutí:**
- minSdk 29 (ADR-033);
- podepisování a kontrola releasu (ADR-034);
- jazyk mimo aktivitu (ADR-035);
- 10s ticker flushů se po flushi po 50 událostech nerestartuje, takže při souvislém scrollu přijde o ~65 % víc malých zápisů, než by bylo nutné. Zápisy stojí ~0 ms a oba spouštěče jsou podle SPEC §16 → nechávám beze změny (otevřený bod).

## Phase 7 — exit report (2026-09-24)

**Hotovo a ověřené** (větev `phase-7-onboarding-policy`):
- **Onboarding** (SPEC §31, ADR-032), 5 kroků + volitelný 6.:
  1. Kolik toho denně nascrolluješ? (ikona, claim „Screen time ti řekne jak dlouho…“);
  2. Jak měření funguje (posun obsahu, ne obsah; dojezd se počítá; YouTube nehlásí);
  3. **prominent disclosure** doslova podle SPEC §30 → „Rozumím a chci pokračovat“;
  4. Zapni měření → „Otevřít nastavení zpřístupnění“; po návratu se zapnutou službou krok sám pokračuje. Na Androidu 13+ nápověda k „Omezenému nastavení“ + tlačítko „Otevřít informace o aplikaci“; upozornění, že vynucené zastavení měření vypne;
  5. kalibrace kartou (obrazovka kalibrace uvnitř onboardingu) nebo automatický odhad;
  6. Čas v aplikacích — Povolit / Teď ne.
  - Nejde přeskočit před souhlasem ani před zapnutím služby (`OnboardingFlow`); dokončí se i bez Usage access; Zpět vrací o krok.
- **Brána disclosure:** aplikace ukáže přehled až po dokončení onboardingu; banner „Zapnout měření“ a Nastavení › Služba Usnadnění vedou bez souhlasu nejdřív na disclosure (`AccessibilityGate`, `DisclosureRoute`). Záměr nastavení staví jen `MainActivity` (hlídá `PolicyGuardTest`). „Smazat data i nastavení“ vrátí onboarding.
- **Angličtina** (`values-en/`, všechny texty), čísla podle jazyka textů; **volba jazyka aplikace** v Androidu 13+ (`generateLocaleConfig`, cs + en); překlady knihoven omezené na cs + en.
- **Soukromí:** přibyl odstavec, že se nic nezálohuje do cloudu ani nepřenáší na nový telefon.
- **Dokumenty:** `docs/accessibility-policy.md` finální, nový `docs/play-listing.md` (texty listingu cs/en, odpovědi Accessibility declaration, Data safety, oprávnění, text zásad ochrany soukromí, scénář videa, hodnocení obsahu), ADR-032.
- **Brány:** 257 JVM testů (nové: pravidla onboardingu, brána, `TranslationsTest` — klíče, formátovací argumenty, plurály, disclosure a úvod Soukromí proti SPEC), lint jen 6 starých `SetTextI18n`, debug + release build, Python testy, manifest policy.
- **Emulátor (API 34):**
  - čistá instalace anglicky (systémový jazyk emulátoru) i česky (volba jazyka aplikace) — celý onboarding až na přehled;
  - Zpět mezi kroky, automatický posun po návratu z nastavení, služba už zapnutá → „Měření je zapnuté“;
  - „Smazat data i nastavení“ → onboarding znovu; vývojářská obrazovka z launch intentu onboarding obejde;
  - banner bez souhlasu → disclosure → systémové nastavení Usnadnění; „Otevřít informace o aplikaci“ → systémové Informace o aplikaci;
  - release s R8: celý onboarding bez pádu;
  - 24 snímků (cs/en, světlý/tmavý) zkontrolovali 2 subagenti.
- **Review:**
  - `/topshit`: tlačítko na „Povolit omezená nastavení“, viditelná ikona na uvítací obrazovce, dvojí klepnutí na disclosure, nadpisy pro TalkBack, anglická věta u kalibrace.
  - Stage 2 (Fable): žádný HIGH ani MEDIUM, žádné porušení tvrdých pravidel; 6× LOW opraveno:
    - souhlas v onboardingu je až za celým textem (ve scrollu), ne pevně dole — nejde klepnout před dočtením;
    - chyba čtení nastavení po souhlasu už nevrací krok 4 zpátky na disclosure;
    - strážce „nastavení Usnadnění otevírá jen MainActivity“ chytá i řetězcové akce a předání neošetřeného callbacku dál (s vlastním testem);
    - `TranslationsTest` pozná všechny formátovací specifikátory (`%1$.1f`, `%,d` …);
    - „Smazat data i nastavení“ běží v aplikačním scope a ohlásí výsledek, i když mezitím naskočí onboarding;
    - dokumenty: brána disclosure mimo onboarding otevírá nastavení rovnou po souhlasu (ADR-032), text zásad ochrany soukromí zmiňuje název třídy (neukládá se).
  - INFO opraveno: NavHost dostává uložený souhlas z MainActivity (žádné první snímky s výchozí hodnotou).

**Rozhodnutí:** claim „Metry místo minut“ vypuštěn (aplikace od D19 ukazuje i minuty) — „Zjisti, kolik toho skutečně nascrolluješ.“ / „Screen time ti řekne jak dlouho. ScrollMeter ti ukáže jak daleko.“ Krok s výjimkou z optimalizace baterie **není**: potřeboval by nové oprávnění a nic neukazuje, že proti ColorOS pomáhá (R6).

## Phase 6 — exit report (2026-09-24)

**Stage 2** (funkční + bezpečnostní review, Fable, nad `498b301..phase-6-export-settings`) — opraveno v `dcd566f`, ke každé opravě test:
- **HIGH (tvrdé pravidlo):** prahy rekordu `RECORD_MIN_PRIOR_DAYS` / `RECORD_MIN_MM` byly v `NotificationRules`, ne v `MeasurementConfig` → přesunuty; `PolicyGuardTest` teď selže, když je konstanta zmíněná v ADR logu deklarovaná jinde.
- **MEDIUM:** oznámení mohlo po „Smazat všechna data“ ohlásit smazaná data (kontrola přečetla fakta těsně před smazáním) → celé mazání běží pod `writeLock`, oznámení se posílá pod stejným zámkem a jen když se epocha dat nezměnila; úklid oznámení a kopií CSV před mazáním i po něm.
- **LOW:** chyba zápisu DataStore shodila aplikaci (8 přepínačů, kalibrace, mazání) → hláška „Nastavení se nepodařilo uložit“; mazání pokračuje dalšími kroky a řekne „Smazání se nepovedlo celé“.
- **LOW:** zrušený export (Zpět, otočení) hlásil „Export se nepovedl“ → `attemptExport` zrušení propustí; nepovedený zápis přes SAF smaže napůl zapsaný soubor.
- **LOW:** nečitelné nastavení by pustilo vyloučené aplikace do exportu → export čte výluky striktně (`stored`) a raději selže.
- **LOW:** strážce release manifestu hlídal jen debug provider → přesný allowlist autorit (`….exports`, `….androidx-startup`).
- **INFO:** doplněno do ADR-031 — známé meze: čas v aplikaci za den přesynchronizovaný během výluky se po zrušení výluky nevrátí; hodiny vrácené před čas mazání spodní hranici ignorují.
- Emulátor po opravách: smazání dat (0,9 m → 0,0 m, bez pádu), sdílení exportu otevře systémový výběr.

Neověřeno na emulátoru, jen čtením kódu a testem: smazání dat zruší i už zobrazená oznámení (`cancelAll()`).

**Emulátor pro další session:**
- start: `~/Library/Android/sdk/emulator/emulator -avd scrollmeter34 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot-save` na pozadí;
- `adb root` funguje;
- fyzický telefon bývá připojený současně → každý `adb` i `installDebug` s `ANDROID_SERIAL=emulator-5554` (Gradle `installDebug` by jinak instaloval na všechna zařízení);
- `uiautomator dump` odpojí službu → po navigaci ji vrátit `settings put secure enabled_accessibility_services …`;
- release APK podepsat debug keystorem: `zipalign` + `apksigner` z `build-tools/36.0.0`.
- Stav: nainstalovaný debug i release build, u debug buildu `POST_NOTIFICATIONS` odebrané s příznakem user-fixed (test cesty po odmítnutí), motiv podle systému.

**Hotovo a ověřené** (větev `phase-6-export-settings`):
- **Nastavení** (SPEC §44): sekce Měření / Jednotky / Zobrazení / Oznámení / Data / Soukromí / O aplikaci.
  - Denní cíl: předvolby 100 m – 5 km, vlastní 10 m – 100 km v m nebo km (čárka i tečka), mimo rozsah hláška, nic se tiše neořízne.
  - Jednotky (automaticky / metry / kilometry), motiv (podle systému / světlý / tmavý), srovnání na přehledu.
- **Vyloučené aplikace** (SPEC §27): nahoře navržené výluky zjištěné za běhu (launcher přes HOME, výchozí klávesnice, System UI), pod nimi aplikace s daty.
  - Vyloučená aplikace zmizí z Přehledu, Historie, Aplikací, oznámení i exportu.
  - Řádky zůstávají; po zrušení výluky se data vrátí (na emulátoru: Chrome 20 m zmizel a vrátil se).
- **Export CSV** (SPEC §28, D15):
  - `per_app.csv` + `daily_summary.csv` přes systémový výběr souboru (SAF, žádné oprávnění k úložišti);
  - nebo sdílení obou souborů přes neexportovaný `ExportFileProvider` (jen `cache/exports/`).
  - Formát: BOM, CRLF, desetinná tečka, prázdné = neznámé, ochrana proti vzorcům.
- **Smazat všechna data** (SPEC §45):
  - dvě tlačítka: data / data i nastavení a kalibrace;
  - smaže Room, živý nezapsaný stav, sdílené kopie i zobrazená oznámení;
  - jednou spuštěné mazání nejde přerušit (odchod z obrazovky ani otočení ho nenechá napůl).
  - Rozpracovaný flush ani sync času v aplikaci smazaná data nevrátí: zámek zápisu, epocha dat a spodní hranice pro sync.
- **Oznámení** (SPEC §26): Denní cíl dosažen / Nový rekord / Shrnutí včerejška.
  - Všechna jsou volitelná, každé nejvýš 1× denně, kontrola po každém zapsaném flushi.
  - `POST_NOTIFICATIONS` se žádá až při zapnutí přepínače. Po odmítnutí se ukáže cesta do systémových nastavení (po druhém odmítnutí se systém už neptá).
- **Soukromí a O aplikaci:** vysvětlení služby Usnadnění a času v aplikacích; verze, zařízení, metoda a měřítko.
- **Úklid:** sessions starší 90 dní se mažou při připojení služby a o půlnoci.
- **Brány:**
  - JVM testy zelené (nové: pravidla oznámení, CSV, vstup cíle, výluky ve všech čteních, mazání s rozpracovaným flushem, spodní hranice syncu, guard na providery);
  - lint 0 chyb (6 starých `SetTextI18n` z debug plochy);
  - debug + release build, Python testy, manifest policy.
- **Emulátor (API 34):**
  - výzva k oznámením: odmítnutí i povolení; trvalé odmítnutí vede na systémovou stránku;
  - všechna tři oznámení přišla, cíl se po reinstalaci neposlal podruhé;
  - export přes výběr souboru a sdílení v debug i R8 release;
  - cizí uid provider nepřečte a path traversal odmítne;
  - smazání (obě varianty) — po něm jen nová data, čas v Nastavení nezačal znovu od 33 min;
  - snímky světlý i tmavý motiv.
- **Review:**
  - `/topshit`: mazání bylo navázané na obrazovku (odchod / otočení uprostřed = napůl smazaná data) → `NonCancellable` + test; smazání uklidí i zobrazená oznámení; export říká, že vyloučené aplikace v něm nejsou.
  - Kontrola 28 snímků (2 subagenti): ikony stavového řádku při vynuceném tmavém motivu tmavé na tmavém → řídí se motivem aplikace; „Export CSV“ → „Exportovat CSV“; „sessions“ → česky; neznámý balíček se ukazoval dvakrát → jednou celý + vysvětlení.
- **Chyby nalezené až na zařízení a opravené:**
  - Sdílení padalo: `FileProvider.getUriForFile` (androidx.core 1.18) čte cesty jen z meta-data v manifestu, ne z konstruktoru podtřídy. Opraveno a hlídáno testem `PolicyGuardTest`.
  - Pole „Vlastní cíl“ nedostalo fokus.
  - Přesnost / Kalibrace / O aplikaci psaly čísla podle jazyka zařízení („0.0605“) → jazyk textů („0,0605“).
  - Texty oznámení byly v mužském rodě → neutrální.

## Phase 5 — exit report (2026-09-24)

**Hotovo a ověřené** (větev `phase-5-history-apps`):
- **Spodní navigace** Přehled / Historie / Aplikace / Nastavení (SPEC §43). Navigation Compose 2.9.8 s `@Serializable` routami (D14). Lišta je jen na čtyřech hlavních obrazovkách; přepnutí záložky si pamatuje stav; dvojí „Zpět“ neodskočí o obrazovku níž.
- **Historie** (SPEC §23):
  - 7 dní / 30 dní / 12 měsíců ve vlastním sloupcovém grafu (Canvas, D13);
  - kulaté osy s jednou jednotkou na osu („0 · 200 m · 400 m“, „0,25 km … 1 km“);
  - klepnutí na sloupec ukáže den a hodnotu;
  - pod grafem Průměr / den, Nejvyšší den, Nejnižší den, Celkem — vždy po dnech, od prvního naměřeného dne.
- **Aplikace** (SPEC §24, D19):
  - Dnes / 7 dní / 30 dní / Celkem, řazení Vzdálenost | Čas;
  - podíl na vzdálenosti a kvalita slovem, nikdy procentem přesnosti;
  - YouTube a jiné aplikace jen s časem: „—“ a „bez dat o scrollu“;
  - řádek otevře **detail**: souhrn období, tempo, „Scrolluješ X z Y v aplikaci (Z %)“, kvalita s vysvětlením, grafy vzdálenosti a času za 30 dní. Top aplikace na Přehledu vedou do detailu taky.
- **Kvalita měření** (`MeasurementQuality`, ADR-029):
  - hodnotí se z čítačů za celou dobu, ne za zvolené období — jinak by ráno všude svítilo „málo dat“;
  - HIGH jen s kalibrací kartou.
- **Nastavení** (jádro): stav služby, čas v aplikacích (bez oprávnění → vysvětlení; s oprávněním → systémová stránka, kde jde odebrat), Přesnost měření. V debug buildu jsou tady vývojářské nástroje, z Přehledu zmizely.
- **Brány:**
  - 216 JVM testů;
  - lint: jen 6 starých `SetTextI18n` z debug plochy Phase 1;
  - debug + release build, 25 Python testů, manifest policy.
- **Emulátor:**
  - 19 snímků (světlý i tmavý motiv) zkontroloval subagent;
  - R8 release: navigace, detail s argumentem, žádné debug třídy v dexu;
  - útok přes deep-link extras nic neotevřel;
  - vývojářská obrazovka přes `--es devtool` funguje.
- **Review:**
  - `/topshit`: kvalita za celou dobu, lokalizovatelný podtitulek řádku, zbytečný dotaz z detailu pryč.
  - Stage 2 (Fable): 2× MEDIUM opraveno:
    - cizí aplikace mohla přes explicitní deep-link extras Navigation otevřít detail s libovolným textem jako názvem → `NavIntents.scrubbed` + `PackageNames.isValid`;
    - tempo a podíl míchaly dny bez času v aplikaci → `AppPeriodTotals` páruje dny.
  - Stage 2: 5× LOW opraveno, 1× INFO opraveno; `-json` vyměněn za `-core`.

**Zjištění:** Launcher a systémové balíčky bez spouštěcí ikony (např. `com.google.android.apps.nexuslauncher`) se v seznamu ukazují názvem balíčku. Je to záměr (ADR-008, jediný `<queries>` na LAUNCHER). Phase 6 je stejně navrhne jako výchozí výluky (`resolveActivity(HOME)` potřebuje `<queries>` pro HOME → ADR).

## Phase 4 — exit report (2026-09-24)

**Hotovo a ověřené** (větev `phase-4-dashboard`):
- **Přehled** (SPEC §21) nahradil domovskou obrazovku z POC. Obsahuje:
  - „Dnes“ velkým číslem v prstenci k dennímu cíli („z cíle 500 m“ / „Cíl … splněn“),
  - tento týden, měsíc a celkem,
  - top aplikace dnes s názvem a ikonou (4 řádky + „Ostatní“),
  - jedno srovnání („To je přibližně délka jednoho běžeckého okruhu.“),
  - řádek „Přesnost měření“,
  - v debug buildu vývojářské nástroje.

  Všechna čísla jsou živá.
- **Služba vypnutá (§32):** červený banner „Měření je vypnuté“ + „Zapnout měření“ je první na obrazovce; štítek „Měří se“ se ukazuje jen, když služba opravdu běží.
- **Čas v aplikacích (D19):**
  - s oprávněním má řádek aplikace „V aplikaci 12 min · 9,5 m/min“,
  - bez něj „Scrollování 3 min“ a jedna zavíratelná karta → vlastní obrazovka s vysvětlením (co se čte, co se ukládá, co ne) → teprve pak nastavení Androidu,
  - po návratu s oprávněním se obrazovka sama zavře.
- **Formátování (ADR-028):**
  - `DistanceFormatter`: m/km, desetinné čárky podle jazyka textů, ne telefonu,
  - `TimeFormatter`: „< 1 min“, neznámé „—“, nikdy „0 min“,
  - `DistanceComparisonProvider`: reference ze SPEC §22, prahy téměř / přibližně / víc než,
  - `TopApps`,
  - české plurály jsou v resources.
- **Názvy a ikony aplikací** přes `PackageManager` s jediným `<queries>` pro launcher (ADR-008). `PolicyGuardTest` povoluje přesně tenhle dotaz a čistě kotlinovské balíčky `format/` a `insights/` hlídá proti importům Androidu.
- **Motiv** se řídí `Settings.theme`, výběr přijde ve Phase 6.
- **Emulátor:**
  - snímky ověřené subagentem: světlý a tmavý motiv, horní i spodní část, bez Usage access (karta, „Scrollování …“), vypnutá služba (banner) a obrazovka s vysvětlením,
  - kruh k cíli, km s čárkou a srovnání ověřené na syntetických řádcích vložených jen pro snímek a hned smazaných,
  - Chrome se měří (5 započtených, zbytek je jeho duplicitní proud podle ADR-020).

**Zjištění:** `uiautomator dump` na emulátoru odpojí službu, objeví se banner a rozhození layoutu posune tapy. Navigovat se proto musí s vypnutou službou (stejná past jako na telefonu).

## Phase 3 — exit report (2026-09-24)

**Hotovo a ověřené** (větev `phase-3-persistence`):
- **Room v1** (`scrollmeter.db`, schéma `app/schemas/…/1.json` v gitu):
  - `daily_app_aggregate` (SPEC §17 + `calibrationVersion` + `activeScrollMs`),
  - `scroll_session`,
  - `daily_app_usage`.
  Flush je jedna transakce „insert-or-add“ (`INSERT OR IGNORE` + `UPDATE x = x + :x`). UPSERT nejde, protože API 28 má SQLite 3.22 — ADR-026.
- **`ScrollPipeline`** (čistý Kotlin, jedno vlákno) zapisuje:
  - každých 10 s, když něco čeká,
  - při 50. eventu,
  - při novém dni,
  - při `onInterrupt`,
  - na konci služby.

  Zápis je `NonCancellable` a neúspěšný zápis se zopakuje při dalším flushi. Vlastní balíček se neukládá nikdy.
- „Dnes“ na domovské obrazovce je živé: uložené + ještě nezapsané.
- **Čas v aplikaci** (ADR-021, ADR-025):
  - UsageStats → `ForegroundTimeAggregator` (lokální dny, DST, tolerance 2 s při přechodu mezi aktivitami) → `UsageSyncer` (okno od posledního syncu, dny se nahrazují).
  - Sync při otevření aplikace, při připojení služby (když je poslední sync starší než 6 h) a při změně dne.
  - Oprávnění `PACKAGE_USAGE_STATS` je volitelné.
- **`SettingsRepository`** (DataStore): předává vyloučené aplikace enginu a při chybě čtení je nikdy nevynuluje.
- **Oprávnění jako allowlist** v obou strážích, včetně `uses-permission-sdk-23` a exportovaných komponent.
- **ADR-027:** nic neodchází přes zálohu ani přenos na nový telefon (`dataExtractionRules`).
- **Emulátor API 34** (tabulka v `docs/accuracy-testing.md`):
  - DB = součet z logcatu,
  - `kill -9` ztratil jen nezapsané ~4 s a služba se sama vrátila,
  - vypnutí a zapnutí obrazovky je OK,
  - dvě aplikace = dva řádky,
  - čas v aplikaci = systémový `totalTimeUsed` (13:34,4 vs 13:34; 3:19,7 vs 3:20),
  - restart emulátoru: data drží, služba se sama vrátí,
  - **release build s R8** běží (Room OK, bez vývojářských nástrojů).
- Review:
  - `/topshit`: oprava spánku v mezerách sessions a přidání živých součtů,
  - bezpečnostní review (Fable): 2× MEDIUM a 3× LOW opraveno, 2× INFO do Phase 6,
  - funkční review: 1× HIGH (výluky při chybě čtení) už opravené v `d1cccd4`; 1× MEDIUM (překrývající se flushe) → mutex a test, který bez mutexu padá; 1× LOW (Home přes půlnoc) opraveno.

**Zjištění:** `am force-stop` na API 34 **vypne službu Usnadnění** (smaže ji z `enabled_accessibility_services`). Měření zůstane vypnuté, dokud ho uživatel znovu nezapne; domovská obrazovka to řekne. Pro Phase 7 to znamená: onboarding a nápověda musí vysvětlit, že vynucené zastavení měření vypne.

**Odloženo:** restart telefonu (V4) a porovnání s Digital Wellbeing ± 5 % (V5) → „Dluh ověření“.

## Phase 2 — exit report (2026-09-24)

**Hotovo a ověřené** (větev `phase-2-calibration`, PR #4, CI zelené, 4 commity):
- Kalibrace kartou: `CalibrationRepository` (DataStore), `calibrationVersion`, `PhysicalScaleProvider.resolve` (karta přes xdpi/ydpi jen na stejném telefonu a rozlišení — ADR-024), obrazovky **Kalibrace displeje** (svislá čára — ADR-023) a **Přesnost měření** (SPEC §33), karta na domovské obrazovce.
- Služba čeká na uloženou kalibraci před první událostí a změny přebírá živě; každý výsledek nese svou verzi kalibrace (SPEC §65).
- Testovací seznam: MAE / MAPE přes sérii běhů; `TESTLIST` a hlavička debug CSV nesou kalibraci; logcat řádek `cv=`.
- `tools/accuracy.py` + `device_accuracy.py --csv-out`.
- Brány: 91 JVM testů, lint 0 chyb (6 warningů jsou staré `SetTextI18n` v debug View ploše z Phase 1), debug + release build, 19 Python testů, manifest policy. Release APK bez debug tříd, jediné oprávnění `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Na telefonu: nakreslená čára má na screenshotu přesně tolik px, kolik ukazuje (1356, 1359); engine s kartou `544 px → 34,265 mm` = 85,60 / 1359, `cv=1`; „Přeskočit – použít automatický odhad“ přepnul běžící službu bez reconnectu (`cv=2`, 25,4 / ydpi); na šířku nejde uložit; Zpět vede tam, odkud se přišlo; dvojí tap uloží jednou.
- `/topshit` (7/10) nad kumulativním diffem — tři nálezy opravené a ověřené na telefonu (čára se na OnePlusu nevešla: 1219 px místo ~1356; na šířku šla uložit nesmyslná kalibrace; Zpět a dvojí tap). Bezpečnostní čtení (Stage 2): manifest ani accessibility config se nezměnily, nové soubory jen app-private DataStore (`rw-------`), žádné logy / intenty / síť v novém kódu, uložené hodnoty se validují — bez nálezu.

**Odloženo do „Dluh ověření“** (Honza, 2026-09-24: vývoj pokračuje, ruční testy se dodělají najednou): kalibrace skutečnou kartou (mm/px vs 0,0630) a MAPE vlastního test listu s `MANUAL_CARD`. Automatická část ověření proběhla se syntetickou kalibrací (1359 px): MAE 0,00 mm / MAPE 0,00 % na View (A1000, E_reversal) — tahle čísla ale měří pixelovou cestu, ne přesnost karty.

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

## Režim práce od 2026-09-24

Honza: „pusť se do vývoje, nezastavuj, rozhoduj se sám — cílem je dodělat celou aplikaci.“ Proto:
- každá fáze se po zelených branách, CI, `/topshit` a Stage 2 **mergne bez ptaní** (squash jako `hupcus`, tag `v0.N`) a pokračuje se další,
- ruční testy, které potřebují Honzovy ruce, se nečekají — jdou do „Dluh ověření“ níže a dodělají se najednou (nejpozději ověřovací den před vydáním),
- fyzický telefon zůstává v původním stavu (Honza ho používá); automatické ověření na zařízení běží na **emulátoru** (API 34, arm64),
- tvrdá pravidla (bez `INTERNET`, `QUERY_ALL_PACKAGES`, foreground service; služba jen `typeViewScrolled`; žádný text z eventů) platí dál; zveřejnění (Google Play, veřejný release) jen s Honzou.

## Dluh ověření (ruční testy odložené na později)

Každý bod: co udělat, kdo, a co by špatný výsledek změnil. Pořadí = doporučené pořadí provedení.

| # | Test | Fáze | Kdo | Postup | Když vyjde špatně |
|---|---|---|---|---|---|
| V1 | **Instagram + TikTok (+ X, Reddit) s účtem** | 1 → 5 | Honza + session | přihlásit se, 20–30 s scrollovat v každé; session vytáhne `recording.csv` a spustí `tools/analyze_debug_csv.py` | může změnit smysl produktu (mlčí-li jako YouTube) — doporučeno co nejdřív |
| V2 | Kalibrace skutečnou kartou | 2 | Honza | Domů → Zkalibrovat displej; telefon na výšku na stole, karta na výšku vpravo od modré čáry, horní hranou na horní linku, posuvníkem a − / + spodní linku ke spodní hraně karty → Uložit kalibraci | mm/px dál než 5 % od 0,0630 → chyba v raw px kalibrační obrazovky (místní oprava) |
| V3 | MAPE vlastního test listu s `MANUAL_CARD` | 2 | session | po V2: `python3 tools/device_accuracy.py --surface view,column --markdown --csv-out <dir>` + `tools/accuracy.py` → zapsat do `docs/accuracy-testing.md` | MAPE ≥ 5 % → hledat v kalibraci / pipeline |
| V4 | Restart telefonu (ColorOS) | 3 | Honza + session | na emulátoru ověřeno (data drží, služba se sama vrátí); na OnePlusu: nascrollovat, počkat 15 s, restartovat; po startu Domů → „Dnes“ drží hodnotu a služba běží | služba se nezapne → ColorOS ji po restartu nevrací (Phase 7 nápověda) |
| V5 | Čas v aplikaci proti Digital Wellbeing | 3 | Honza + session | povolit „Přístup k údajům o využití“, otevřít aplikaci; session porovná `daily_app_usage` pro dnešek a včerejšek s Digitální rovnováhou (± 5 %) → `docs/accuracy-testing.md` | odchylka > 5 % → ADR-025 (tolerance, uzavírače) přeladit |
| V6 | Oznámení a export na telefonu | 6 | Honza + session | na emulátoru ověřeno (výzva k povolení, cíl / rekord / shrnutí přijdou jednou denně, export přes výběr souboru i sdílení); na OnePlusu: zapnout „Překročení limitu“, nastavit limit 100 m, nascrollovat → oznámení „Denní limit překročen“ přijde do ~10 s; Export CSV → Sdílet → otevřít v Tabulkách Google / Excelu (čeština a čísla správně) | oznámení nepřijde → ColorOS ho tlumí (Phase 7 nápověda); CSV se rozpadne → ADR-031 formát |
| V7 | Onboarding se sideloadem + volba jazyka | 7 | Honza + session | APK z GitHub Release [`v0.1.0`](https://github.com/hupcus/scrollmeter/releases/tag/v0.1.0) (podepsané upload klíčem) nainstalovat **přes Soubory / prohlížeč** (ne adb — ten omezení nespustí); projít onboarding: krok 4 → Android napíše „Omezené nastavení“ → „Otevřít informace o aplikaci“ → ⋮ → „Povolit omezená nastavení“ → zpět → zapnout službu; pak Nastavení Androidu › Aplikace › ScrollMeter › Jazyk → English | ColorOS nabídku ⋮ nemá nebo ji jmenuje jinak → upravit nápovědu kroku 4 (text z telefonu) |
| V8 | Baterie na telefonech (SPEC §39) | 8 | Honza + session | OnePlus + druhý telefon s release z `v0.1.0`: `dumpsys batterystats --reset`, pak 1 h aktivního scrollování, 8 h běžného používání, 24 h normálního dne; session vytáhne `dumpsys batterystats` (uid ScrollMeteru: CPU, wakelocky, spotřeba) a počet zápisů → tabulka „Battery“ v `docs/accuracy-testing.md` | podíl ScrollMeteru v Nastavení › Baterie nad ~1 % za den → nejdřív ticker flushů (otevřený bod), pak práh 50 událostí |
| V9 | ColorOS: odvolání Usage access + restart | 8 | Honza + session | na OnePlusu vzít „Přístup k údajům o využití“ → aplikace musí ukázat „Čas scrollování“ bez pádu (na emulátoru API 36 ověřeno); pak restart → služba běží, čas v aplikaci po novém povolení doběhne | pád nebo prázdné obrazovky → oprava v `UsageAccessChecker` / detailu aplikace |
| V10 | Statistiky a limit v běžném používání (v0.2.0) | 9 | Honza | pár dní používat na OnePlusu i Samsungu: sedí barvy (venku i v tmavém režimu), „Zbývá … z limitu …“, rozpad po aplikacích a čas v závorce; hlavně jestli 500 m a oranžová od 70 % odpovídají tomu, co chce hlídat | jiný limit nebo práh → jedna konstanta / předvolba (ADR-036); jiné slovo nebo pořadí → texty |

## Otevřené body

- [x] **OnePlus blokoval `settings put` přes adb** (`WRITE_SECURE_SETTINGS` denied — ColorOS „sledování oprávnění“). Vyřešeno 2026-09-23: Možnosti pro vývojáře → úplně dole **„Zakázat sledování oprávnění“** zapnuto (bez restartu), `settings put` funguje. Zároveň zapnuto „Při dobíjení nevypínat obrazovku“ (`stay_on_while_plugged_in=7`). Po resetu telefonu / aktualizaci OS zkontrolovat znovu.
- [ ] **GitHub Actions nestartují (od 2026-09-24 07:04 UTC):** „The job was not started because recent account payments have failed or your spending limit needs to be increased.“ Jde o billing účtu, ne o kód.
  - Do vyřešení se fáze mergují na **CI-ekvivalentní bráně v čistém checkoutu**: stejné příkazy jako `.github/workflows/ci.yml` + `assembleRelease`, stejné JDK 21; wrapper validace odpadá jen tam, kde se wrapper nemění. Každý PR to uvádí.
  - Honza: zvýšit spending limit, nebo přesunout job na self-hosted runner (v hh-main běží privátní joby od 24. 9. v LXC 106 — potřeboval by Android SDK).
  - Po opravě pustit CI znovu na `main` (`gh workflow run CI` nebo re-run posledního běhu).
- [x] Přenos dat na nový telefon (device-to-device): rozhodnuto ADR-027 — nic se nepřenáší (`dataExtractionRules`), data si uživatel odnese CSV exportem (Phase 6).
- [x] **„Smazat všechna data“** maže Room (`clearAllTables()`), čas v aplikacích i sdílené kopie CSV; `scroll_session` se promazává po 90 dnech (ADR-030/031, Phase 6).
- [x] Vyloučená aplikace zmizí ze všech čtení včetně historie a exportu; řádky zůstávají a po zrušení výluky se vrátí (ADR-031, Phase 6).
- [ ] **Instagram + TikTok (+ X, Reddit) doměřit s účtem** → Dluh ověření V1. GO dané bez nich (přijaté riziko). Reddit na telefonu chybí.
- [x] YouTube mlčí (ADR-013 poznámka) — od Phase 5 se v seznamu aplikací ukazuje s časem, „—“ a štítkem „bez dat o scrollu“. Sledovat dál, jestli se chování změní s novou verzí YouTube.
- [ ] Compose lazy seznamy neměřitelné (ADR-019) — přibývá jich. Hledat zdroj bez čtení obsahu až po GO (backlog).
- [x] Launcher: `<queries>` pro HOME (ADR-030) — ukazuje se názvem a je první navržená výluka; vyloučit ho rozhoduje uživatel (Phase 6).
- [ ] Kvalita měření se hodnotí s kalibrací platnou *teď* (ADR-029): den změřený před kalibrací kartou může ukázat „vysoká“. Přijato; kdyby vadilo, uložit metodu kalibrace k řádku (migrace Room).
- [ ] R6: zabíjení procesu ColorOS — Phase 3 flush ≤ 10 s, Phase 7 onboarding (výjimka z optimalizace baterie — ověřit, že pomáhá).
- [ ] GitHub Actions jsou připnuté na SHA tagů `v4`; bump na aktuální major (checkout v7, setup-java v6, gradle/actions v6, upload-artifact v7) je samostatné rozhodnutí.
- [ ] Služba nemá instrumentovaný test životního cyklu. Phase 3 to pokrývá jinak: `ScrollPipelineTest` s virtuálním časem (flush, uzavření, zrušení, neúspěšný zápis) a na emulátoru `kill -9`, reinstalace a force-stop. Reconnect bez zabití procesu je ověřený jen čtením kódu.
- [ ] Debug CSV export leží v app-specific external storage — na API 28/29 čitelný aplikacemi s `READ_EXTERNAL_STORAGE`. Jen debug, testovací telefon je API 34; přijato.
- [x] R2: Chrome bez `canRetrieveWindowContent` hlásí — ADR-013 uzavřeno (2026-09-23).
- [ ] **Čas v aplikaci (D19, ADR-021/022)** — naplánováno do Phase 3–8 podle Honzova přání „kolik času a kolik metrů v které aplikaci". Výchozí volby (Honza může změnit):
  - funkce je volitelná, nabízí se v onboardingu (krok 6), na kartě dashboardu a v Nastavení,
  - YouTube a další aplikace s časem, ale bez scrollu se ukazují s „—",
  - tempo m/min se počítá z času v aplikaci, když je oprávnění, jinak z času scrollování; vždy je u něj popsané, ze kterého,
  - vlastní aplikace a launcher se vylučují i z času,
  - žádný WorkManager.
  **Otevřené pro Honzu:**
  - ~~tagline SPEC §55 „Metry místo minut"~~ → rozhodnuto ve Phase 7 (ADR-032): vypuštěn, claim „Zjisti, kolik toho skutečně nascrolluješ.“ Honza může změnit v `docs/play-listing.md`.
  - denní pojistný sync přes WorkManager pro někoho, kdo 10+ dní neotevře aplikaci? Návrh: ne.
- [x] `calibrationVersion` po ztrátě dat začne znovu od 0 — rozhodnuto ADR-026: verze je informativní („nejnovější kalibrace, se kterou se řádek měřil“), nic se na ni nenapojuje; opakovat se může jen při ztrátě souboru kalibrace při zachované DB. Nic dalšího se nedělá.
- [ ] Repo nemá Gradle dependency verification (`gradle/verification-metadata.xml`) — supply-chain pojistka nad piny; samostatné rozhodnutí.
- [ ] Testovací telefon: USB spojení dnes 2× na chvíli vypadlo — zkontrolovat kabel / port.
- [x] Tagy: release tag `v0.1.0` = `versionName`, fázové tagy `v0.N` jsou jiné řetězce, nekolidují (rozhodnuto 2026-09-24, Phase 8).
- [x] Testovací telefon vrácen do původního stavu (2026-09-24 ráno a znovu 2026-09-24 po přerušení Phase 2, na Honzovo přání):
  - služba Usnadnění vypnutá,
  - `accessibility_enabled 0`,
  - `stay_on_while_plugged_in 0`,
  - debug build odinstalovaný (i s kalibrací a záznamem),
  - výpisy `uiautomator` smazané,
  - automatické otáčení vrácené na původní hodnotu (`accelerometer_rotation 1`, `user_rotation 0`; Phase 2 testovala landscape).
  Přepínač **Zakázat sledování oprávnění** vrací Honza ručně. Další session si telefon připraví znovu, postup je v `docs/prompts/continue-next-phase.md`.
- [x] Emulátory: arm64 `google_apis` obrazy existují pro API 28 / 30 / 33 / 35 / 36 (ověřeno `sdkmanager --list` 2026-09-24); matice změřená v Phase 8.
- [ ] Ikona a barva aplikace: pořád zástupná značka (pravítko se šipkou na modré `#1E4FD8`) — SPEC §42 nechává na implementaci; bez Play jen volitelné (Honza / grafik).
- [x] Výchozí jazyk: `values/` je čeština (D17) — pro rodinu správně; řešilo se jen kvůli zahraničnímu vydání na Play, které není v plánu (2026-09-25).
- [x] Zásady ochrany soukromí s veřejnou URL — potřeboval je jen Google Play, který není v plánu (2026-09-25). Text zůstává v `docs/play-listing.md` a stejné informace ukazuje aplikace (Soukromí).
- [x] Podpisový keystore pro release — Phase 8 (ADR-034): `~/.android-keystores/scrollmeter-upload.jks`, heslo v Klíčence, `tools/build_release.sh`.
- [ ] **Záloha upload keystoru** — Honza rozhodne kam (návrh: soubor do iCloud Drive › Klíče vedle záložního klíče Supabase, heslo zvlášť do správce hesel). Bez Google Play je klíč **jediná identita aplikace**: jeho ztráta = všichni v rodině musí aplikaci odinstalovat a přijdou o data (CSV export existuje, import ne).
- [ ] **Ověření vývojáře Androidu (Google, globálně od 2027):** na certifikovaných telefonech půjde neověřená aplikace instalovat jen přes adb nebo „pokročilý postup“ (režim vývojáře, restart, 24 h čekání). Pro rodinu stačí **bezplatný účet „limited distribution“** v Android Developer Console: až 20 autorizovaných zařízení (párování QR kódem / odkazem se souhlasem na telefonu), bez dokladu totožnosti, potřebuje Google účet s dvoufázovým ověřením a platební profil (jméno, adresa); registrovat jde jen balíček, který Android ještě neviděl → `com.scrollmeter.app` zaregistrovat (Honza) před rokem 2027. Pro cizí lidi (veřejné stažení) by platilo plné ověření vývojáře. Zdroje: developer.android.com/developer-verification/guides/limited-distribution, android-developers.googleblog.com (03/2026). V ČR zatím beze změny (2026 jen BR, ID, SG, TH).
- [ ] **Veřejné stažení z GitHubu** — rozhodnutí Honzy: (a) zveřejnit celé repo (před tím audit historie na citlivé údaje), (b) samostatné veřejné repo jen s releasy (kód zůstane privátní), (c) nic — APK posílat rodině přímo. Veřejné releasy navíc umožní aktualizace přes Obtainium (aplikace sama nemá `INTERNET`, takže se aktualizovat neumí).
- [ ] **Android 9 (API 28)** — nepodporovaný od Phase 8 (ADR-033, minSdk 29). Vrátit by ho šlo jen s `typeWindowStateChanged` ve službě = změna tvrdého pravidla → jen s Honzovým souhlasem; doporučení: nechat (podíl Androidu 9 je malý a klesá).
- [ ] Compose `Column` pomalé tahy podměřuje o 6–11 % (3 události na tah; MAPE přesto < 5 %). Hledat až s Compose lazy (ADR-019), stejný zdroj.
- [ ] Ticker flushů (10 s) se po flushi po 50 událostech nerestartuje → při souvislém scrollu ~65 % zápisů navíc (115 místo ~70 za 10 min, každý ~0 ms). Zvážit až podle V8.
- [ ] GitHub `ubuntu-latest` přejde od 2026-10-19 na Ubuntu 26 — po odblokování CI zkontrolovat, že obraz dál nese Android SDK (jinak připnout `ubuntu-24.04`).

## Log rozhodnutí (nejnovější nahoře)

### 2026-09-25 — Phase 9 Statistiky + denní limit (Opus 5.5, návrh Fable 5.1)
- Honza: období s rozpadem po aplikacích, čas na stejném řádku, „není to soutěž“ — cíl → limit se zelenou / oranžovou / červenou. Detail ADR-036.
- Rozhodnuto samostatně (výchozí doporučení): žádná spodní lišta, Nastavení pod ozubeným kolem; limit 500 m zapnutý (kdo si v 0.1.0 nastavil cíl, má ho jako limit); oranžová od 70 %; týden / měsíc podle průměru na den; srovnání „Pro představu“ zůstává; oznámení o rekordu a gamifikace (SPEC §47) pryč.

### 2026-09-25 — distribuce: rodina, ne Google Play (Honza)
- Honza: aplikace jen pro rodinu, případně volně ke stažení na GitHubu; na Google Play ne. Play podklady (`docs/play-listing.md`, AAB z `build_release.sh`) zůstávají, nic se kvůli tomu v kódu nemění.
- Dopad: záloha keystoru je kritická (bez Play App Signing), od 2027 účet „limited distribution“ (≤ 20 zařízení, zdarma), aktualizace jen ručně nebo přes Obtainium z veřejných releasů.

### 2026-09-25 — Phase 8 release (Opus 5.5, security review Fable 5.1)
- **Pin:** `minSdk` 28 → **29** (ADR-033). Ostatní piny beze změny; nový nástroj mimo Gradle: `keytool` (JDK), `apksigner` / `dexdump` / `aapt2` z nejnovějších build-tools.
- ADR-034: podepisování přes env proměnné, upload klíč v `~/.android-keystores/`, heslo v Klíčence, R8 bez `Log`, kontrola release APK, pin certifikátu v `build_release.sh`, AAB bez jazykových splitů.
- ADR-035: texty mimo aktivitu v jazyce aplikace.
- Stage 0 (`/impact`, bez grafu) proběhl na začátku session; review v plné hloubce (`/topshit` nad kumulativním diffem + Stage 2 security na Fable), protože jde o podepisování a hranici toho, co release smí obsahovat.
- Emulátory: AVD 28/30/33/35/36 po měření smazané, `scrollmeter34` zůstává; obrazy zůstávají.

### 2026-09-24 — Phase 7 onboarding + policy (Opus 5.5, review Fable 5.1)
- ADR-032: onboarding, brána disclosure, nápovědy kroku 4, jazyky, claim.
- Toolchain beze změny pinů; nově `androidResources { generateLocaleConfig = true; localeFilters += cs, en }` (AGP 8.13.2 obojí umí) a `res/resources.properties` (`unqualifiedResLocale=cs`).
- `number_locale` je přeložitelný (cs / en), `app_name` nepřeložitelný; debug texty `tools:ignore="MissingTranslation"`.
- Stage 0 (`/impact`, bez grafu): riziko HIGH — vstupní brána aplikace a hranice Play policy.

### 2026-09-24 — Phase 6 export, nastavení, oznámení (Opus 5.5)
- Oprávnění `POST_NOTIFICATIONS` (plán ho povoluje pro Phase 6), druhý `<queries>` (HOME), `ExportFileProvider` — ADR-030.
- Sémantika výluk, mazání, CSV a oznámení — ADR-031; konstanty `RECORD_MIN_PRIOR_DAYS = 3`, `RECORD_MIN_MM = 10 m`, `SESSION_RETENTION_DAYS = 90`, rozsah cíle 10 m – 100 km.
- Guard manifestu v release: přesný allowlist autorit providerů (`….exports`, `….androidx-startup`) — po Stage 2 místo „jen ne debug provider“.
- Stage 2 (Fable): 1× HIGH, 1× MEDIUM, 4× LOW opraveno (`dcd566f`), ADR-031 doplněné o bod (6).
- Piny beze změny.

### 2026-09-24 — Phase 5 historie + aplikace (Opus 5.5, review Fable 5.1)
- Toolchain:
  - Navigation Compose 2.9.8 zapojená, pin beze změny;
  - `kotlinx-serialization-json` v katalogu → `kotlinx-serialization-core` 1.9.0 (stejná verze, jiný artefakt);
  - plugin `kotlin.plugin.serialization` na verzi Kotlinu.
- ADR-029:
  - prahy kvality měření (spec §20);
  - statistiky historie;
  - řazení a podíl v seznamu aplikací;
  - párování tempa;
  - navigace.
- Konstanty `QUALITY_*` v `MeasurementConfig` (ADR-029).
- Vývojářské nástroje se přestěhovaly z Přehledu do Nastavení.

### 2026-09-24 — Phase 4 dashboard (Opus 5.5)
- Toolchain beze změny.
- ADR-028 (pravidla přehledu).
- Kalibrace se z přehledu otevírá přes „Přesnost měření“, zmizela přímá cesta z domovské obrazovky, a tím i logika „odkud byla otevřena“.
- Bottom navigation (SPEC §43) přijde ve Phase 5 spolu s obrazovkami, na které vede.
- Denní cíl 500 m je výchozí z `Settings`; volbu přidá Phase 6.

### 2026-09-24 — Phase 3 persistence + čas v aplikaci (Opus 5.5)
- Toolchain beze změny pinů. Nově zapojené:
  - Room `@Database` (2.8.4, KSP),
  - Robolectric pro testy DAO,
  - `kotlinx-coroutines-test` 1.10.2 jako `testImplementation` (verze už byla v katalogu).
- ADR-025 (čas v aplikaci — detaily), ADR-026 (persistence, flush, vlastní balíček se neukládá, `calibrationVersion` informativní), ADR-027 (žádná záloha ani D2D přenos).
- Mezery sessions a aktivního scrollování se posuzují na uptime **i** na reálném čase, protože `eventTime` v hlubokém spánku stojí (nález `/topshit`).
- Klíč posledního syncu je `usage_sync_last_ms` (čas syncu), ne `usageSyncLastEventTs` z PLAN — okno potřebuje čas syncu.
- Ověřovací prostředí:
  - emulátor `scrollmeter34`,
  - pomocné skripty jsou v scratchpadu a neverzují se,
  - postup je v `docs/accuracy-testing.md`.

### 2026-09-24 — Phase 2 mergnuta, režim „dodělat celou aplikaci“ (Opus 5.5)
- Honza rozhodl pokračovat ve vývoji bez čekání na ruční testy a svěřil rozhodování session (viz „Režim práce“). Kalibrace kartou a MAPE s ní → Dluh ověření V2, V3; Instagram/TikTok → V1.
- Emulátor (API 34, `google_apis` arm64-v8a) přidán už teď místo Phase 8 — fyzický telefon je Honzův.

### 2026-09-24 — Phase 2 kalibrace (Opus 5.5)
- PR #3 (plán „čas v aplikaci“) mergnutý jako první krok (`e7f761f`), po schválení v promptu.
- Stage 0 (`/impact`): riziko HIGH — měřítko násobí každou vzdálenost a mění se start pipeline ve službě.
- ADR-023: kalibrační čára je **svislá** (odchylka od SPEC §8 „vodorovný pruh“) — 85,60 mm se na šířku telefonu nevejde (1080 px ≈ 68 mm). Start délky = průměr os z xdpi/ydpi.
- ADR-024: kalibrace v DataStore; `calibrationVersion` počítá změny uživatele; karta platí jen pro stejného výrobce + model + rozlišení panelu (jinak xdpi/ydpi a Přesnost to řekne); `DisplayMetricsProvider` zůstává na `getRealMetrics` (PLAN zmiňoval `WindowMetrics`).
- Bez nových konstant v `MeasurementConfig`; rozsah čáry vychází z existujícího 100–1000 dpi (ADR-016). Piny beze změny, DataStore zapojen (okio 3.4.0 tranzitivně).
- Nálezy z telefonu (opraveno): čára se nevešla (max 1219 px, karta ~1356 px) → nadpis a text vedle čáry; na šířku šla uložit +222 % → čára na okraji displeje zakáže Uložit.
- Přerušeno na Honzovo přání před kalibrací kartou; telefon vrácen.

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

**Všech 9 fází (0–8) je hotových a mergnutých; GitHub Release `v0.1.0` existuje.** Další práce už není fáze z `PLAN.md`, ale:
1. **Dluh ověření V1–V9** s Honzou u telefonu (doporučené pořadí v tabulce; V1 Instagram / TikTok nejdřív — může změnit smysl produktu);
2. **Distribuce rodině** — zálohovat keystore, rozhodnout veřejný GitHub (a/b/c v otevřených bodech), před rokem 2027 účet „limited distribution“ a registrace balíčku; telefony rodiny jsou zároveň druhé zařízení pro V4 / V8 (jiní výrobci = jiné zabíjení služby a jiný text „Omezeného nastavení“);
3. otevřené body výše (CI billing, Android 9).

Nová session: `docs/prompts/continue-next-phase.md` je psaný na fáze a ty došly — místo něj řekni session, který bod výše dělat (např. „V1 s telefonem, je k dispozici“), a ať nejdřív přečte `CLAUDE.md` a tenhle soubor. Pravidla o telefonu z promptu platí dál. Historický kickoff Phase 0+1: `docs/prompts/kickoff-phase-0-1.md`.
