# handoff.md — ScrollMeter

> Živý předávací dokument. Sem se zapisují **rozhodnutí, stav a otevřené body** — po každé fázi,
> a hned při každé změně pinu nebo konstanty. Zadání = `docs/SPEC.md`, plán = `PLAN.md`.

## Identita projektu

- Lokální cesta: `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER`
- GitHub: `hupcus/scrollmeter` (privátní) — **zatím nezaložený**, vznikne v Phase 0
- Package: `com.scrollmeter.app` (debug: `com.scrollmeter.app.debug`)
- Cíl: veřejná aplikace na Google Play (po Phase 8); do té doby sideload APK z GitHub Releases

## Toolchain (závazný — převzato z DETECT 08/2026, **potvrdit reálnými verzemi v Phase 0**)

| Věc | Verze | Pozn. |
|---|---|---|
| JDK pro build | **21** (`/opt/homebrew/opt/openjdk@21`) | systémová Java 25 AGP nepodporuje; pin `gradle/gradle-daemon-jvm.properties` `toolchainVersion=21` |
| Gradle wrapper | **8.14.3** | Gradle 9 rozbíjí AGP 8.x; homebrew gradle 9.6.1 jen na `gradle wrapper` |
| AGP | **8.13.2** | AGP 9 ne (built-in Kotlin, KSP/Room ekosystém) |
| Kotlin / KSP | 2.3.21 / 2.3.11 | Compose compiler v Kotlinu |
| Compose BOM | **2026.06.01** | 2026.08 chce compileSdk 37 + AGP 9.1 |
| Room / DataStore | 2.8.4 / 1.1.7 | |
| Navigation / Lifecycle / Activity | 2.9.8 / 2.9.4 / 1.13.0 | |
| compileSdk / target / min | 36 / 36 / **28** | `scrollDeltaX/Y` od API 28 |
| DI | žádné (ruční `AppGraph`) | ADR-003 |

## Testovací zařízení

| Zařízení | OS | Displej | Pozn. |
|---|---|---|---|
| OnePlus CPH2399 (Nord 2T), serial `W84LFE856LTWKNMN` | Android 14 / API 34 | 1080×2400, xdpi 403,411 / ydpi 401,052, densityDpi 480, 60/90 Hz | Chrome, Instagram, Facebook, Messenger, YouTube, TikTok, X, Play, Maps, Seznam Mapy; **Reddit chybí** |

## Stav fází

| Fáze | Stav | Větev / PR | Poznámka |
|---|---|---|---|
| 0 Bootstrap | nezačato | — | |
| 1 Measurement POC | nezačato | — | brána GO/NO-GO |
| 2 Kalibrace | nezačato | — | |
| 3 Persistence | nezačato | — | |
| 4 Dashboard | nezačato | — | |
| 5 Historie + Aplikace | nezačato | — | |
| 6 Export + Nastavení | nezačato | — | |
| 7 Onboarding + Policy | nezačato | — | |
| 8 Release | nezačato | — | |

## Otevřené body

- [ ] Reddit nainstalovat na testovací telefon (Honza) — je v DoD Phase 1.
- [ ] R2: chová se Chrome bez `canRetrieveWindowContent`? Změří Phase 1; rozhodnutí Honzovo.
- [ ] Emulátory: ověřit, že pro API 28 existuje arm64 systémový obraz (`sdkmanager --list | grep android-28`).
- [ ] Ikona a barva aplikace — až Phase 4 (SPEC §42 nechává na implementaci).
- [ ] Podpisový keystore pro release — Phase 8, přes env proměnné, nikdy v gitu.

## Log rozhodnutí (nejnovější nahoře)

### 2026-09-23 — založení projektu (Fable 5.1)
- Projekt založen ze zadání `docs/SPEC.md` v1.0. Plán `PLAN.md`, hotová rozhodnutí D1–D18 (PLAN §1), ADR-001…ADR-012 v `docs/measurement-decisions.md`.
- Toolchain převzat z DETECT (jediný Android projekt na tomhle Macu, který v 08/2026 buildil): JDK 21, Gradle 8.14.3, AGP 8.13.2, Compose BOM 2026.06.01. Bez Hiltu.
- Ověřeno prostředí: SDK 36, JDK 21 přítomné, telefon OnePlus CPH2399 připojený přes adb, žádná accessibility služba zapnutá, žádný emulátor, žádné Android Studio.
- Pořadí práce je záměrně obrácené proti „nejdřív hezké UI": Phase 1 = měřicí jádro + debug obrazovka + brána GO/NO-GO.

## Jak navázat

Nová session: přečti `CLAUDE.md`, tenhle soubor (stav fází + otevřené body) a fázi v `PLAN.md`, na které se pokračuje. Kickoff prompt pro Phase 0+1 je v `docs/prompts/kickoff-phase-0-1.md`.
