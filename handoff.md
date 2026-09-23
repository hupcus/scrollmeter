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
| Room / DataStore | 2.8.4 / 1.1.7 | Room 2.8.4 (runtime + KSP compiler zapojené, zatím bez `@Database`); DataStore až Phase 2/3 | |
| Navigation / Lifecycle / Activity / core-ktx | 2.9.8 / 2.9.4 / 1.13.0 / 1.18.0 | Lifecycle 2.9.4, Activity Compose 1.13.0, core-ktx 1.18.0; Navigation až Phase 4 | |
| coroutines | 1.10.2 | 1.10.2 | |
| Testy | JUnit 4.13.2 · Truth 1.4.5 · Robolectric 4.16 | JUnit 4.13.2 + Truth 1.4.5; Robolectric až s Room (Phase 3) | |
| compileSdk / target / min | 36 / 36 / **28** | 36 / 36 / 28 | `scrollDeltaX/Y` od API 28 |
| DI | žádné (ruční `AppGraph`) | `ScrollMeterApplication.graph` | ADR-003 |

## Testovací zařízení

| Zařízení | OS | Displej | Pozn. |
|---|---|---|---|
| OnePlus CPH2399 (Nord 2T), serial `W84LFE856LTWKNMN` | Android 14 / API 34 | 1080×2400, xdpi 403,411 / ydpi 401,052, densityDpi 480, 60/90 Hz | Chrome, Instagram, Facebook, Messenger, YouTube, TikTok, X, Play, Maps, Seznam Mapy; **Reddit chybí** |

## Stav fází

| Fáze | Stav | Větev / PR | Poznámka |
|---|---|---|---|
| 0 Bootstrap | PR otevřený, čeká na merge | `phase-0-bootstrap` / [#1](https://github.com/hupcus/scrollmeter/pull/1) | build/test/lint zelené lokálně i v CI; `installDebug` + spuštění na OnePlus OK |
| 1 Measurement POC | nezačato | — | brána GO/NO-GO |
| 2 Kalibrace | nezačato | — | |
| 3 Persistence | nezačato | — | |
| 4 Dashboard | nezačato | — | |
| 5 Historie + Aplikace | nezačato | — | |
| 6 Export + Nastavení | nezačato | — | |
| 7 Onboarding + Policy | nezačato | — | |
| 8 Release | nezačato | — | |

## Otevřené body

- [x] **OnePlus blokoval `settings put` přes adb** (`WRITE_SECURE_SETTINGS` denied — ColorOS „sledování oprávnění“). Vyřešeno 2026-09-23: Možnosti pro vývojáře → úplně dole **„Zakázat sledování oprávnění“** zapnuto (bez restartu), `settings put` funguje. Zároveň zapnuto „Při dobíjení nevypínat obrazovku“ (`stay_on_while_plugged_in=7`). Po resetu telefonu / aktualizaci OS zkontrolovat znovu.
- [ ] Přenos dat na nový telefon (device-to-device): `allowBackup="false"` vypíná cloud backup, D2D transfer zůstává na výchozím chování platformy — rozhodnout v Phase 6 (export/nastavení).
- [ ] Reddit nainstalovat na testovací telefon (Honza) — je v DoD Phase 1.
- [ ] R2: chová se Chrome bez `canRetrieveWindowContent`? Změří Phase 1; rozhodnutí Honzovo.
- [ ] Emulátory: ověřit, že pro API 28 existuje arm64 systémový obraz (`sdkmanager --list | grep android-28`).
- [ ] Ikona a barva aplikace — až Phase 4 (SPEC §42 nechává na implementaci).
- [ ] Podpisový keystore pro release — Phase 8, přes env proměnné, nikdy v gitu.

## Log rozhodnutí (nejnovější nahoře)

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

Nová session: přečti `CLAUDE.md`, tenhle soubor (stav fází + otevřené body) a fázi v `PLAN.md`, na které se pokračuje. Kickoff prompt pro Phase 0+1 je v `docs/prompts/kickoff-phase-0-1.md`.
