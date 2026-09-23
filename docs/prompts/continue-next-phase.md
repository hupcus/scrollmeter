# Continue prompt — next phase (reusable)

Paste into a new Claude Code session started in `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER`.
It always picks up the first unfinished phase from `handoff.md`, so the same text works for Phase 2 … 8.

---

Pracuj v `/Users/hupcus/Documents/VIBE-CODE/SCROLLMETER` — ScrollMeter (Android, Kotlin, Compose), repo `hupcus/scrollmeter`. Phase 0 a Phase 1 (měřicí POC) jsou hotové a mergnuté (tagy `v0.0`, `v0.1`).

**Nejdřív přečti:**
- `CLAUDE.md`,
- `handoff.md` (stav fází, exit report Phase 1, otevřené body, log rozhodnutí),
- z `PLAN.md` oddíly 1 (hotová rozhodnutí včetně D19), 5 (testování), 6 (rizika), 7 (git a proces) a **fázi, na kterou je řada** = první nedokončená v tabulce „Stav fází“ v `handoff.md`,
- ADR v `docs/measurement-decisions.md`.

Ze `docs/SPEC.md` otevírej jen oddíly, které ta fáze cituje. Než začneš, ověř, že na `main` je mergnutý plán „čas v aplikaci“ (D19 v `PLAN.md`, ADR-021/022). Pokud tam není, zeptej se mě.

**Úkol:** udělej tu jednu fázi přesně v rozsahu `PLAN.md`, nic z pozdějších fází. Pracuj na větvi `phase-N-…` z aktuálního `main`.
- **Stage 0:** impact analýza, protože fáze sahá na existující moduly.
- **Testy:** unit testy podle PLAN.
- **Ověření na telefonu:** OnePlus, serial v `CLAUDE.md`. Co musím udělat ručně (kalibrace kartou, restart telefonu, Přístup k údajům o využití), mi napiš jako přesný postup krok za krokem a počkej.
- **Brány před každým pushem:**
  - `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`,
  - `python3 -m unittest discover -s tools -p 'test_*.py'`,
  - `tools/check_manifest_policy.py` nad merged manifesty.
- **Konec fáze:**
  1. PR s DoD checklistem fáze, CI zelené.
  2. `/topshit` jednou nad kumulativním diffem.
  3. Stage 2: funkční + bezpečnostní review.
  4. Zápis do `handoff.md`: stav fáze, rozhodnutí, otevřené body, log.
  5. Napiš „mergni?“ a **čekej**.
- **Po mém „go“ / „mergni“:**
  1. Squash merge jako `hupcus`.
  2. Tag `v0.N`.
  3. Smazat větev.
  4. Pokračuj další fází stejným postupem, dokud neřeknu stop.
  5. Když se kontext plní, zapiš handoff a řekni mi, ať vložím tenhle prompt (`docs/prompts/continue-next-phase.md`) do nové session.

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
- **Telefon:** `uiautomator dump` odpojí služby Usnadnění. ColorOS při nedostatku paměti zabíjí proces služby. Záznam z debug buildu se čte přes `run-as` (viz `CLAUDE.md`).
- **Poctivost:** když něco nejde ověřit, napiš to, nepředstírej.
