# Kickoff prompt — Phase 0 + Phase 1

Paste into a new Claude Code session started in `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER`
(the project's `.claude/settings.json` sets the model to Opus).

---

Pracuj v `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER` — nový projekt ScrollMeter (Android, Kotlin, Compose). Git repo je založené, zatím bez remotu.

Nejdřív přečti `CLAUDE.md`, `handoff.md` a z `PLAN.md` oddíl 1 (hotová rozhodnutí), oddíl 2 (prostředí), Phase 0, Phase 1 a oddíl 5 (testování). Zadání je `docs/SPEC.md` — přečti §1, §5–§12, §34, §35, §61–§63, §70 a §80; zbytek otevírej podle potřeby.

Úkol: udělej **pouze Phase 0 a Phase 1** podle `PLAN.md`. Nic z Phase 2+ nezačínej.

1. `gh repo create hupcus/scrollmeter --private --source=. --remote=origin --push`, pak větev `phase-0-bootstrap`.
2. **Phase 0:** založ Android projekt přesně s pinovaným toolchainem z `CLAUDE.md` (JDK 21 přes `gradle/gradle-daemon-jvm.properties`, wrapper 8.14.3 vygenerovaný homebrew gradlem, AGP 8.13.2, Kotlin 2.3.21, Compose BOM 2026.06.01, Room 2.8.4, **bez Hiltu**, minSdk 28 / target 36). Strukturu gradle souborů okoukej z `/Users/hupcus/Documents/VIBE-CODE/DETECT` (jen verze a tvar, ne obsah aplikace). Manifest bez `INTERNET`. Přidej GitHub Actions CI (`testDebugUnitTest lintDebug assembleDebug`). Brána: `assembleDebug` + `testDebugUnitTest` + `lintDebug` zelené, `installDebug` na připojený OnePlus (serial v `CLAUDE.md`) a aplikace se spustí (screenshot ověř subagentem, obrázek nedávej do hlavního kontextu), CI na PR zelené. Otevři PR, doplň reálné verze do `handoff.md`, napiš mi „mergni?" a **čekej**.
3. **Phase 1** (po merge, větev `phase-1-measurement-poc`): `ScrollAccessibilityService` jen na `typeViewScrolled` s `canRetrieveWindowContent=false` a `isAccessibilityTool=false`; `AccessibilityEventParser` → `ScrollSample` (jen primitiva, callback končí předáním do channelu); čistě kotlinový `measurement/` engine (validator s 4× diagonálou, fallback tracker, xdpi/ydpi scale, hypot kalkulátor) s unit testy podle SPEC §57–§59 včetně příkladů 420 dpi/1200 px → 72,5714 mm a 300/400 px → 30 mm; policy guard test; debug obrazovka posledních 100 eventů (package, dx, dy, mm, source, accepted/rejected, Clear / Pause / Export CSV); hlavní obrazovka se stavem služby a tlačítkem do Accessibility settings; testovací `LazyColumn` + `LazyRow` s ground truth; `tools/analyze_debug_csv.py`.
   Službu si pro POC zapni přes adb (příkazy v `CLAUDE.md`). Vlastní test list a Chrome otestuj **automaticky** přes `adb shell input swipe` + `logcat -s ScrollMeter:D` (Test A, C, D, E ze SPEC §36). Pak mi napiš přesný scénář, co mám ručně proscrollovat (Instagram, Facebook, YouTube, TikTok, X, Reddit, Play, Mapy, Nastavení, in-app browser — Reddit si nainstaluju) a jak dlouho; po každé aplikaci mi řekni, jak exportovat CSV. Výsledky zapiš do `docs/accuracy-testing.md` a do `handoff.md` **exit report**: per-app tabulka, verdikt GO/NO-GO, dedupe ano/ne s daty, vyřazuje 4× diagonála validní flingy, a jestli Chrome bez `canRetrieveWindowContent` vůbec něco posílá (pokud ne, změř to na throwaway větvi s `true` a rozhodnutí nech na mně — ADR-013).
   Otevři PR, `/topshit` nad kumulativním diffem, Stage 2, `handoff.md`, README s návodem „jak POC ručně otestovat". **Zastav se a čekej na moje GO.**

Pravidla: platí globální `CLAUDE.md` (chatter anglicky, deliverable česky, commity anglicky s trailerem). Žádná změna pinovaných verzí bez záznamu v `handoff.md`. Nikdy nečti text z eventů, žádný `INTERNET`, žádný `QUERY_ALL_PACKAGES`, žádná foreground service. Velké výstupy gradle směřuj do scratchpadu a grepuj, nečti je celé. Když něco nejde ověřit, napiš to, nepředstírej.
