# Napéct 🍞
**Projektová specifikace · Android only · lokální data · v3.0**

---

## Přehled

Plně lokální Android appka pro ukládání a procházení receptů. Veškerá data zůstávají na zařízení — žádný server, žádný backend, žádné přihlášení. Sdílíš odkaz nebo soubor → appka zpracuje vše sama.

---

## Datové úložiště

| Co | Kde |
|---|---|
| Recepty, ingredience, kroky, kategorie | Room DB (SQLite) |
| Fotky receptů | Room DB (BLOB) |
| Nastavení aplikace | DataStore Preferences |

---

## AI — Gemini Nano (on-device)

Vše probíhá offline přímo na zařízení.

- **Automatická kategorizace** — po uložení receptu přiřadí kategorii (dezert, polévka…)
- **Sumarizace** — krátký popis receptu (2–3 věty)
- **Strukturování ingrediencí** — parsování textu → tabulka s množstvím a jednotkami
- **Import z URL** — stáhne stránku, Gemini Nano extrahuje název, ingredience, postup
- **OCR fotek** — přečte naskenovaný papírový recept (ML Kit)

> **Poznámka:** Gemini Nano vyžaduje Android AICore API — dostupné na Pixel 8+ a vybraných zařízeních s Androidem 10+.

### Automatické kategorie

Polévky · Hlavní jídla · Dezerty · Pečení · Snídaně · Svátky · Rychlé (pod 30 min) · Bezlepkové / diety

---

## Způsoby přidání receptu

| Způsob | Jak to funguje |
|---|---|
| Sdílet odkaz | Android share sheet z prohlížeče → appka otevře import URL obrazovku a stáhne stránku (JSON-LD parsing), uživatel může zkontrolovat a uložit (implementováno) |
| Sdílet soubor / foto | Share sheet → ML Kit OCR + Gemini Nano strukturuje text |
| Foto papíru | CameraX v aplikaci → ML Kit OCR + Gemini Nano strukturuje |
| Ručně | Formulář — název, ingredience, kroky, foto z galerie nebo fotoaparátu |
| Hlasový vstup | Speech-to-text přes Android API → Gemini Nano strukturuje |

---

## Zobrazení receptu

| Prvek | Detail |
|---|---|
| Ingredience | Tabulka: množství · jednotka · ingredience — škálovaná na počet porcí |
| Škálování porcí | +/– tlačítka → všechna množství se přepočítají v reálném čase |
| Pracovní postup | Číslované kroky, přehledné jako v kuchařce |
| Shrnutí | Gemini Nano — 2–3 věty automaticky generovaného popisu |
| Foto | Titulní fotka uložená v Room DB jako BLOB |
| Zdroj | Odkaz na původní URL nebo poznámka o původu |

---

## Funkce aplikace

### MVP

| Funkce | Detail |
|---|---|
| Přidání receptu | Odkaz, foto, soubor, ručně — ruční přidání podporuje název, ingredience a kroky (implementováno), foto z galerie (implementováno) |
| AI kategorizace | Automaticky po uložení, on-device (rule-based classifier implemented as fallback) |
| Sumarizace + ingredience | Sumarizace: neimplementováno; Strukturování ingrediencí: UI pro zadání implementováno |
| Škálování porcí | +/– v detailu receptu, přepočet ingrediencí (implementováno) |
| Procházení & vyhledávání | Fulltext + filtr dle kategorie (základní vyhledávání v UI implementováno) |
| Oblíbené ⭐ | Označit recept jako oblíbený (implementováno) |

### Po MVP

| Funkce | Detail |
|---|---|
| Poznámky k receptu | Vlastní poznámka ("dala jsem méně cukru") |
| Verzování | Historie úprav receptu |
| Exportovat / sdílet | Sdílet recept jako text nebo PDF |
| Týdenní plánování | Nákupní seznam z vybraných receptů |
| Sezónní doporučení | Gemini Nano — "co uvařit teď v květnu?" |

---

## Tech stack

| Vrstva | Technologie |
|---|---|
| UI | Jetpack Compose |
| Databáze | Room (SQLite + BLOB) |
| AI | Gemini Nano (MediaPipe / AICore) |
| OCR | ML Kit Text Recognition |
| Fotoaparát | CameraX |
| Dependency injection | Hilt |
| Síť (jen import z URL) | Ktor Client / Retrofit |
| Nastavení | DataStore Preferences |

---

## Roadmapa

### Fáze 1 — MVP `3–4 týdny`
Room DB schéma · CRUD receptů · Jetpack Compose UI · Gemini Nano kategorizace + sumarizace + ingredience · Import z URL · Fulltext vyhledávání · Škálování porcí · Oblíbené

### Fáze 2 — Přidávání `+2 týdny`
Share sheet (odkaz + soubory) · CameraX foto papíru + OCR · Hlasový vstup · Fotky receptů (BLOB)

### Fáze 3 — Komfort `+2 týdny`
Poznámky k receptu · Verzování · Export / sdílení receptu · Tmavý / světlý režim

### Fáze 4 — Extras `průběžně`
Týdenní plánování + nákupní seznam · Sezónní doporučení (Gemini Nano) · Statistiky
