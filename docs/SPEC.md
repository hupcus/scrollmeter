# ScrollMeter – detailní zadání pro Claude Code

> Verze zadání: 1.0  
> Datum: 23. 9. 2026  
> Platforma: Android  
> Pracovní název: **ScrollMeter**  
> Cíl: vytvořit použitelnou Android aplikaci, která měří, kolik fyzické vzdálenosti uživatel „nascrolloval“ na telefonu, a převádí scrollování na metry a kilometry.

---

# 1. Co má Claude Code postavit

Vytvoř kompletní Android aplikaci **ScrollMeter** v Kotlinu a Jetpack Compose.

Aplikace má na pozadí sledovat scrollování uživatele napříč ostatními aplikacemi prostřednictvím Android `AccessibilityService` a z dostupných scrollovacích událostí počítat:

- vzdálenost scrollování dnes,
- vzdálenost tento týden,
- vzdálenost tento měsíc,
- celkovou vzdálenost,
- vzdálenost podle jednotlivých aplikací,
- historii po dnech,
- počet scrollovacích událostí,
- kvalitu / spolehlivost měření.

Primární jednotky:

- mm interně,
- m pro běžné zobrazení,
- km pro vyšší hodnoty.

Aplikace musí fungovat lokálně a pro MVP nemá vyžadovat:

- účet,
- cloud,
- backend,
- přihlášení,
- internetové připojení.

## Kritická definice metriky

**Neměř přesnou fyzickou trajektorii prstu po skle.**

Na standardním nerootovaném Androidu nelze napříč cizími aplikacemi spolehlivě získávat raw `MotionEvent` souřadnice každého dotyku bez režimů a zásahů, které nejsou vhodné pro běžnou spotřebitelskou aplikaci.

Měř:

> **fyzický ekvivalent vzdálenosti, o kterou se posunul scrollovatelný obsah na displeji.**

To znamená, že se má započítat i inertní / fling scrollování po puštění prstu.

Produktové texty proto používej ve stylu:

- „Dnes jsi nascrolloval 428 metrů.“
- „Instagram: 187 m.“
- „Tento týden: 2,8 km.“

Nepoužívej jako technicky přesné tvrzení:

- „Tvůj palec dnes urazil 428 metrů.“
- „Měříme přesnou dráhu prstu.“

V aplikaci může být lidsky srozumitelný marketingový text, ale v onboarding / info části musí být vysvětleno, že jde o **scroll distance**, tedy posun obsahu, nikoliv raw trajektorii prstu.

---

# 2. Hlavní produktová myšlenka

Na trhu již existují aplikace, které podobnou funkci mají, např. Scrollscape. Proto ScrollMeter nesmí být pouze další jednoduchý čítač metrů.

Hlavní diferenciace má být:

1. **Transparentní a kalibrovatelný výpočet vzdálenosti.**
2. **Offline-first a privacy-first.**
3. **Žádné čtení textů, zpráv ani obsahu obrazovky.**
4. **Používat pouze minimální rozsah Accessibility API.**
5. **Ukazovat kvalitu měření podle aplikace.**
6. **Export vlastních dat.**
7. **Velmi čisté a moderní UI.**
8. **Srozumitelné převody vzdálenosti na reálné vzdálenosti.**
9. **Žádný agresivní „digital detox“ v MVP.**
10. **Měřit horizontální i vertikální scrollování.**

---

# 3. Technologický stack

Použij:

- Kotlin
- Jetpack Compose
- Material 3
- Android Architecture Components
- Room
- DataStore Preferences
- Kotlin Coroutines + Flow
- WorkManager pouze tam, kde skutečně dává smysl
- Gradle Kotlin DSL
- Version Catalog
- JUnit
- AndroidX Test / Compose UI Test

## SDK

K 23. 9. 2026:

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 28`

Důvod pro `minSdk = 28`:

`AccessibilityRecord.getScrollDeltaX()` a `getScrollDeltaY()` jsou dostupné od API 28 a právě ty tvoří nejkvalitnější primární zdroj dat.

Pokud aktuální stabilní Android Studio / knihovny vyžadují mírnou úpravu verzí závislostí, použij nejnovější vzájemně kompatibilní stabilní verze. Nepoužívej alpha knihovny bez důvodu.

---

# 4. Architektura projektu

Pro první verzi NEVYTVÁŘEJ zbytečně složitý multi-module projekt.

Použij jeden `app` modul, ale čistě oddělené balíčky.

Navržená struktura:

```text
com.scrollmeter.app
│
├── accessibility
│   ├── ScrollAccessibilityService.kt
│   ├── AccessibilityEventParser.kt
│   └── AccessibilityStatusChecker.kt
│
├── measurement
│   ├── ScrollMeasurementEngine.kt
│   ├── ScrollDistanceCalculator.kt
│   ├── ScrollEventValidator.kt
│   ├── ScrollFallbackTracker.kt
│   ├── MeasurementQuality.kt
│   └── PhysicalScaleProvider.kt
│
├── calibration
│   ├── CalibrationRepository.kt
│   ├── CalibrationMethod.kt
│   └── DisplayMetricsProvider.kt
│
├── data
│   ├── local
│   │   ├── ScrollDatabase.kt
│   │   ├── dao
│   │   └── entity
│   ├── repository
│   └── model
│
├── aggregation
│   ├── ScrollAccumulator.kt
│   ├── ScrollSessionManager.kt
│   └── DailyAggregationWorker.kt
│
├── ui
│   ├── onboarding
│   ├── dashboard
│   ├── history
│   ├── apps
│   ├── calibration
│   ├── settings
│   ├── about
│   └── components
│
├── export
│   └── CsvExporter.kt
│
└── MainActivity.kt
```

Používej dependency injection pouze pokud přináší reálný užitek. Hilt je povolený, ale není nutné ho použít jen proto, že je populární.

Preferuj jednoduchý, dobře testovatelný návrh.

---

# 5. Nejdůležitější část – AccessibilityService

Vytvoř:

```kotlin
class ScrollAccessibilityService : AccessibilityService()
```

Služba má přijímat **pouze**:

```kotlin
AccessibilityEvent.TYPE_VIEW_SCROLLED
```

## Minimalizace oprávnění

Aplikace NEMÁ:

- číst text obrazovky,
- číst zprávy,
- analyzovat formuláře,
- ukládat text z AccessibilityNodeInfo,
- ovládat jiné aplikace,
- klikat za uživatele,
- měnit nastavení,
- používat touch exploration,
- zachytávat raw dotyková gesta,
- používat `FLAG_SEND_MOTION_EVENTS`,
- používat `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, pokud to nebude při implementaci prokazatelně nutné.

Výchozí implementace musí fungovat bez:

```xml
android:canRetrieveWindowContent="true"
```

Preferovaná hodnota:

```xml
android:canRetrieveWindowContent="false"
```

Accessibility config má poslouchat pouze scroll eventy.

Například:

```xml
<accessibility-service
    android:accessibilityEventTypes="typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="0"
    android:canRetrieveWindowContent="false"
    android:isAccessibilityTool="false" />
```

Přesnou konfiguraci uprav podle aktuálního Android API a ověř kompilací.

## Proč minimalizovat Accessibility API

Je to důležité kvůli:

- soukromí,
- důvěře uživatele,
- spotřebě baterie,
- Google Play review,
- stabilitě,
- menšímu množství edge cases.

---

# 6. Jak zpracovat jeden scroll event

Pro každý:

```kotlin
TYPE_VIEW_SCROLLED
```

získej minimálně:

```kotlin
event.eventTime
event.packageName
event.scrollDeltaX
event.scrollDeltaY
event.scrollX
event.scrollY
event.windowId
event.className
```

Nikdy neukládej textový obsah eventu.

## Priorita zdrojů

### Metoda A – hlavní metoda

Použij:

```kotlin
dx = event.scrollDeltaX
dy = event.scrollDeltaY
```

Tyto hodnoty reprezentují změnu scroll pozice v pixelech.

Pokud alespoň jedna z hodnot není 0, použij je.

### Metoda B – fallback

Některé aplikace mohou emitovat `TYPE_VIEW_SCROLLED`, ale `scrollDeltaX/Y` může být 0 nebo nepoužitelné.

V takovém případě lze zkusit rozdíl:

```text
dx = currentScrollX - previousScrollX
dy = currentScrollY - previousScrollY
```

Použij však fallback pouze pokud:

- předchozí event patřil ke stejnému package,
- ideálně ke stejnému `windowId`,
- časový odstup je krátký,
- předchozí a aktuální hodnoty vypadají validně,
- výsledný skok neprojde jako outlier.

Tracker klíčuj například:

```text
packageName + windowId + className
```

Nespoléhej na to, že `scrollX/Y` bude vždy monotónní nebo globální. RecyclerView, lazy lists, WebView a nekonečné feedy mohou hodnoty resetovat nebo reprezentovat jinou logiku.

### Metoda C – nic nedopočítávat

Pokud nejsou dostupná smysluplná pixelová data:

- událost eviduj jako `unmeasurable`,
- vzdálenost = 0,
- NESNAŽ se převádět `fromIndex`, `toIndex` nebo počet položek na centimetry.

Raději mírné podměření než falešná přesnost.

---

# 7. Výpočet vzdálenosti

Interní jednotka:

```text
millimetres
```

## Varianta s oddělenou kalibrací X/Y

Měj:

```text
mmPerPxX
mmPerPxY
```

Pro jeden event:

```text
dxMm = dxPx * mmPerPxX
dyMm = dyPx * mmPerPxY
```

Vzdálenost:

```text
eventDistanceMm = sqrt(dxMm² + dyMm²)
```

Použij absolutní velikost vektoru.

Kotlin:

```kotlin
val dxMm = dxPx.toDouble() * mmPerPxX
val dyMm = dyPx.toDouble() * mmPerPxY

val distanceMm = hypot(dxMm, dyMm)
```

To zajistí, že:

- vertikální scroll = správná vertikální vzdálenost,
- horizontální scroll = správná horizontální vzdálenost,
- diagonální scroll není započítán dvakrát.

## Součet

```text
totalDistanceMm += eventDistanceMm
```

Pokud uživatel scrolluje 10 cm dolů a 10 cm nahoru:

```text
celková scroll distance = 20 cm
```

Nejde o výsledný displacement, ale o celkovou uraženou scrollovací vzdálenost.

---

# 8. Převod pixelů na fyzické mm

Toto je zásadní pro přesnost celé aplikace.

Použij následující hierarchii.

## PRIORITA 1 – ruční fyzická kalibrace

Toto je doporučená nejpřesnější metoda.

Uživatel během onboardingu dostane možnost:

> „Zkalibruj displej pro přesnější měření.“

Na obrazovce zobraz vodorovný kalibrační pruh.

Uživatel ho nastaví tak, aby odpovídal šířce běžné platební karty:

```text
ISO/IEC 7810 ID-1 width = 85.60 mm
```

Uživatel posuvníkem mění délku pruhu.

Aplikace musí znát skutečnou délku pruhu v RAW pixelech:

```text
referencePixels
```

Potom:

```text
mmPerPx = 85.60 / referencePixels
```

U běžného telefonu předpokládej čtvercové pixely:

```text
mmPerPxX = mmPerPx
mmPerPxY = mmPerPx
```

Ulož:

```text
CalibrationMethod.MANUAL_CARD
```

Tato metoda má nejvyšší confidence.

### UX kalibrace

Screen:

**Kalibrace displeje**

Text:

> Přilož platební kartu k displeji a posuvníkem uprav modrou čáru tak, aby měla stejnou délku jako delší strana karty.

Tlačítko:

> Uložit kalibraci

Také umožni:

> Přeskočit – použít automatický odhad

---

# 9. Automatická kalibrace pomocí xdpi / ydpi

Android poskytuje:

```kotlin
DisplayMetrics.xdpi
DisplayMetrics.ydpi
```

Výpočet:

```text
mmPerPxX = 25.4 / xdpi
mmPerPxY = 25.4 / ydpi
```

Protože:

```text
1 inch = 25.4 mm
```

Příklad:

```text
ydpi = 420

mmPerPxY = 25.4 / 420
         = 0.060476 mm/px
```

Scroll:

```text
dy = 1000 px
```

odpovídá:

```text
60.48 mm
= 6.048 cm
```

## Důležité

Pro fyzickou vzdálenost NEPOUŽÍVEJ jako primární údaj:

```kotlin
density
densityDpi
```

`densityDpi` je logická hustota Android UI a nemusí přesně odpovídat skutečné fyzické PPI panelu.

Pro fyzickou vzdálenost preferuj:

1. manual calibration,
2. `xdpi / ydpi`,
3. případně modelovou databázi.

Ulož:

```text
CalibrationMethod.DISPLAY_METRICS
```

Confidence = medium.

---

# 10. Model telefonu

Aplikace může automaticky zobrazit:

```kotlin
Build.MANUFACTURER
Build.MODEL
Build.DEVICE
```

Model NEPOUŽÍVEJ jako povinný vstup.

Uživatel nemá ručně hledat model telefonu jen proto, aby mohl aplikaci používat.

Do budoucna lze přidat databázi známých zařízení:

```text
model
nativeWidthPx
nativeHeightPx
physicalDiagonalInches
ppi
```

Výpočet:

```text
ppi = sqrt(widthPx² + heightPx²) / diagonalInches
mmPerPx = 25.4 / ppi
```

Ale pro MVP databázi modelů nedělej.

Důvody:

- tisíce zařízení,
- varianty panelů,
- rozdílné regionální modely,
- další maintenance,
- ruční kalibrace je jednodušší a přesnější.

---

# 11. Validace a outliers

Některé aplikace mohou poslat nesmyslný skok.

Measurement engine musí obsahovat `ScrollEventValidator`.

## Reject

Event ignoruj, pokud:

```text
packageName == null
```

nebo package odpovídá vlastní aplikaci mimo interní testovací režim.

## Outlier detection

Zjisti aktuální:

```text
screenWidthPx
screenHeightPx
screenDiagonalPx
```

```text
screenDiagonalPx = sqrt(widthPx² + heightPx²)
```

Defaultní bezpečnostní hranice:

```text
MAX_EVENT_DISTANCE = 4 × screenDiagonalPx
```

Pokud:

```text
hypot(dx, dy) > MAX_EVENT_DISTANCE
```

nepřičítej event automaticky do celku.

Zařaď jej jako:

```text
OUTLIER_REJECTED
```

Konstanta musí být centralizovaná a snadno upravitelná po testování.

Neimplementuj agresivní clipping typu:

```text
min(distance, limit)
```

Raději event celý označ jako podezřelý, aby se nezkreslovala statistika.

Během testů ověř, zda 4× screen diagonal nevyřazuje validní fling události. Pokud ano, hranici uprav na základě reálných dat.

---

# 12. Fling / inertní scrollování

Započítávej jej.

Příklad:

1. uživatel táhne prstem 4 cm,
2. pustí displej,
3. obsah díky kinetice pokračuje dalších 25 cm.

ScrollMeter má započítat přibližně:

```text
29 cm scroll distance
```

pokud Android během celého pohybu emituje odpovídající scroll delta eventy.

To je správně, protože aplikace měří:

> vzdálenost scrollovaného obsahu

ne:

> fyzickou trajektorii prstu.

---

# 13. Horizontal scroll

Započítávej:

- carousely,
- horizontální feedy,
- swipe mezi kartami, pokud je reportován jako scroll,
- tabulky,
- galerie,
- další scrollovatelné UI.

Použij společný vektorový výpočet X + Y.

Nepořizuj zvláštní „vertikální metry“ a „horizontální metry“ jako hlavní statistiku.

Volitelně je můžeš uchovat interně:

```text
verticalDistanceMm
horizontalDistanceMm
```

pro budoucí analytiku.

---

# 14. Co se nemá započítávat

Ve výchozím stavu ignoruj:

```text
vlastní ScrollMeter package
```

Dále vytvoř blacklist / exclusion mechanismus.

Defaultně lze nabídnout vypnutí měření pro:

- System UI,
- launcher,
- klávesnice,
- konkrétní aplikace zvolené uživatelem.

Nezakóduj však příliš mnoho package names natvrdo, protože výrobci používají jiné systémové aplikace.

V nastavení vytvoř:

> **Vyloučené aplikace**

Uživatel může z měření odstranit konkrétní aplikaci.

---

# 15. Package / aplikace

Z `event.packageName` ukládej:

```text
packageName
```

např.:

```text
com.instagram.android
com.google.android.youtube
com.reddit.frontpage
```

Když je možné bezpečně získat label/icon přes `PackageManager`, zobraz:

```text
Instagram
YouTube
Reddit
```

Pokud package info kvůli package visibility není dostupné:

- aplikace nesmí spadnout,
- zobraz package name,
- nevyžaduj automaticky `QUERY_ALL_PACKAGES`.

`QUERY_ALL_PACKAGES` do MVP nepřidávej.

Pokud bude později nutné ho použít, musí být nejprve ověřena Google Play politika a oprávněnost use case.

---

# 16. Buffered zápis do databáze

NEZAPISUJ jeden Room INSERT pro každý scroll event.

To by zbytečně:

- zatěžovalo storage,
- spotřebovávalo baterii,
- generovalo mnoho I/O.

Vytvoř `ScrollAccumulator`.

V paměti agreguj data podle:

```text
date + packageName
```

Flush proveď například:

- každých 10 sekund,
- nebo po 50 eventech,
- při změně dne,
- při ukončení / přerušení služby,
- při relevantním lifecycle callbacku.

Použij `UPSERT`.

Cíl:

ztráta při neočekávaném kill procesu maximálně několik sekund dat, ne desítky minut.

Hodnoty flush intervalů dej do centrální konfigurace.

---

# 17. Datový model

## DailyAppAggregateEntity

```kotlin
@Entity(
    tableName = "daily_app_aggregate",
    primaryKeys = ["date", "packageName"]
)
data class DailyAppAggregateEntity(
    val date: String,
    val packageName: String,

    val distanceMm: Double,

    val horizontalDistanceMm: Double,
    val verticalDistanceMm: Double,

    val rawDeltaXPx: Long,
    val rawDeltaYPx: Long,

    val measuredEventCount: Long,
    val fallbackEventCount: Long,
    val unmeasurableEventCount: Long,
    val rejectedOutlierCount: Long,

    val firstEventTimestamp: Long?,
    val lastEventTimestamp: Long?
)
```

## CalibrationEntity / Preferences

Ulož minimálně:

```text
calibrationMethod
mmPerPxX
mmPerPxY
calibratedAt
deviceManufacturer
deviceModel
xdpiAtCalibration
ydpiAtCalibration
```

## Settings

DataStore:

```text
dailyGoalMm
useManualCalibration
showComparisons
excludedPackages
theme
unitPreference
onboardingCompleted
privacyDisclosureAccepted
```

---

# 18. Sessions

Přidej jednoduchou logiku scroll session.

Nová session vzniká pokud mezi dvěma měřitelnými scroll eventy uplynulo více než:

```text
60 sekund
```

Konstanta:

```kotlin
SCROLL_SESSION_GAP_MS = 60_000L
```

Session může obsahovat:

```text
start
end
packageName
distanceMm
eventCount
```

Sessions nejsou nutné pro základní součet, ale umožní později:

- „nejdelší scroll session“,
- „večer jsi scrolloval 820 m“,
- počet scrollovacích session,
- průměrná délka session.

Pokud by ukládání sessions zbytečně komplikovalo MVP, navrhni schéma nyní, ale implementaci lze označit jako Phase 1.1.

---

# 19. Časové agregace

Data musí být zobrazitelná:

## Dnes

```text
SUM(distanceMm WHERE date = today)
```

## Týden

Použij lokální týden:

```text
Monday -> Sunday
```

Pro první verzi evropské výchozí nastavení.

Architektura však nemá bránit pozdější lokalizaci prvního dne týdne.

## Měsíc

Kalendářní měsíc.

## Lifetime

```text
SUM(distanceMm)
```

Používej lokální časovou zónu zařízení.

Při změně time zone nesmí databáze spadnout.

Historická data nepřepisuj zpětně.

---

# 20. Measurement quality / Confidence

Tohle je jedna z hlavních funkcí, která může ScrollMeter odlišit.

Pro každou aplikaci počítej:

```text
measuredEvents
fallbackEvents
unmeasurableEvents
outliers
```

Vytvoř `MeasurementQuality`.

Například:

### HIGH

- většina eventů používá `scrollDeltaX/Y`,
- manuální kalibrace,
- minimum outlierů.

### MEDIUM

- automatická xdpi/ydpi kalibrace,
- nebo častější fallback scrollX/Y.

### LOW

- velká část scroll eventů nemá použitelný delta údaj,
- velké množství fallbacků,
- podezřelé eventy.

Neukazuj uživateli falešné procento přesnosti typu:

```text
97.4 % přesnost
```

pokud pro to nemáme ground truth.

UI raději:

```text
Kvalita měření: vysoká
```

a info:

> Tato aplikace poskytuje standardní Android scroll data, takže měření je pravděpodobně spolehlivé.

---

# 21. Dashboard

Hlavní obrazovka musí být velmi jednoduchá.

## Header

```text
Dnes
```

Velká hodnota:

```text
428 m
```

pod tím:

```text
z cíle 500 m
```

Progress ring / progress bar.

## Secondary stats

```text
Tento týden   2,84 km
Tento měsíc   11,7 km
Celkem        42,6 km
```

## Top aplikace dnes

Například:

```text
Instagram        187 m
Chrome            96 m
Reddit            74 m
YouTube           42 m
Ostatní           29 m
```

## Comparison card

Například:

```text
Dnes jsi nascrolloval přibližně délku 4 fotbalových hřišť.
```

Tyto převody jsou jen ilustrace a nesmějí měnit primární data.

---

# 22. Převody do reálného světa

Vytvoř čistou helper vrstvu:

```text
DistanceComparisonProvider
```

Příklady orientačních referencí:

```text
footballField = 105 m
EiffelTowerHeight = 330 m
runningTrackLap = 400 m
5kRun = 5 000 m
halfMarathon = 21 097.5 m
marathon = 42 195 m
```

Preferuj vzdálenosti, které dávají smysl v daném rozsahu.

Příklad:

```text
428 m
```

=> přibližně 1 běžecký okruh.

```text
4.2 km
```

=> téměř 5 km běh.

```text
42.3 km
```

=> přibližně maraton.

Nezahlcuj dashboard.

Zobraz maximálně jednu vhodnou comparison card.

---

# 23. History

Vytvoř obrazovku:

> Historie

Přepínač:

```text
7 dní
30 dní
12 měsíců
```

Graf:

- osa X = čas,
- osa Y = metry / km.

Pod grafem:

```text
Průměr / den
Nejvyšší den
Nejnižší den
Celkem
```

Graf musí být dobře čitelný a nesmí být závislý na internetové knihovně.

Pokud použiješ chart knihovnu, použij aktivně udržovanou, stabilní a rozumně lehkou knihovnu.

Pokud je jednodušší vykreslit základní bar chart vlastní Compose implementací, preferuj vlastní řešení.

---

# 24. Apps breakdown

Obrazovka:

> Aplikace

Defaultní období:

```text
Dnes
```

Přepínač:

```text
Dnes | 7 dní | 30 dní | Celkem
```

Řazení podle vzdálenosti sestupně.

Každý řádek:

```text
[icon] Instagram

187 m
43,7 %
Measurement quality: High
```

Po otevření detailu:

```text
Instagram
Dnes
7 dní
30 dní
Lifetime
```

Graf historie dané aplikace.

---

# 25. Daily goal

Uživatel může nastavit:

```text
100 m
250 m
500 m
1 km
2 km
5 km
vlastní
```

Goal není zákaz.

MVP nemá:

- blokovat aplikace,
- zobrazovat fullscreen overlay,
- automaticky zasahovat do jiné aplikace.

Pouze:

```text
„Dnes jsi překročil svůj cíl 500 m.“
```

Notification je volitelná.

Tím minimalizujeme:

- Accessibility policy risk,
- overlay oprávnění,
- behaviorální problémy,
- složitost.

---

# 26. Notifikace

Volitelné notifikace:

```text
Denní cíl dosažen
500 m
```

```text
Nový rekord
Dnes jsi nascrolloval 1,8 km.
```

Neposílej notifikaci při každém scroll eventu.

U Android 13+ respektuj runtime notification permission.

Notifikace nejsou potřeba pro samotné měření.

---

# 27. Widget

Phase 1.1 / Phase 2.

Home screen widget:

```text
ScrollMeter

Dnes
428 m

████████░░
428 / 500 m
```

Aktualizovat rozumně, nikoliv při každém scroll eventu.

---

# 28. CSV export

Toto chci už v první použitelné verzi.

Export:

```text
date,package_name,app_name,distance_mm,distance_m,event_count,fallback_count,unmeasurable_count,outlier_count
```

Příklad:

```csv
2026-09-23,com.instagram.android,Instagram,187450,187.45,1280,14,3,0
```

Druhý export:

```text
daily_summary.csv
```

```text
date,total_distance_m,events
```

Použij Android Storage Access Framework / share sheet.

Nevyžaduj broad storage permission.

---

# 29. Privacy

ScrollMeter má být privacy-first.

MVP:

- žádný backend,
- žádný analytics SDK,
- žádné reklamy,
- žádný INTERNET permission, pokud není technicky nutný,
- žádné odesílání dat mimo telefon.

AccessibilityService ukládá pouze:

```text
timestamp
package name
scroll deltas
odvozenou distance
```

Neukládá:

```text
text
passwords
messages
URLs
screen content
form values
usernames
```

V aplikaci vytvoř obrazovku:

> Soukromí

Text jasně vysvětluje:

> ScrollMeter používá službu zpřístupnění pouze k detekci scrollovacích událostí. Nečte ani neukládá text, zprávy, hesla nebo obsah obrazovky. Naměřená data zůstávají v telefonu.

---

# 30. Google Play Accessibility disclosure

Aplikaci NEOZNAČUJ:

```text
isAccessibilityTool=true
```

Není primárně pomůckou pro lidi s postižením.

Musí být:

```text
isAccessibilityTool=false
```

Před odesláním uživatele do Android Accessibility Settings ukaž samostatný prominent disclosure screen.

## Návrh disclosure

Nadpis:

> Povolit měření scrollování

Text:

> ScrollMeter potřebuje přístup ke službě Zpřístupnění, aby Android aplikaci informoval o scrollování v ostatních aplikacích.
>
> ScrollMeter používá pouze scrollovací události a název aplikace, ve které ke scrollování došlo.
>
> Nečte ani neukládá text obrazovky, zprávy, hesla ani obsah formulářů.
>
> Data jsou zpracována a uložena pouze v tomto zařízení.

Checkbox není nutný, pokud je explicitní tlačítko dostatečnou affirmative action, ale implementace musí splnit aktuální Google Play požadavky.

Tlačítko:

> Rozumím a chci pokračovat

Až poté:

> Otevřít nastavení zpřístupnění

Nepřeskakuj tento krok.

Při publikaci bude potřeba:

- Accessibility declaration v Play Console,
- jasný use case,
- video ukazující onboarding a používání služby,
- popis Accessibility API v Google Play listing.

---

# 31. Onboarding

Maximálně 4–5 kroků.

## Screen 1

```text
Kolik toho denně nascrolluješ?
```

Krátké vysvětlení.

## Screen 2

```text
Jak měření funguje
```

Vysvětlení:

- měříme posun obsahu,
- nikoliv obsah obrazovky.

## Screen 3

Privacy disclosure.

## Screen 4

Accessibility service setup.

Po návratu do aplikace automaticky detekuj, zda je služba aktivní.

## Screen 5

Kalibrace.

Nabídni:

```text
Zkalibrovat platební kartou
Použít automatický odhad
```

Pak otevři dashboard.

---

# 32. Accessibility status

Implementuj:

```kotlin
AccessibilityStatusChecker
```

Aplikace musí poznat:

```text
ENABLED
DISABLED
```

Pokud je vypnutá:

Dashboard nahoře zobrazí výrazný stav:

```text
Měření je vypnuté
```

Tlačítko:

```text
Zapnout měření
```

Nikdy netvrď, že sbírá data, pokud accessibility service není aktivní.

---

# 33. Kalibrace confidence

Ulož confidence podle metody:

```text
MANUAL_CARD -> HIGH
DISPLAY_METRICS -> MEDIUM
MODEL_DATABASE -> MEDIUM/HIGH podle zdroje
UNKNOWN -> LOW
```

Na About / Accuracy screen ukaž:

```text
Kalibrace:
Platební karta

1 pixel = 0.0612 mm

Kalibrováno:
23. 9. 2026
```

Tlačítko:

```text
Překalibrovat
```

---

# 34. Debug screen

V debug buildu vytvoř vývojářskou obrazovku:

```text
Debug measurement
```

Ukazuj živě:

```text
package
dx
dy
distance mm
measurement method
timestamp
accepted/rejected
```

Posledních například 100 eventů drž pouze v RAM.

Neukládej jejich textový obsah.

Funkce:

```text
Clear
Pause
Export debug data
```

Debug CSV může obsahovat:

```text
timestamp,package,dx_px,dy_px,distance_mm,source,status
```

Toto je zásadní pro testování různých aplikací.

Debug screen nesmí být dostupný v produkčním buildu.

---

# 35. Testovací režim v aplikaci

Vytvoř interní screen:

> Measurement Test

Obsah:

- dlouhý vertikální seznam,
- horizontální carousel,
- tlačítko reset,
- zobrazení přesné interní scroll distance vlastní komponenty,
- vedle toho hodnota naměřená AccessibilityService.

Cíl:

porovnat:

```text
ground truth scroll delta
vs.
Accessibility measurement
```

Proto v interním testovacím režimu dočasně NEVYLUČUJ vlastní package.

Po skončení testu se vlastní package z normální statistiky opět vyloučí.

---

# 36. Test přesnosti

Proveď minimálně tyto testy.

## Test A – lineární scroll

Scroll list přesně o známou pixelovou vzdálenost:

```text
500 px
1000 px
5000 px
```

Porovnej:

```text
expected
measured
error
```

## Test B – manuální swipe

Proveď 20 opakovaných scrollů podobnou rychlostí.

## Test C – fling

Krátký rychlý swipe a nechat seznam dojet.

Ověř, že jsou zachyceny eventy i po puštění prstu.

## Test D – horizontal

Carousel.

## Test E – změna směru

```text
down 1000 px
up 1000 px
```

Výsledek musí být přibližně:

```text
2000 px distance
```

ne 0.

---

# 37. Reálné aplikace pro test

Testuj minimálně:

- Chrome
- Instagram
- Facebook
- Reddit
- YouTube
- TikTok
- Google Play
- Google Maps seznamy / panely
- běžný systém Settings
- alespoň jedna Compose aplikace
- alespoň jedna WebView stránka

Pro každou vytvoř testovací tabulku:

```text
App
TYPE_VIEW_SCROLLED emitted?
scrollDeltaY usable?
fallback required?
outliers?
subjective coverage
notes
```

Důležitý výstup testu není jen „funguje/nefunguje“, ale kompatibilita jednotlivých implementací UI.

---

# 38. Accuracy metriky

Pro interní testy používej:

```text
absoluteErrorMm = abs(measuredMm - groundTruthMm)
```

```text
MAE = mean(absoluteError)
```

```text
percentageError = abs(measured-groundTruth) / groundTruth × 100
```

```text
MAPE = mean(percentageError)
```

Cíl pro vlastní testovací list:

```text
MAPE < 5 %
```

Ideální:

```text
< 2 %
```

U cizích aplikací není vždy dostupný ground truth, proto netvrď konkrétní přesnost bez měření.

---

# 39. Battery test

Test:

```text
1 hodina aktivního scrollování
8 hodin běžného používání
24 hodin normálního dne
```

Sleduj:

- CPU,
- wakeups,
- Room writes,
- počet eventů,
- battery usage.

Optimalizační priority:

1. poslouchat pouze `TYPE_VIEW_SCROLLED`,
2. žádné čtení node tree,
3. žádný screenshot,
4. žádné OCR,
5. bufferovaný zápis,
6. žádný permanentní polling,
7. žádný foreground loop.

---

# 40. Foreground service

NEVYTVÁŘEJ foreground service pouze proto, aby aplikace „běžela na pozadí“.

AccessibilityService má lifecycle řízený Android systémem a uživatel ho explicitně aktivuje.

Použij foreground service jen pokud se při implementaci objeví konkrétní technický důvod a předem ho zdokumentuj.

Výchozí architektura:

```text
AccessibilityService
       ↓
Measurement engine
       ↓
In-memory accumulator
       ↓
Room
```

---

# 41. WorkManager

WorkManager nepoužívej pro zachytávání scrollů.

Použít lze například pro:

- periodický maintenance,
- cleanup velmi starých debug dat,
- případnou přípravu denních souhrnů,
- widget refresh.

Primární agregace musí být event-driven.

---

# 42. UI design

Design:

- světlý i tmavý motiv,
- Material 3,
- moderní,
- čistý,
- minimum vizuálního chaosu,
- hlavní číslo velmi výrazné.

Inspirace:

- fitness tracker,
- Apple Health / Google Fit typ jednoduchosti,
- ne administrátorský dashboard.

Hlavní barva může být nastavena až v implementaci.

Používej dynamic color, pokud je dostupný, ale UI musí vypadat dobře i bez něj.

---

# 43. Navigace

Bottom navigation:

```text
Přehled
Historie
Aplikace
Nastavení
```

Maximálně 4 položky.

---

# 44. Settings

Sekce:

## Measurement

- stav Accessibility service
- kalibrace
- denní limit
- vyloučené aplikace

## Units

- Automatic
- metres
- kilometres

## Notifications

- daily goal
- daily summary

## Data

- export CSV
- delete all data

## Privacy

- privacy explanation
- Accessibility explanation

## About

- version
- device
- measurement method
- calibration scale

---

# 45. Delete all data

Musí být možné smazat:

- všechny naměřené hodnoty,
- historii,
- sessions,
- app aggregates.

Před smazáním confirm dialog:

```text
Opravdu smazat všechna naměřená data?
```

Kalibraci lze nabídnout:

```text
ponechat
```

nebo:

```text
smazat také nastavení
```

---

# 46. Co NEMÁ být v MVP

Nevytvářej nyní:

- uživatelské účty,
- server,
- cloud sync,
- social network,
- reklamní SDK,
- AI funkce,
- OCR,
- screenshot monitoring,
- VPN,
- root funkce,
- automatické blokování aplikací,
- overlay přes jiné aplikace,
- touch interception,
- vlastní keyboard,
- app blocker,
- složitou gamifikaci,
- databázi všech modelů telefonů.

MVP se musí soustředit na:

```text
MĚŘENÍ → PŘESNOST → DATA → PŘEHLED
```

---

# 47. Phase 2 – funkce, které dávají smysl až po ověření měření

Pokud je core measurement stabilní, přidat:

## Gamification

- daily streak,
- achievements,
- milestones,
- osobní rekordy.

## Challenges

Například:

```text
Pod 500 m denně 7 dní v řadě
```

## Comparisons

```text
Maraton
Praha centrum – letiště
výška Mount Everestu
```

U geografických vzdáleností používej pouze obecné statické reference, ne polohu uživatele.

## Widget

Denní stav.

## Backup/export

Lokální JSON / CSV.

## Optional cloud sync

Teprve pokud bude reálná uživatelská potřeba.

---

# 48. Funkce, kterou bych přidal oproti konkurenci – Coverage / compatibility test

Do Settings přidej:

> Otestovat kompatibilitu

Flow:

1. uživatel zvolí aplikaci nebo ji otevře,
2. ScrollMeter čeká na scroll event,
3. uživatel v dané aplikaci několik sekund scrolluje,
4. po návratu ScrollMeter ukáže:

```text
Instagram
✓ Scroll eventy detekovány
✓ Pixel delta dostupná
Kvalita měření: vysoká
```

nebo:

```text
Aplikace XYZ
✓ Scroll eventy detekovány
! Pixel delta není vždy dostupná
Kvalita měření: omezená
```

Toto je velmi užitečná diferenciace.

---

# 49. Funkce, kterou bych přidal oproti konkurenci – Accuracy panel

Uživatel může otevřít:

> Jak přesné je moje měření?

Zobraz:

```text
Kalibrace displeje: vysoká
Zdroj měření: Accessibility scroll delta
Podíl fallback eventů: 1,8 %
Rejected events: 0,02 %
```

Nedávej falešné celkové procento přesnosti.

---

# 50. Funkce, kterou bych přidal oproti konkurenci – „Scroll speed“

Počítej interně krátkodobou scrollovací rychlost.

Například v session:

```text
distanceMm / activeScrollDuration
```

Ale nezahrnuj běžné pauzy mezi eventy.

Lze později zobrazit:

```text
Nejrychlejší scroll session
Průměrné tempo scrollování
```

Toto není priorita MVP.

---

# 51. Funkce, kterou bych přidal oproti konkurenci – data ownership

Uživatel musí mít možnost exportovat data bez placení.

To je produktová výhoda:

> Tvoje scrollovací data patří tobě.

CSV export má být free.

---

# 52. Hlavní technické riziko

Největší riziko projektu není UI ani databáze.

Je to:

> různé aplikace reportují Accessibility scroll eventy rozdílně.

Proto postup implementace NESMÍ být:

```text
nejprve postavit krásnou celou aplikaci
a potom zjistit, jestli měření funguje.
```

Musí být opačný.

---

# 53. Povinný implementační postup pro Claude Code

## PHASE 0 – Project bootstrap

Vytvoř:

- Android project,
- Compose,
- Material 3,
- Room,
- DataStore,
- základní navigaci.

Ověř build.

---

## PHASE 1 – Measurement proof of concept

Nejprve vytvoř:

```text
AccessibilityService
Debug event viewer
Pixel distance calculator
```

Bez krásného dashboardu.

Musí být možné vidět:

```text
Chrome
dy: 138 px
distance: 8.31 mm
```

a další eventy live.

Ověř minimálně Chrome + Instagram + Reddit.

Pokud core measurement nefunguje, zastav vývoj dalších feature obrazovek a oprav measurement engine.

---

## PHASE 2 – Calibration

Implementuj:

1. DisplayMetrics scale
2. manual card calibration
3. test screen

Ověř reálným měřením.

---

## PHASE 3 – Persistence

Room + accumulator.

Ověř:

```text
restart app
restart screen
změna aplikace
změna dne
```

Data nesmí zmizet.

---

## PHASE 4 – Dashboard

Až potom:

- Today,
- Week,
- Month,
- Lifetime,
- Top apps.

---

## PHASE 5 – History + Apps

Grafy a breakdown.

---

## PHASE 6 – Export + settings

CSV, exclusions, goal, delete.

---

## PHASE 7 – Policy / onboarding

Finalizovat:

- prominent disclosure,
- privacy,
- accessibility setup,
- app listing podklady.

---

## PHASE 8 – Release hardening

Test:

- API 28,
- API 30,
- API 33,
- API 35,
- API 36.

Minimálně fyzicky nebo na emulatoru.

Measurement přes Accessibility musí být ověřen alespoň na několika fyzických telefonech.

---

# 54. Definition of Done – MVP

MVP je hotové pouze pokud splní všechny body:

- [ ] aplikace se sestaví bez chyb
- [ ] target API 36
- [ ] AccessibilityService lze aktivovat
- [ ] přijímá `TYPE_VIEW_SCROLLED`
- [ ] načítá package name
- [ ] načítá `scrollDeltaX/Y`
- [ ] počítá vzdálenost
- [ ] umí fallback přes scrollX/Y
- [ ] má outlier protection
- [ ] má automatickou kalibraci
- [ ] má ruční kalibraci platební kartou
- [ ] ukládá agregace do Room
- [ ] ukazuje Today
- [ ] ukazuje Week
- [ ] ukazuje Month
- [ ] ukazuje Lifetime
- [ ] ukazuje breakdown podle apps
- [ ] má history chart
- [ ] má exclusions
- [ ] má daily goal
- [ ] má CSV export
- [ ] má delete data
- [ ] má privacy screen
- [ ] má Google Play compliant disclosure flow
- [ ] nečte text z jiných aplikací
- [ ] nevyžaduje internet
- [ ] nevyžaduje účet
- [ ] nepoužívá QUERY_ALL_PACKAGES bez zásadního důvodu
- [ ] prošel testem Chrome
- [ ] prošel testem Instagram
- [ ] prošel testem Reddit
- [ ] prošel testem horizontal scroll
- [ ] prošel testem fling
- [ ] vlastní testovací screen má MAPE ideálně < 5 %
- [ ] po restartu aplikace zůstávají data zachována

---

# 55. Doporučený produktový claim

Preferovaný:

> **See how far you scroll.**

Česky:

> **Zjisti, kolik toho skutečně nascrolluješ.**

Další:

> **Metry místo minut.**

> **Screen time ti řekne jak dlouho. ScrollMeter ti ukáže jak daleko.**

To je silnější produktová pozice než snaha tvrdit „dráhu palce“.

---

# 56. Důležité technické komentáře, které mají zůstat v kódu

U measurement engine vlož komentář vysvětlující:

```text
Accessibility scroll delta represents content scroll displacement,
not the physical path travelled by the user's finger.

Fling/inertial scrolling is intentionally included.
```

U konverze fyzických rozměrů:

```text
densityDpi is logical Android UI density and is not used as the
primary physical-distance conversion.
```

U AccessibilityService:

```text
Do not expand requested accessibility capabilities without a
documented product need and privacy/policy review.
```

---

# 57. Povinné unit testy

Minimálně:

## DistanceCalculator

```text
dx=0, dy=1000
dx=1000, dy=0
dx=1000, dy=1000
negative dx
negative dy
zero movement
```

## Calibration

```text
85.6 mm / known pixel width
xdpi conversion
ydpi conversion
```

## Aggregation

```text
two events same app
two apps
date rollover
week aggregation
month aggregation
```

## Outlier validator

```text
normal event accepted
huge event rejected
zero event fallback
```

## Sessionization

```text
gap <60 s = same
gap >60 s = new
```

---

# 58. Příklad výpočtu pro test

Telefon:

```text
xdpi = 420
ydpi = 420
```

Potom:

```text
mmPerPx = 25.4 / 420
        = 0.0604761905 mm
```

Event:

```text
dx = 0
dy = 1200
```

Výsledek:

```text
distance = 1200 × 0.0604761905
         = 72.5714 mm
         = 7.257 cm
         = 0.07257 m
```

100 podobných eventů:

```text
7.257 m
```

Tento příklad vlož do unit testu s tolerancí floating point.

---

# 59. Příklad diagonálního eventu

```text
dx = 300 px
dy = 400 px

mmPerPxX = 0.06
mmPerPxY = 0.06
```

```text
dxMm = 18 mm
dyMm = 24 mm
```

```text
distance = sqrt(18² + 24²)
         = 30 mm
```

Nesčítej:

```text
18 + 24 = 42 mm
```

To by nadhodnocovalo diagonální pohyb.

---

# 60. Data migration

Room database musí mít definované version číslo.

Během prototypu lze používat destructive migration pouze v debug variantě.

Produkční build má používat reálné migrations.

Nepřidávej schema export jen jako formalitu – připrav databázi na další pole.

---

# 61. Crash safety

Accessibility callback nesmí spadnout kvůli jednomu špatnému eventu.

Parser musí být defensive.

Pokud event nelze zpracovat:

```text
ignore
increment diagnostics
```

Ne:

```text
throw -> service dies
```

Ve production buildu neukládej detailní obsah eventů.

---

# 62. Performance

V `onAccessibilityEvent()` nedělej:

- Room query,
- disk I/O,
- dlouhé výpočty,
- package scan,
- network request.

Callback má:

1. rychle parsovat primitivní data,
2. předat sample do coroutine/channel/actor pipeline,
3. okamžitě skončit.

---

# 63. Doporučený event pipeline

```text
Android
  ↓
AccessibilityEvent
  ↓
AccessibilityEventParser
  ↓
ScrollEventValidator
  ↓
FallbackTracker
  ↓
PhysicalScaleProvider
  ↓
ScrollDistanceCalculator
  ↓
ScrollAccumulator
  ↓
Room UPSERT
  ↓
Repository
  ↓
Flow
  ↓
Compose UI
```

Toto je preferovaná architektura.

---

# 64. Diagnostika kompatibility

U každého package ukládej metadata:

```text
lastMeasuredAt
hasDirectDelta
hasFallback
unmeasurableCount
outlierCount
```

Díky tomu lze později zobrazit:

```text
Instagram – good compatibility
App XYZ – limited compatibility
```

---

# 65. Co dělat při změně kalibrace

Důležitá otázka:

Pokud uživatel dnes změní `mmPerPx`, co se stane s historií?

Doporučení:

**Historická naměřená `distanceMm` data nepřepočítávej.**

U eventů / agregací ukládej distance vypočítanou kalibrací platnou v daný moment.

Nová kalibrace platí pouze pro nová data.

Důvod:

- historie zůstává stabilní,
- nemusíme ukládat všechny raw eventy,
- změna nastavení nepřepisuje minulost.

Můžeš uložit:

```text
calibrationVersion
```

do daily aggregate, pokud to architektura umožní.

---

# 66. Raw data retention

Produkčně neukládej každý jednotlivý scroll event dlouhodobě.

Ukládej agregace.

Výhody:

- privacy,
- menší databáze,
- výkon,
- export je přehlednější.

Raw event logging pouze:

```text
debug build
```

a ideálně pouze do RAM / explicitního testovacího exportu.

---

# 67. Accessibility sensitive events

Novější Android může některá accessibility data označit jako sensitive a omezit je pro služby, které nejsou `isAccessibilityTool`.

Architektura ScrollMeter na tom nesmí být závislá.

Potřebujeme pouze:

- `TYPE_VIEW_SCROLLED`,
- package name,
- scroll deltas.

Pokud některá aplikace událost neposkytne nebo ji systém omezí:

- nic neobcházej,
- označ aplikaci jako limited / unsupported,
- nepokoušej se použít screenshoty nebo jiná invazivní řešení.

---

# 68. Multi-touch, stylus a systémová gesta

MVP nemá tvrdit, že rozpoznává typ fyzického inputu.

Scroll event mohl vzniknout:

- prstem,
- dvěma prsty,
- stylusem,
- trackpadem,
- accessibility inputem,
- programatickým scrollováním uvnitř aplikace.

Pokud Android vyšle standardní scroll event, ScrollMeter ho může započítat.

Nevytvářej nepodloženou klasifikaci „palec / ukazováček / stylus“.

Systémová navigační gesta, která neposouvají obsah view, se nezapočítávají.

---

# 69. Refresh rate a touch sampling rate

Výpočet vzdálenosti nemá být přímo závislý na:

- 60 Hz,
- 90 Hz,
- 120 Hz,
- 144 Hz,
- touch sampling rate.

Měří se delta scroll pozice, ne počet vykreslených frameů.

Vyšší frekvence může ovlivnit počet eventů, ale součet delta hodnot by měl reprezentovat celkový posun.

Proto nikdy nepočítej:

```text
eventCount × průměrná vzdálenost
```

pokud je k dispozici scroll delta.

---

# 70. Co když stejný pohyb vyvolá více nested scroll eventů

Nested scrolling může potenciálně vytvořit scroll event na více úrovních UI.

Je nutné během POC ověřit, zda na konkrétních frameworkách nevzniká double counting.

Debug logger má proto ukládat:

```text
package
windowId
className
dx
dy
timestamp
```

Pokud se ukáže, že identický pohyb systematicky vytváří duplikované eventy se stejným delta v téměř stejném čase, implementuj opatrný deduplication mechanismus.

Výchozí návrh dedupe:

Event považuj za potenciální duplicate pouze pokud:

```text
same package
same windowId
same dx
same dy
timestamp difference <= 5 ms
```

Nezapínej dedupe naslepo.

Nejprve ho ověř debug daty, protože příliš agresivní dedupe by vedlo k podměření.

---

# 71. Produktové pořadí priorit

Při konfliktu priorit platí:

```text
1. správnost měření
2. stabilita
3. soukromí
4. nízká spotřeba
5. jednoduché UX
6. vizuální polish
7. gamifikace
```

Nikdy neobětuj měření kvůli efektnímu UI.

---

# 72. Co má Claude Code dělat při nejasnosti

Pokud během implementace narazíš na nejasné Android chování:

1. nejprve ověř aktuální oficiální Android dokumentaci,
2. napiš malý reprodukovatelný test,
3. preferuj méně invazivní API,
4. nedoplňuj data odhadem, pokud je lze raději označit jako chybějící,
5. zdokumentuj rozhodnutí v `docs/measurement-decisions.md`.

---

# 73. Dokumentace v repozitáři

Vytvoř:

```text
README.md
docs/
    measurement-model.md
    accessibility-policy.md
    accuracy-testing.md
    architecture.md
```

## measurement-model.md

Popiš:

- co je scroll distance,
- co není finger path,
- vzorce,
- kalibraci,
- fallback,
- fling.

## accessibility-policy.md

Popiš:

- jaké API používáme,
- proč,
- jaká data nečteme,
- Google Play disclosure.

## accuracy-testing.md

Tabulka výsledků zařízení / aplikací.

---

# 74. Doporučený README claim

```markdown
# ScrollMeter

ScrollMeter is an Android app that measures how far content moves while you
scroll across your phone and converts that movement into real-world distance.

It uses Android Accessibility scroll events and processes all measurement data
locally on the device.

ScrollMeter measures scroll distance, not the raw physical trajectory of a
finger across the touchscreen.
```

---

# 75. Release strategy

## První interní APK

Pouze:

- measurement engine,
- debug UI,
- calibration.

Otestovat na fyzickém telefonu.

## Alpha

- dashboard,
- history,
- apps,
- export.

Distribuce například interním testováním Google Play.

## Public release

Až po:

- kompatibilitních testech,
- privacy review,
- Accessibility declaration,
- policy review,
- Play listing disclosure.

---

# 76. Moje finální doporučení pro tento projekt

Projekt **má smysl stavět**, ale ne jako pouhou kopii Scrollscape.

Nejlepší produktová pozice je:

> **přesnější, transparentní, offline scroll-distance tracker s kalibrací a informací o kvalitě měření.**

Největší výhoda nebude „umíme spočítat metr“.

To už konkurence umí.

Výhoda má být:

```text
VÍŠ, JAK SE TO POČÍTÁ.
VÍŠ, JAK KVALITNÍ JSOU DATA.
DATA NEOPUSTÍ TELEFON.
MŮŽEŠ SI MĚŘENÍ FYZICKY ZKALIBROVAT.
MŮŽEŠ SI DATA EXPORTOVAT.
```

Pro MVP doporučuji neimplementovat blokování aplikací, overlays ani agresivní digital-wellbeing zásahy. Nejprve ověřit, že měření funguje konzistentně na hlavních aplikacích.

---

# 77. Aktuální ověřené Android skutečnosti

K září 2026:

- `TYPE_VIEW_SCROLLED` reprezentuje scroll view.
- Accessibility event může poskytovat `scrollDeltaX` a `scrollDeltaY`.
- `getScrollDeltaX/Y()` udává rozdíl pozice v pixelech a je dostupný od API 28.
- `DisplayMetrics.xdpi` a `ydpi` poskytují fyzickou hustotu pixelů v osách X/Y.
- `density` / `densityDpi` je logická Android hustota a nemá být primární fyzickou kalibrací.
- AccessibilityService běží jako systémem spravovaná služba po explicitním povolení uživatelem.
- Aplikace, která není skutečný accessibility tool, nemá nastavovat `isAccessibilityTool=true`.
- Google Play vyžaduje pro jiné použití Accessibility API disclosure, souhlas uživatele a deklaraci v Play Console.
- Od 31. 8. 2026 nové Android aplikace a aktualizace na Google Play musí cílit na Android 16 / API 36.

---

# 78. Relevantní oficiální dokumentace

Android AccessibilityEvent:

https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent

Android AccessibilityRecord:

https://developer.android.com/reference/android/view/accessibility/AccessibilityRecord

Android AccessibilityService:

https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

Android DisplayMetrics:

https://developer.android.com/reference/android/util/DisplayMetrics

Google Play – AccessibilityService API:

https://support.google.com/googleplay/android-developer/answer/10964491

Google Play – target API requirements:

https://support.google.com/googleplay/android-developer/answer/11926878

Package visibility:

https://developer.android.com/training/package-visibility

---

# 79. Instrukce Claude Code pro způsob práce

Nezačínej generováním celého projektu najednou bez validace.

Postupuj iterativně.

Po každé fázi:

1. spusť build,
2. oprav compile errors,
3. spusť relevantní unit testy,
4. shrň, co bylo implementováno,
5. uveď otevřená rizika,
6. pokračuj další fází pouze pokud základ funguje.

Pokud máš přístup k Android SDK / emulatoru:

- buildni projekt,
- spusť testy,
- zkontroluj Logcat.

Pokud je připojeno fyzické zařízení:

- použij jej pro Accessibility POC.

Nevytvářej pouze mock UI.

Výsledkem musí být skutečně spustitelná Android aplikace.

---

# 80. První úkol pro Claude Code

Začni pouze **Phase 0 + Phase 1**.

Konkrétně:

1. založ Android projekt,
2. nastav API 36 / min API 28,
3. vytvoř jednoduchou Compose `MainActivity`,
4. implementuj `ScrollAccessibilityService`,
5. nakonfiguruj službu pouze na `TYPE_VIEW_SCROLLED`,
6. vytvoř measurement parser,
7. vytvoř `ScrollDistanceCalculator`,
8. načti `xdpi/ydpi`,
9. vytvoř debug obrazovku posledních scroll eventů,
10. ukaž live:
   - package,
   - dx,
   - dy,
   - mm,
   - accepted/rejected,
11. přidej tlačítko pro otevření Accessibility settings,
12. ověř build,
13. napiš README s návodem, jak POC ručně otestovat.

**Neimplementuj ještě gamifikaci, historii ani finální design.**

Nejdřív potřebujeme odpovědět na jedinou kritickou otázku:

> Dokážeme na reálném telefonu konzistentně a dostatečně přesně získat scroll delta data z hlavních aplikací?

Teprve pokud je odpověď ano, pokračuj Phase 2–8.
