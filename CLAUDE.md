# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & test commands

```bash
# Compile only (fast feedback)
./gradlew :app:compileDebugKotlin

# Full debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run all unit tests
./gradlew testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.tkolymp.napect.data.ai.openai.OpenAiServiceTest"

# Lint
./gradlew :app:lintDebug
```

The `namespace` is `com.tkolymp.napect` but the `applicationId` is `com.tobiso.napect` — this divergence is intentional; don't "fix" it.

Room schema files are exported to `app/schemas/` via the `ksp { arg("room.schemaLocation", ...) }` block. Migration tests use these snapshots.

## Architecture

Single-module Android app. Kotlin + Jetpack Compose + Material 3. Hilt DI throughout.

### Layer overview

```
ui/          — Compose screens + @HiltViewModel
domain/      — Use cases + repository interfaces + domain models
data/        — Implementations: Room, DataStore, OkHttp, WorkManager, AI
di/          — AppModule.kt (single Hilt module)
```

Top-level package files (not inside sub-packages): `NapectApp.kt` (root Composable + NavHost), `MainActivity.kt`, `SettingsScreen.kt`, `AppDestinations.kt`, `AppNavBar.kt`, `FeatureFlags.kt`.

### Dependency injection

All bindings live in `di/AppModule.kt`. Two OkHttpClient instances:
- Default (15 s timeouts) — used by `UrlImportService` for HTML fetching.
- `@Named("openai")` (30 s connect / 60 s read) — used by `OpenAiService`.

`RepositoryModule` (inside `data/`) provides Room-backed repository bindings.

### AI / URL import flow

`AiClient` interface is the app's single AI abstraction. `DefaultAiClient` orchestrates:
1. Check `UrlImportCache` (LRU, 20 entries, in-memory).
2. If `OpenAiKeyProvider.getKey()` is non-null → fetch HTML via `UrlImportService.fetchHtml()` → send to `OpenAiService.extractRecipeFromHtml()`.
3. On any failure or absent key → fall back to `UrlImportService.importFromUrl()` (JSON-LD parser).

`OpenAiService` uses Chat Completions (`POST /v1/chat/completions`) with `response_format: json_schema` + `strict: true`. Retry policy: 429 → respect `Retry-After` header, retry once; 5xx → 1 s delay, retry once; 4xx → fail immediately.

The OpenAI API key is stored in `EncryptedSharedPreferences` via `OpenAiKeyStore`. Model and base URL live in DataStore via `SettingsRepository : OpenAiConfig`.

Background URL imports run through `UrlImportWorker` (`@HiltWorker`), which calls `ai.extractRecipeFromUrl(url)`.

### Testability pattern

`OpenAiService` takes `OpenAiKeyProvider` and `OpenAiConfig` interfaces (not concrete Android classes) so it can be tested with anonymous `object :` fakes in JVM unit tests. Tests use `MockWebServer` (no Robolectric). **Do not name a property `key` in an `OpenAiKeyProvider` anonymous object — it causes a JVM declaration clash with `getKey()`; use `apiKey` or similar.**

`UrlImportService` is an `open class` with `open` methods so `DefaultAiClientFallbackTest` can subclass it with a fake without Android context.

`FakeAiClient` lives in `test/.../domain/usecase/FakeAiClient.kt` — update it when the `AiClient` interface changes.

No Mockk in the project — use plain fakes and `runTest`.

### Settings

`SettingsRepository` manages DataStore preferences (theme, servings, screenshot protection, OpenAI model/base URL) and implements `OpenAiConfig`. `SettingsViewModel` additionally injects `OpenAiKeyStore` and `OpenAiService` for key management and connection testing.

### Backup rules

`res/xml/backup_rules.xml` excludes `napect_secure_prefs.xml` and `napect_secure_prefs_fallback.xml` — the encrypted key store must never be cloud-backed.
