# Continue prompt — next phase (reusable)

Paste into a new Claude Code session started in `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER`.
It always picks up the first unfinished phase from `handoff.md`, so the same text works for Phase 2 … 8.

---

Pracuj v `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER` — ScrollMeter (Android, Kotlin, Compose), repo `hupcus/scrollmeter`. Stav fází, tagy a rozpracovanou práci najdeš v `handoff.md`.

**Nejdřív přečti:**
- `CLAUDE.md`,
- `handoff.md` (stav fází, exit report Phase 1, otevřené body, log rozhodnutí),
- z `PLAN.md` oddíly 1 (hotová rozhodnutí včetně D19), 5 (testování), 6 (rizika), 7 (git a proces) a **fázi, na kterou je řada** = první nedokončená v tabulce „Stav fází“ v `handoff.md`,
- ADR v `docs/measurement-decisions.md`.

Ze `docs/SPEC.md` otevírej jen oddíly, které ta fáze cituje. **Rozpracovaná fáze:** když `handoff.md` u fáze uvádí větev a PR a oddíl „Zbývá“, pokračuj na té větvi přesně od „Zbývá“ — nezakládej novou větev a neopakuj hotové kroky (Stage 0, `/topshit`, bezpečnostní review), pokud se od nich nezměnil kód.

**Režim od 2026-09-24 (viz `handoff.md` → „Režim práce“):** dodělej aplikaci až do konce bez zastavování. Rozhoduj sám, rozhodnutí zapiš do `handoff.md` nebo ADR. Ruční testy, které potřebují moje ruce, nečekej — zapiš je do „Dluh ověření“ v `handoff.md` a pokračuj.

**Zařízení:** automatické ověření běží na **emulátoru** (API 34, `google_apis` arm64-v8a; AVD viz `handoff.md`). Fyzický telefon je vrácený do původního stavu a používám ho — sahej na něj, jen když výslovně napíšu, že je k dispozici. Pak si ho připrav:
1. `installDebug`.
2. Zapni službu přes adb (příkazy v `CLAUDE.md`).
3. Na dobu práce `settings put global stay_on_while_plugged_in 7`.

Pokud telefon `settings put` odmítne, řekni mi, ať zapnu **Volby vývojáře → Zakázat sledování oprávnění** (je v části s aplikacemi, pod „Deaktivace omezení podřízených procesů“). Když řeknu „vrať telefon“, udělej to v tomhle pořadí:
1. Vypni službu (`settings delete secure enabled_accessibility_services`, `accessibility_enabled 0`).
2. `stay_on_while_plugged_in 0`.
3. Odinstaluj debug build.
4. Smaž výpisy `uiautomator` ze `/sdcard`.
5. Nakonec mi řekni, ať přepínač sledování oprávnění vypnu sám. Telefon mezitím zhasne a zamkne se, dlouhé hledání přes uiautomator nefunguje.

**Úkol:** udělej tu jednu fázi přesně v rozsahu `PLAN.md`, nic z pozdějších fází. Nová fáze = větev `phase-N-…` z aktuálního `main`; rozpracovaná fáze = její existující větev.
- **Stage 0:** impact analýza, protože fáze sahá na existující moduly.
- **Testy:** unit testy podle PLAN.
- **Ověření na zařízení:** emulátor (automaticky). Co by muselo proběhnout na skutečném telefonu nebo mýma rukama (kalibrace kartou, restart telefonu, Přístup k údajům o využití vs Digitální pohoda, aplikace s účtem, baterie), zapiš do „Dluh ověření“ jako přesný postup krok za krokem a **pokračuj**.
- **Brány před každým pushem:**
  - `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`,
  - `python3 -m unittest discover -s tools -p 'test_*.py'`,
  - `tools/check_manifest_policy.py` nad merged manifesty.
- **Konec fáze:**
  1. PR s DoD checklistem fáze, CI zelené.
  2. `/topshit` jednou nad kumulativním diffem.
  3. Stage 2: funkční + bezpečnostní review.
  4. Zápis do `handoff.md`: stav fáze, rozhodnutí, otevřené body, dluh ověření, log.
  5. **Bez ptaní:** squash merge jako `hupcus` (`unset GH_TOKEN GH_CONFIG_DIR`), tag `v0.N`, smazat větev a pokračuj další fází stejným postupem, dokud neřeknu stop.
  6. Když se kontext plní, zapiš handoff a řekni mi, ať vložím tenhle prompt (`docs/prompts/continue-next-phase.md`) do nové session.

**Pravidla:**
- **Jazyk a commity:** platí globální `CLAUDE.md` — pracovní chatter anglicky, deliverable česky, commity anglicky s trailerem.
- **Piny:** žádná změna pinovaných verzí bez záznamu v `handoff.md`.
- **Tvrdá pravidla:**
  - nikdy nečti text z eventů,
  - žádný `INTERNET`, `QUERY_ALL_PACKAGES` ani foreground service,
  - služba Usnadnění jen `typeViewScrolled` s `canRetrieveWindowContent=false`,
  - nová oprávnění jen ta, která plán a ADR výslovně povolují: `PACKAGE_USAGE_STATS` ve Phase 3 podle ADR-021, `POST_NOTIFICATIONS` ve Phase 6. Cokoli dalšího = ADR + můj souhlas.
- **Konstanty a přístup k Androidu:** každá změna = ADR.
- **Velké výstupy:** gradle výstupy směřuj do scratchpadu a grepuj, nečti je celé.
- **Screenshoty** ověřuj subagentem, obrázek nedávej do hlavního kontextu.
- **Telefon:** `uiautomator dump` odpojí služby Usnadnění — domovská obrazovka se pak přeskládá a klepnutí podle souřadnic z výpisu mine; po obrazovkách proto naviguj s vypnutou službou a zapni ji až potom. ColorOS při nedostatku paměti zabíjí proces služby. Záznam z debug buildu se čte přes `run-as` (viz `CLAUDE.md`). USB kabel občas na chvíli vypadne — `adb get-state` a opakovat.
- **Poctivost:** když něco nejde ověřit, napiš to, nepředstírej.
