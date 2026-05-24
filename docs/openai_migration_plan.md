# Migration Plan: URL Import & AI from Gemini Nano → OpenAI API

**Status:** Draft
**Owner:** TBD
**Target branch:** `feat/openai-migration`
**Related docs:** [main_goal.md](main_goal.md), [AUDIT.md](../AUDIT.md)

---

## 1. Goal

Replace the on-device Gemini Nano (reflection-based, mostly stub) path with a cloud-based **OpenAI Chat Completions API** integration that handles:

1. **URL import** — fetch page HTML, hand off to OpenAI to extract structured recipe data (title, ingredient groups, steps, summary, difficulty, source) as JSON.
2. **Summarization** — generate the 2–3-sentence Czech summary for recipes.
3. **Difficulty inference** — replace the keyword-regex heuristic in `RecipeRepositoryImpl.suggestTagsForRecipe`.
4. **(Optional, Phase 2)** OCR/Vision flow for shared images via `gpt-4o`-family vision models.

The user supplies their own OpenAI API key in **Settings**. The key is stored locally in encrypted DataStore; nothing leaves the device unless the user actively imports/summarizes.

---

## 2. Architectural impact (READ FIRST)

The current app is positioned as **fully offline** ([main_goal.md:7](main_goal.md), [project_overview.md:8](../memory/project_overview.md)). Adopting OpenAI breaks that contract for AI-driven flows. Decisions to confirm before coding:

| Topic | Recommendation |
|---|---|
| Privacy story | Update `main_goal.md`: "AI features optional, require user-supplied OpenAI API key; otherwise local fallback (JSON-LD parser + rule-based classifier)." |
| Default behavior on fresh install | OpenAI disabled; user opts in by entering an API key in Settings. App remains fully usable without it (existing fallback path stays as the default). |
| Model | Default `gpt-4o-mini` (cheap, fast, supports JSON mode); allow user to override in Settings. |
| Cost | One recipe import ≈ 1–3 K input tokens + ~500 output tokens ≈ < $0.001 with `gpt-4o-mini`. Document this in Settings. |
| Network failures | Always fall back to the existing `UrlImportService` JSON-LD parser + rule-based summarizer so the app degrades gracefully. |
| Key safety | Store via `EncryptedSharedPreferences` (or DataStore with manual `Tink` encryption). **Never** log the key. Redact from Timber. |

---

## 3. Inventory of touched files

### Delete / replace
| File | Action |
|---|---|
| `app/src/main/java/com/tkolymp/napect/data/ai/GeminiNanoService.kt` | Delete (or shrink to a no-op `LocalFallbackSummarizer` if the heuristics are still useful as fallback). |

### Heavily modified
| File | Reason |
|---|---|
| `data/ai/AiClient.kt` | `DefaultAiClient` rewritten to consume `OpenAiService`; interface gains `extractRecipeFromHtml(html, sourceUrl)` so the worker can call it. |
| `di/AppModule.kt` | Provide `OpenAiService`, drop `provideGeminiService`, update `provideAiClient`. Add provider for the encrypted key store. |
| `data/work/UrlImportWorker.kt` | Branch: if key present → `openAiService.extractRecipeFromUrl(url)`; else fall back to `UrlImportService`. |
| `ui/recipes/UrlImportViewModel.kt` | Drop `gemini` constructor param; route through `AiClient` only. `fetchUrl` follows same fallback logic. |
| `MainActivity.kt` | Remove `@Inject lateinit var geminiService: GeminiNanoService`. |
| `data/local/SettingsRepository.kt` | Add OpenAI key + model + base URL prefs (encrypted-backed). |
| `ui/settings/SettingsViewModel.kt` | Expose key/model state + setters. |
| `SettingsScreen.kt` | UI for API key entry (masked field, paste/clear), model dropdown, "Test connection" button. |
| `res/values/strings.xml` | New keys: section header, hint, validation messages, "Test connection", privacy disclaimer. |
| `app/build.gradle.kts` + `gradle/libs.versions.toml` | Add `androidx.security:security-crypto` (or `Tink`); add `kotlinx-serialization-json` + plugin if not already present. |
| `docs/main_goal.md`, `memory/project_overview.md` | Update wording — Gemini Nano references → "optional OpenAI integration". |

### New files
| File | Purpose |
|---|---|
| `data/ai/openai/OpenAiService.kt` | Thin OkHttp client targeting `POST /v1/chat/completions` with JSON mode + retry. |
| `data/ai/openai/OpenAiModels.kt` | `@Serializable` request/response DTOs. |
| `data/ai/openai/OpenAiKeyStore.kt` | `EncryptedSharedPreferences`-backed accessor (`getKey()`, `setKey()`, `clear()`). |
| `data/ai/openai/RecipePrompt.kt` | Single source of truth for the extraction/summary prompts (Czech-first). |
| `app/src/test/.../OpenAiServiceTest.kt` | Tests with MockWebServer covering: success, malformed JSON, 401, 429 retry, timeout. |
| `app/src/test/.../DefaultAiClientFallbackTest.kt` | When key absent → uses local summarizer; when OpenAI throws → falls back. |

---

## 4. Phased work plan

### Phase 0 — Decisions & prep (½ day)
- [ ] Confirm architectural changes in §2 with stakeholder; update `main_goal.md`.
- [ ] Pick crypto lib: `androidx.security:security-crypto` 1.1.0-alpha (lightweight, deprecated but still works on minSdk 24) **vs** Tink direct. Recommend `security-crypto` to stay small.
- [ ] Add version-catalog entries: `openaiApiVersion` is N/A (no SDK), but add `securityCrypto`, `kotlinxSerialization`, `mockwebserver`.

### Phase 1 — Foundation (1 day)
- [ ] Add deps & sync Gradle.
- [ ] Create `OpenAiKeyStore` with `EncryptedSharedPreferences` (file: `napect_secure_prefs`).
- [ ] Extend `SettingsRepository`:
  - `openAiApiKey: Flow<String?>` (delegates to key store, not DataStore).
  - `openAiModel: Flow<String>` (DataStore key, default `"gpt-4o-mini"`).
  - `openAiBaseUrl: Flow<String>` (DataStore key, default `"https://api.openai.com/v1/"`). Supports self-hosted proxies.
- [ ] Settings UI:
  - Masked `OutlinedTextField` with show/hide toggle.
  - Model dropdown (`gpt-4o-mini`, `gpt-4o`, `gpt-5-mini`, custom).
  - "Test connection" button → calls `models` endpoint or a 1-token completion; shows `Snackbar`.
  - Disclaimer string explaining data leaves the device.
- [ ] Strings: add all new keys to `res/values/strings.xml`.

### Phase 2 — OpenAI client (1 day)
- [ ] `OpenAiModels.kt` — `ChatRequest`, `ChatMessage`, `ResponseFormat`, `ChatResponse`, `ChatChoice`, `Usage`, `ExtractedRecipe`.
- [ ] `OpenAiService.kt`:
  - Reuses the injected `OkHttpClient` (already has 15s timeouts in [AppModule.kt:27](../app/src/main/java/com/tkolymp/napect/di/AppModule.kt)).
  - `suspend fun extractRecipeFromUrl(url: String): Result<ImportedRecipeData>` — fetches HTML via existing `UrlImportService` (HTTP path), then `extractRecipeFromHtml`.
  - `suspend fun extractRecipeFromHtml(html: String, sourceUrl: String?)` — sends a `chat/completions` call with `response_format = { type: "json_schema", json_schema: {...} }`. Trims/cleans HTML to <30 K chars before sending.
  - `suspend fun summarize(title, ingredients, steps): String?`
  - `suspend fun inferDifficulty(...): String?`
  - Retry: 1 retry on 5xx/429 (respect `Retry-After`); fail-fast on 4xx other than 429.
  - **Never** log Authorization header; pass key via `Request.Builder().header("Authorization", "Bearer $key")` only.
- [ ] `RecipePrompt.kt` — system prompt instructs Czech output, structured JSON, no commentary. Includes the JSON schema for `ExtractedRecipe`.

### Phase 3 — Wire into existing flows (½ day)
- [ ] Refactor `AiClient` interface:
  ```kotlin
  interface AiClient {
      suspend fun extractRecipeFromUrl(url: String): Result<ImportedRecipeData>
      suspend fun generateSummary(...): String?
      suspend fun inferDifficulty(...): String?
  }
  ```
- [ ] `DefaultAiClient(openAi: OpenAiService, keyStore: OpenAiKeyStore, fallback: UrlImportService)`:
  - If `keyStore.getKey()` is null/blank → delegate to `fallback` + local summarizer (existing logic moved out of `GeminiNanoService`).
  - Else call `openAi.*`; on any exception → fall back.
- [ ] Update `UrlImportWorker` to take `AiClient` instead of `GeminiNanoService`.
- [ ] Update `UrlImportViewModel`:
  - Remove `gemini: GeminiNanoService` ctor param.
  - `fetchUrl` calls `ai.extractRecipeFromUrl(url)` (which internally handles fallback).
- [ ] Update `MainActivity` (drop unused `geminiService` field).
- [ ] Delete `GeminiNanoService.kt`. Move any reusable summarizer text into `DefaultAiClient` as private functions.

### Phase 4 — Tests (1 day)
- [ ] `OpenAiServiceTest` with `MockWebServer`: success path, schema parse failure, 401 → surface as `Result.failure` (no retry), 429 with `Retry-After: 1` → retried once, 500 → retried once, timeout.
- [ ] `DefaultAiClientFallbackTest`: key absent → fallback used; OpenAI throws → fallback used; both succeed → OpenAI result preferred.
- [ ] Update `FakeAiClient` for new `extractRecipeFromUrl` method.
- [ ] Update `UrlImportViewModelTest` (if it exists) and `PrepareAndSaveRecipeUseCaseTest`.
- [ ] Smoke instrumentation test: enter dummy key, attempt import against MockWebServer, verify Snackbar shows error gracefully.

### Phase 5 — Docs & cleanup (½ day)
- [ ] Update `main_goal.md` AI section.
- [ ] Update `memory/project_overview.md` stack list (drop Gemini Nano).
- [ ] Update `AUDIT.md` if any audit items become obsolete (e.g., §4.2 — TLS pinning becomes more relevant; consider pinning OpenAI cert).
- [ ] Add a `README.md` blurb on how to obtain an API key.

---

## 5. JSON schema for extraction

Used as `response_format.json_schema` so OpenAI returns parseable output (no markdown fences). Mirrors `ImportedRecipeData` in [ImportedRecipeData.kt](../app/src/main/java/com/tkolymp/napect/data/network/ImportedRecipeData.kt):

```json
{
  "name": "imported_recipe",
  "strict": true,
  "schema": {
    "type": "object",
    "additionalProperties": false,
    "required": ["title", "ingredientGroups", "steps"],
    "properties": {
      "title": { "type": "string" },
      "description": { "type": ["string", "null"] },
      "ingredientGroups": {
        "type": "array",
        "items": {
          "type": "object",
          "additionalProperties": false,
          "required": ["name", "ingredients"],
          "properties": {
            "name": { "type": "string" },
            "ingredients": { "type": "array", "items": { "type": "string" } }
          }
        }
      },
      "steps": { "type": "array", "items": { "type": "string" } },
      "difficulty": { "type": ["string", "null"], "enum": ["Jednoduché", "Střední", "Náročné", null] }
    }
  }
}
```

## 6. System prompt (sketch, Czech)

> Jsi extraktor receptů. Z přiloženého HTML/textu extrahuj recept v češtině jako JSON podle daného schématu. Pokud chybí pole, vrať prázdné pole nebo null. Nepřidávej komentáře. Ingredience seskup do logických sekcí (těsto, krém, ozdoba…). Krok = jedna instrukce. Náročnost odhadni z počtu kroků a ingrediencí.

## 7. Risk & rollout

- **Privacy regression** — surface a one-time dialog after key entry: "Importy receptů budou odesílány na OpenAI." Allow disabling per import (checkbox in URL import sheet).
- **API key leakage** — `EncryptedSharedPreferences`, never include in Timber logs, scrub from stack traces in error Snackbars.
- **Network outage** — fallback path covers it; surface `Snackbar` with reason.
- **Cost runaway** — add an optional per-day token budget in Settings (Phase 6, not in initial scope).
- **Rollback** — keep `GeminiNanoService.kt` for one release behind a `FeatureFlags.OPENAI_IMPORT` flag if conservative rollout is desired.

## 8. Acceptance criteria

- [ ] App still installs and runs with no API key; all existing flows work (URL import falls back to JSON-LD parser).
- [ ] With a valid `sk-…` key in Settings, importing a non-JSON-LD blog URL produces a fully populated `ImportedRecipeData`.
- [ ] Invalid key → user-visible error in Snackbar; no crash; key not logged.
- [ ] Network failure → falls back to JSON-LD parser silently; user sees normal Success state.
- [ ] All existing unit tests pass; new tests cover the OpenAI service paths.
- [ ] `GeminiNanoService.kt` deleted (or reduced to fallback heuristics class); no remaining references to Gemini/Gemma in code or docs.

## 9. Out of scope

- Streaming responses (not needed for one-shot extraction).
- Function calling / tool use.
- Image (Vision API) OCR — kept on ML Kit for now; revisit in a follow-up if extraction quality is poor.
- Voice input transcription via Whisper — current `SpeechRecognizer` is good enough.
- Multi-provider abstraction (Anthropic, Google AI Studio). Add only if a second provider is requested.
