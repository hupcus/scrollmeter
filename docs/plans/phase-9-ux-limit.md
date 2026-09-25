# Phase 9 — UX: periods + daily limit (v0.2.0)

Honza's request (2026-09-25): statistics for today / this week / this month with per-day values; tapping a period
opens its history with a per-app breakdown (metres, sorted, time in app on the same row); durations in min → h →
d; the simplest possible UX; **not a competition** — the daily goal becomes a **limit** not to exceed, the UI turns
green → orange → red. Design by Fable 5.1 (plan agent), refined here; decisions go to ADR-036.

## Screens

- **Přehled** (start, only top-level screen; no bottom bar — Nastavení under a gear in the header):
  service banner · **Dnes card** (background = limit level; distance, "zbývá X do limitu Y" / "o X přes limit Y",
  "V aplikacích 1 h 12 min", comparison sentence when enabled; tap → Období Den today) · rows **Tento týden** /
  **Tento měsíc** (level dot, total, avg/day; tap → Období) · **Nejvíc dnes**: top 3 apps + "Všechny aplikace ›"
  (→ Období Den) · usage-access card · accuracy row.
- **Období** (`PeriodRoute(kind, anchor)`): segmented Den / Týden / Měsíc; ‹ label › (calendar day, week Mon–Sun,
  month; › disabled at the current period, ‹ disabled before the period containing the first measured day);
  header card (background = level; total, time in apps, Den: remaining/over sentence; Týden/Měsíc: "Průměr X / den"
  + remaining/over vs the limit); Týden/Měsíc: bar chart per day, each bar in its own level colour, dashed limit
  line, tap a bar → Den of that date; app list sorted by metres, row = icon · name · "1,21 km (3 h 40 min)",
  time-only apps "— (2 h 15 min)" last; tap → App detail for this period. Empty: "Za toto období nic naměřeno."
- **App detail** (`AppDetailRoute(pkg, kind, anchor)`): header "Instagram · Tento týden · 21.–27. 9.", no period
  selector; summary + "Podíl na vzdálenosti"; quality card and the two 30-day charts unchanged.
- **Nastavení**: pushed route with Back; "Denní limit · 500 m / Bez limitu"; switches "Překročení limitu" and
  "Shrnutí včerejška" (record gone). **O aplikaci** gains "Celkem naměřeno X · od <first day>".
- Deleted: History and Apps tabs/screens, rolling 7/30/12 periods, sort chips, share %/quality word in rows,
  TopApps "Ostatní", Nejvyšší/Nejnižší den, lifetime tile on Přehled, the goal ring.

## Limit

- `LimitLevel { NONE, UNDER, NEAR, OVER }`, ratio = distance / limit: UNDER < 0.7 ≤ NEAR < 1.0 ≤ OVER; NONE when the
  limit is 0 ("Bez limitu"). `MeasurementConfig.LIMIT_WARN_RATIO = 0.7`.
- Week / month: average per day vs the daily limit; counted days = range ∩ [first measured day, today].
- Colour on the Dnes card, Období header, bars, week/month dots — never the whole screen, never alone (text always).
- Fixed palette (not dynamic), light / dark per level: container / onContainer / bar —
  UNDER #C8E6C9/#1B5E20/#43A047 · #1E4620/#C8E6C9/#66BB6A; NEAR #FFE0B2/#7A3E00/#FB8C00 · #4E3200/#FFE0B2/#FFA726;
  OVER #FFCDD2/#8E1B1B/#E53935 · #5B1A1A/#FFCDD2/#EF5350; NONE = surfaceContainerHigh/onSurface/primary.
- Stored in the same DataStore key `daily_goal_mm` (0.0 = off), default 500 m; code renames goal → limit.
- Notifications: LIMIT ("Denní limit překročen — Dnes jsi přes svůj limit 500 m.", keys `notify_goal` /
  `notified_goal_date` kept), SUMMARY unchanged; RECORD removed with its constants, facts and query; channel id
  `goals` kept, name "Limit a shrnutí". No "approaching" notification.

## Duration format

null / < 0 → "—"; < 60 s → "< 1 min"; floored minutes; < 60 min "N min"; < 24 h "H h" / "H h M min"; ≥ 24 h
"D d" + non-zero "H h" / "M min" (e.g. 24 h → "1 d", 25 h 1 min → "1 d 1 h 1 min", 24 h 5 min → "1 d 5 min").
Units identical in cs / en.

## Implementation order

1. Pure helpers + tests: `format/TimeFormatter` (days), `insights/Period.kt`, `insights/DailyLimit.kt`,
   `insights/HistorySeries.kt` → day bars for any range, `insights/AppRanking.kt` trimmed to quality/share,
   delete `insights/TopApps.kt`; `MeasurementConfig` (+ LIMIT_WARN_RATIO, − RECORD_*).
2. Settings / notifications: renames, 0 = off, no record (rules, facts, DAO `daysBefore`, `PriorDays`).
3. Theme `LimitColors`, `BarChart` (bar colour per bar, dashed reference line), `ChartLabels` (period labels).
4. Screens: `LimitCard`, `AppList`, Dashboard rewrite, new `ui/period/PeriodScreen.kt` (replaces History + Apps),
   App detail takes the period, NavHost (routes, no bottom bar, gear), Settings header + limit dialog, About total.
5. Strings cs + en together (TranslationsTest parity).
6. `versionCode 2`, `versionName 0.2.0`.
7. Docs: ADR-036, SPEC deviation note, README UI section, architecture.md, PLAN.md Phase 9, handoff.md.
8. Gates, `/topshit`, Stage 2, emulator screenshots (cs/en, light/dark, UNDER/NEAR/OVER, Bez limitu, upgrade from
   v0.1.0), PR, merge, tag `v0.9`, release `v0.2.0`, install on the OnePlus + Samsung as an update.
