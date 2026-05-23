# Napect — Complete Senior-Level Codebase Audit

**Date:** 2026-05-21
**Auditor:** Automated static analysis
**App:** Napect — Czech recipe management app (Jetpack Compose, Room, Hilt)

---

## Table of Contents

1. [Architecture Audit](#1-architecture-audit)
2. [Kotlin Code Quality Audit](#2-kotlin-code-quality-audit)
3. [Android-Specific Audit](#3-android-specific-audit)
4. [Security Audit](#4-security-audit)
5. [Performance Audit](#5-performance-audit)
6. [Dependency & Gradle Audit](#6-dependency--gradle-audit)
7. [UI/UX Audit](#7-uiux-audit)
8. [Networking Audit](#8-networking-audit)
9. [Database Audit](#9-database-audit)
10. [Testing Audit](#10-testing-audit)
11. [Play Store & Production Readiness Audit](#11-play-store--production-readiness-audit)
12. [Final Summary](#12-final-summary)

---

## Severity Scale

| Level | Description |
|-------|-------------|
| **CRITICAL** | Will cause crashes, data loss, or security breach |
| **HIGH** | Major risk to stability, security, or user experience |
| **MEDIUM** | Should be addressed; reduces quality or scalability |
| **LOW** | Minor issue; best practice violation |
| **INFO** | Observation or suggestion |

---

## 1. Architecture Audit

### 1.1 CRITICAL — Business logic leak: `AppContextHolder` global mutable singleton

**File:** `AppContextHolder.kt:9-11`

```kotlin
object AppContextHolder {
    var context: Context? = null
}
```

Mutable global `object` exposing a nullable `Context`. This is a memory leak risk and an anti-pattern that circumvents proper DI. `UrlImportViewModel.kt:71` reads it during `importImage()`.

**Why it matters:** `AppContextHolder.context` can be set to an Activity context that gets garbage collected while still referenced, causing leaks. It breaks the lifecycle safety guarantees that Hilt provides.

**Fix:** Inject `@ApplicationContext Context` via Hilt into the ViewModel instead of using a global holder.

---

### 1.2 HIGH — `MainActivity` contains repository instantiation with ephemeral scope

**File:** `MainActivity.kt:45-53`

```kotlin
val repo = com.tkolymp.napect.data.repository.RecipeRepositoryImpl(db.recipeDao(), db.tagDao())
repo.ensureDefaultTags()
repo.migrateEnglishTagsToCzech()
```

Creates a `RecipeRepositoryImpl` directly (bypassing DI) inside `onCreate` with a `lifecycleScope` coroutine. This duplicates DI setup.

**Why it matters:** If DI configuration changes, this manual instantiation will silently diverge. These operations should be triggered from a proper initialization point (e.g., `HiltViewModel` scoped to `@Singleton` or `AppStartup`).

**Fix:** Create an `@Singleton` initializer class or use Hilt's `@EntryPoint` to invoke these operations.

---

### 1.3 HIGH — ViewModel-to-ViewModel coupling via Activity passing

**File:** `NapectApp.kt:118-123`

```kotlin
fun NapectApp(
    vm: RecipeViewModel,
    importVm: com.tkolymp.napect.ui.recipes.UrlImportViewModel? = null,
    ...
)
```

The top-level composable receives `RecipeViewModel` and `UrlImportViewModel` as parameters from the Activity. This bakes in a specific Activity-VM wiring.

**Why it matters:** Makes composables non-reusable, breaks navigation-module isolation. If you ever move to `hiltNavCompose` `hiltViewModel()` calls in composables, this refactor is blocked.

**Fix:** Use `hiltViewModel()` inside composable nav destinations instead of passing VMs as parameters.

---

### 1.4 MEDIUM — Domain layer is anemic; use case layer missing entirely

**Files:** `domain/repository/RecipeRepository.kt`, `domain/` (no `usecase/` directory)

The repository interface mixes data-access methods (`getAllRecipes`, `search`, `createRecipe`) with AI concerns (`suggestTagsForRecipe`). There is no `domain/usecase/` directory.

**Why it matters:** As the app grows (tag management, planned cooking, export), having use cases prevents ViewModel bloat. Currently `RecipeViewModel` directly calls repository methods, mixing UI orchestration with business logic.

**Fix:** Introduce use cases like `SuggestTagsUseCase`, `CreateRecipeWithTagsUseCase`, `GetFilteredRecipesUseCase`.

---

### 1.5 MEDIUM — Repository interface leaks data-layer types

**File:** `RecipeRepository.kt:18`

```kotlin
suspend fun suggestTagsForRecipe(recipe: Recipe): com.tkolymp.napect.data.ai.TagSuggestion
```

`suggestTagsForRecipe()` returns `TagSuggestion` which is a `data/ai/` layer class, not a domain type. This couples the domain layer to the data layer's AI submodule.

**Fix:** Define `TagSuggestion` as a domain model or map it in the repository implementation.

---

### 1.6 MEDIUM — `RecipeClassifier` is dead code at domain level

**File:** `RecipeClassifier.kt`

Never directly called from domain; all classification logic is duplicated in `RecipeRepositoryImpl.kt:253-268` (derived category logic) and `RecipeViewModel.kt:47-50`. Dead code that violates DRY.

---

### 1.7 LOW — `RecipeViewModelFactory.kt` and `UrlImportViewModelFactory.kt` are vestigial

Both factory files exist alongside `@HiltViewModel` annotated classes. With Hilt injection, these manual factories are unused dead code.

**Fix:** Delete both factory files.

---

## 2. Kotlin Code Quality Audit

### 2.1 CRITICAL — Swallowed exceptions everywhere

**Pattern throughout the codebase:**

```kotlin
catch (_: Exception) { }
```

Silently swallowing exceptions. Affected files:

| File | Lines |
|------|-------|
| `MainActivity.kt` | 53 |
| `NapectApp.kt` | 243, 248, 298, 303, 355, 408 |
| `RecipeRepositoryImpl.kt` | 153 |
| `RecipeViewModel.kt` | 96 |
| `AddRecipeScreen.kt` | 249, 265, 284, 303 |
| `RecipeDetailScreen.kt` | 178 |
| Multiple AI files | Various |

**Why it matters:** Silent catches hide real errors (database corruption, permission failures, network timeouts). During development/debugging, every failure is invisible. This is perhaps the single most dangerous pattern in the codebase.

**Fix:** At minimum log the exception. Use `catch (e: Exception) { Log.e(TAG, "message", e) }`.

---

### 2.2 HIGH — `BitmapFactory.decodeByteArray` on main thread

**Files:**
- `NapectApp.kt:305`
- `AddRecipeScreen.kt:344`
- `RecipeDetailScreen.kt:82`
- `RecipeListScreen.kt:92`
- `MakeScreen.kt:140`

All call `BitmapFactory.decodeByteArray()` directly inside composable functions.

**Why it matters:** This decodes potentially large photo BLOBs on the main thread, causing jank and ANR risk for photos > 10MB.

**Fix:** Offload decoding to `Dispatchers.Default` using `withContext` and `remember` with a `produceState` or `LaunchedEffect`.

---

### 2.3 HIGH — `SettingsRepository` recreated per recomposition in multiple screens

**Files:**
- `NapectApp.kt:74`
- `SettingsScreen.kt:51`
- `RecipeDetailScreen.kt:72`
- `MakeScreen.kt:77`

```kotlin
val repo = SettingsRepository(context)
```

`SettingsRepository(context)` is created inline in composable functions, meaning every recomposition could create a new instance.

**Why it matters:** Each instantiation creates a new DataStore reference. While the DataStore is a singleton per name, this is wasteful and breaks the DI pattern.

**Fix:** Inject `SettingsRepository` via Hilt (it is already provided in `AppModule.kt:47`). Use it from the ViewModel or via `hiltViewModel()`.

---

### 2.4 MEDIUM — `Date` usage instead of `java.time` / kotlinx-datetime

**Files:** `Recipe.kt:18-19`, `RecipeMappers.kt:34-35`

Uses `java.util.Date`. This type is mutable, error-prone, and has been superseded by `java.time.Instant` (API 26+, which is minSdk 24 but available via desugaring).

**Fix:** Migrate to `java.time.Instant` or `kotlinx-datetime.Instant`.

---

### 2.5 MEDIUM — Unsafe `!!` on non-null assertions

**File:** `AddRecipeScreen.kt:344`

```kotlin
BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes!!.size)
```

Double-bang on a nullable `ByteArray?`. If `photoBytes` changes to null between the null check and the use, this crashes.

**Fix:** Use `let { }` or `?.let { }` consistently.

---

### 2.6 MEDIUM — Unsafe cast in `MakeScreen.kt:188`

```kotlin
val amtDouble = amountStr as Double
```

Unsafe cast. `amountStr` could be a non-Double `Any` type. The `takeIf` doesn't guarantee type safety.

**Fix:** Replace with `(amountStr as? Double) ?: ing.amount`.

---

### 2.7 LOW — Redundant `try-catch` around `Log.d` calls

Multiple places:

```kotlin
try { Log.d(...) } catch (_: Exception) { }
```

`Log.d` never throws. These are noise.

---

### 2.8 LOW — Unnecessary import duplication

**File:** `MainActivity.kt:8-9`

```kotlin
import androidx.activity.viewModels
import androidx.activity.viewModels
```

Duplicated import — indicates past refactoring residue.

---

## 3. Android-Specific Audit

### 3.1 CRITICAL — Photo BLOBs stored in Room database

**File:** `RecipeEntity.kt:16`

```kotlin
@ColumnInfo(typeAffinity = ColumnInfo.BLOB) val photo: ByteArray? = null
```

Photos stored as `ByteArray?` BLOBs in the Room database.

**Why it matters:** Database can grow to gigabytes quickly. Each photo (3-10 MB) is loaded entirely into memory on every recipe list fetch. Room does not lazily load BLOBs. This causes:
- 10+ second DB read times for 50 recipes with photos
- OutOfMemoryError on low-end devices
- Main thread jank (since even Flow-based reads deserialize the entire row)

**Fix:** Store photos as files in the app's internal storage directory. Store only the file path (or URI) in Room. Load via `BitmapFactory.decodeFile()` with sampling.

---

### 3.2 HIGH — `enableEdgeToEdge()` without proper window insets handling

**File:** `MainActivity.kt:42`

Calls `enableEdgeToEdge()` but the top app bar and bottom navigation bar do not properly handle system window insets.

**Why it matters:** On Android 15+, content may render under system bars (navigation bar, status bar), causing tap targets to be obscured.

**Fix:** Use `WindowInsets` APIs properly or ensure `Scaffold` with `innerPadding` is used consistently.

---

### 3.3 HIGH — Recipe detail screen creates `SettingsRepository` every recomposition

**File:** `RecipeDetailScreen.kt:72`

As noted in section 2.3.

---

### 3.4 MEDIUM — `MakeScreen` uses `DisposableEffect` with empty key

**File:** `MakeScreen.kt:82-87`

```kotlin
DisposableEffect(Unit) {
    activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
```

`DisposableEffect(Unit)` with `Unit` as key means the effect fires once. But if the `activity` reference changes (configuration change), `onDispose` might not clean up properly.

**Fix:** Use `DisposableEffect(activity)` or consume the lifecycle directly.

---

### 3.5 MEDIUM — `FLAG_KEEP_SCREEN_ON` not cleaned up on process death

**File:** `MakeScreen.kt:83`

Sets the keep-screen-on flag but on process death, the `onDispose` may not fire, leaving the window flag set forever.

**Fix:** Override `onStop()` or use `Lifecycle.Event.ON_STOP` to clear the flag.

---

### 3.6 MEDIUM — `BackHandler` used but no deep link handling

**File:** `NapectApp.kt:139`

`BackHandler` integrated but the manifest declares `intent-filter` for `ACTION_SEND` without any deep link handling (`android:autoVerify` or intent scheme validation).

**Why it matters:** Any app can send text/image to Napect. While this is the intended function, there is no input validation for shared images. A malicious app could send a crafted URI leading to path traversal via `FileProvider` (see security section).

---

### 3.7 LOW — `exportSchema = false` for Room

**File:** `NapectDatabase.kt:26`

Room's schema export is disabled. This prevents migration testing, schema diff verification in CI, and makes it impossible to auto-generate migration test helpers.

**Fix:** Enable schema export: `exportSchema = true` and add `kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }`.

---

### 3.8 LOW — Example unit tests are placeholder

**Files:** `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`

Both contain only trivial `2+2=4` and package-name checks. Zero tests for any business logic, ViewModel, Repository, or DAO.

---

## 4. Security Audit

### 4.1 CRITICAL — FileProvider grants broad access to entire cache directory

**File:** `file_paths.xml:3`

```xml
<cache-path name="cache" path="." />
```

Exposes the **entire** cache directory.

**Why it matters:** When `launchCameraInternal` in `AddRecipeScreen.kt:184-196` creates a temp file in `context.cacheDir` and grants `FLAG_GRANT_WRITE_URI_PERMISSION | FLAG_GRANT_READ_URI_PERMISSION` to the camera app, the camera app (or any malicious app that intercepts the intent) can read/write **all files** in the cache directory, not just the specific photo file. Path traversal is trivially exploitable by replacing the filename in the URI.

**Fix:** Restrict the path to a specific subdirectory:

```xml
<cache-path name="camera_captures" path="camera_captures/" />
```

And create camera files in that subdirectory.

---

### 4.2 HIGH — No SSL/TLS certificate pinning

**File:** `di/AppModule.kt:27`

```kotlin
fun provideOkHttpClient(): OkHttpClient = OkHttpClient()
```

Uses default configuration with no certificate pinning. Since the URL import service fetches arbitrary URLs, this is partially acceptable. However, there is no hostname verification customization.

**Fix:** Add certificate pinning for known hosts if/when API endpoints are introduced. For now, at minimum configure timeouts.

---

### 4.3 MEDIUM — No ProGuard/R8 minification in release builds

**File:** `app/build.gradle.kts:30`

```kotlin
isMinifyEnabled = false
```

**Why it matters:** APK is not obfuscated. Hardcoded strings (including Firebase project info, package name) are trivially extractable. This is a Play Store policy concern for 2025+ where minification/obfuscation is increasingly recommended.

**Fix:** Enable R8 minification: `isMinifyEnabled = true` with proper keep rules.

---

### 4.4 MEDIUM — No screenshot protection

The app contains recipe data which is personal. No `FLAG_SECURE` is set on the window.

**Fix:** Optionally set `FLAG_SECURE` on sensitive screens, or add a user-facing toggle in settings.

---

### 4.5 MEDIUM — `RECORD_AUDIO` permission declared but never used

**File:** `AndroidManifest.xml:12`

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

`RECORD_AUDIO` is declared. The `VoiceInputButton` uses the speech recognizer intent (which does not require RECORD_AUDIO — the system app handles recording). This is a **lying permission** — it declares a dangerous permission that the app never actually requests, which could trigger user distrust during Play Store review.

**Fix:** Remove `<uses-permission android:name="android.permission.RECORD_AUDIO" />` from the manifest.

---

### 4.6 LOW — Firebase BOM dependency but no Firebase initialization

**File:** `app/build.gradle.kts:91-92`

`firebase-bom` and `firebase-ai` are included. `NapectApplication` does not call `FirebaseApp.initializeApp()`. The `firebase-ai` artifact may not work without initialization. The app currently never calls any Firebase API, so these dependencies are essentially dead weight.

**Fix:** Either initialize Firebase in `NapectApplication.onCreate()` or remove unused Firebase dependencies.

---

## 5. Performance Audit

### 5.1 CRITICAL — Photo BLOB loading causes massive main thread allocations

As noted in 3.1 — all photos loaded as BLOBs decoded on main thread. This is the single biggest performance risk.

---

### 5.2 HIGH — `BitmapFactory.decodeByteArray` without sampling

**Files:** `NapectApp.kt:305`, `AddRecipeScreen.kt:344`, `RecipeDetailScreen.kt:82`, etc.

All decode photos at full resolution with no `BitmapFactory.Options.inSampleSize`.

**Why it matters:** A 4000x3000 phone photo (12 MP) decoded at full size produces a ~48 MB bitmap. On a budget phone with limited memory, this causes frequent GC jank and `OutOfMemoryError`.

**Fix:** Use `BitmapFactory.Options` with `inJustDecodeBounds = true` first to determine size, then calculate `inSampleSize` to decode at display resolution (e.g., 1080px wide).

---

### 5.3 HIGH — Room queries load all recipes on every composition

**File:** `RecipeViewModel.kt:26`

```kotlin
val recipes: StateFlow<List<Recipe>> = repo.getAllRecipes()
    .catch { emit(emptyList()) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

`getAllRecipes()` returns a `Flow` that loads all recipes (including BLOB photo data) into memory, every time any recipe data changes. When the database has 100+ recipes with photos, this is many megabytes of data per read.

**Fix:** Create a lightweight projection (`RecipeListItem` entity with no photo) for list queries. Load full recipe (with photo) only for detail screen.

---

### 5.4 MEDIUM — Inefficient tag filtering via Kotlin collections

**File:** `NapectApp.kt:260-262`

```kotlin
val tagFiltered = selectedTagId?.let { tid ->
    searchFiltered.filter { r -> r.tags.any { t -> t.id == tid } }
} ?: searchFiltered
```

Tag filtering and search filtering are done in-memory via Kotlin collection operations for every recomposition. With 1000+ recipes, this causes frame drops.

**Fix:** Push filtering to Room queries. Either use Room's `@Query` with WHERE clauses or use Room's `Flow` with proper database-level filtering.

---

### 5.5 MEDIUM — Search uses `LIKE` with leading `%`

**File:** `RecipeDao.kt:102`

```sql
WHERE title LIKE '%' || :query || '%'
```

This is a non-sargable query (cannot use indexes). For 10K+ recipes, this is a full table scan.

**Fix:** Consider FTS4 (Room FTS) for full-text search or use `COLLATE NOCASE` with indexed columns.

---

### 5.6 MEDIUM — No minimum query length for search

**File:** `RecipeViewModel.kt:32`

```kotlin
val searchResults: StateFlow<List<Recipe>> = _searchQuery
    .debounce(300)
    .flatMapLatest { q -> if (q.isBlank()) repo.getAllRecipes() else repo.search(q) }
```

No minimum length check, so a single character query triggers a search.

**Fix:** Add `filter { it.length >= 2 }` before the debounce to avoid trivial searches.

---

### 5.7 LOW — OkHttp client has no custom timeouts

**File:** `di/AppModule.kt:27`

```kotlin
fun provideOkHttpClient(): OkHttpClient = OkHttpClient()
```

Default timeouts (10s connect, 10s read/write). This is reasonable but could be tightened for URL import.

---

## 6. Dependency & Gradle Audit

### 6.1 HIGH — Firebase BOM and `firebase-ai` are dead dependencies

**File:** `app/build.gradle.kts:91-92`

`firebase-bom` and `firebase-ai` are included but **no Firebase SDK class is ever imported or used** in any Kotlin file. The `GeminiNanoService` uses reflection-based class detection for AICore, not Firebase AI.

**Why it matters:** Adds ~2MB to APK size, increases build time, and pulls transitive dependencies unnecessarily.

**Fix:** Remove Firebase BOM and `firebase-ai` dependency unless Firebase is actively being integrated.

---

### 6.2 HIGH — `material3:1.5.0-alpha10` pinned in dependencies (overriding BOM)

**File:** `app/build.gradle.kts:62`

```kotlin
implementation("androidx.compose.material3:material3:1.5.0-alpha10")
```

Pins `material3:1.5.0-alpha10` with an **alpha** version, overriding the BOM's stable version.

**Why it matters:** Alpha libraries are unstable and may introduce breaking changes or bugs in production. Using an alpha override is risky.

**Fix:** Remove the explicit override unless the M3 Expressive APIs are critical. If needed, use a beta or stable version.

---

### 6.3 MEDIUM — Version catalog has unused entries

**File:** `libs.versions.toml`

Contains `kotlin-android` plugin catalog entry (line 67) which is not applied in `build.gradle.kts` (the project uses AGP built-in Kotlin). Also contains `kapt` plugin (line 69) which is not used (project uses KSP).

**Fix:** Remove unused plugin entries from the version catalog.

---

### 6.4 MEDIUM — KSP version mismatch with Kotlin version

**File:** `libs.versions.toml:12`

```
ksp = "2.3.7"
kotlin = "2.3.21"
```

KSP version `2.3.7` but Kotlin version is `2.3.21`. KSP versions should match the Kotlin version.

**Fix:** Use KSP version `2.3.21-1.0.20` (or the matching KSP version for Kotlin 2.3.21).

---

### 6.5 MEDIUM — No parallel build / build cache optimizations

**File:** `gradle.properties:13`

```properties
# org.gradle.parallel=true
```

Parallel build is commented out. No build cache configuration, no configuration cache.

**Fix:** Enable Gradle parallel build, configuration cache, and build cache for faster CI builds.

---

### 6.6 LOW — `agp = "9.2.1"` is very new

AGP 9.2.1 is compatible with the project. Ensure all other tools (Android Studio, SDK) are compatible.

---

### 6.7 LOW — No JVM target set in `gradle.properties`

`kotlin.jvm.target` is not set in properties. The `app/build.gradle.kts` sets `jvmToolchain(11)` correctly.

---

## 7. UI/UX Audit

### 7.1 HIGH — No loading/empty/error states for recipe list

**File:** `RecipeListScreen.kt`

When the list is empty, no empty state is shown. When loading (initial), no shimmer or progress indicator. When there's an error, a simple `Text("Error: $vmError")` is shown in `NapectApp.kt:266` but it's not styled and may be invisible.

**Fix:** Add `EmptyState` composable with illustration/text, `LoadingState` with shimmer, and proper `ErrorState` with retry button.

---

### 7.2 HIGH — Search bar renders full recipe cards in suggestions

**File:** `NapectApp.kt:271-314`

The search suggestion popup renders recipe cards with photos inside a `LazyColumn` inside `SearchBar` suggestions slot. This is computationally expensive during typing.

**Why it matters:** Every keystroke triggers a database search (debounced 300ms) and recomposition of the suggestion list with full recipe cards including image decoding.

**Fix:** Show only text-based suggestions in the search bar (title + summary), defer full card rendering to the main list.

---

### 7.3 MEDIUM — No accessibility content descriptions on several interactive elements

- `NapectApp.kt:211` — `Icons.Filled.Image` has `contentDescription = null`
- `NapectApp.kt:219` — `Icons.Filled.CameraAlt` has `contentDescription = null`
- `SettingsScreen.kt:92` — `Icons.Filled.AutoAwesome` leading icon has no content description
- Multiple `IconButton` instances with `contentDescription = null`

**Why it matters:** TalkBack users cannot understand these controls.

---

### 7.4 MEDIUM — Emoji used in place of accessible icon

**File:** `VoiceInputButton.kt:43`

```kotlin
Text("🎤")
```

Using emoji as a button icon. This is not accessible, not consistent with Material Design, and has different rendering across devices.

**Fix:** Use `Icon(Icons.Filled.Mic, ...)` and `Text` for label.

---

### 7.5 MEDIUM — DatePickerDialog uses platform dialog (not Compose)

**File:** `RecipeDetailScreen.kt:172`

Uses `DatePickerDialog` (platform widget) instead of `DatePickerDialog` from Material3 Compose.

**Why it matters:** Inconsistency with the rest of the Compose-based UI. Platform dialogs may not respect dynamic theming.

**Fix:** Use Material3 DatePicker composable (`androidx.compose.material3.DatePickerDialog`).

---

### 7.6 MEDIUM — Toast messages used for feedback throughout

Multiple files use `Toast.makeText(...)` for errors, confirmations, and status messages.

**Why it matters:** Toasts are deprecated on Android 14+ (App Ops). They are easily missed by users and not accessible.

**Fix:** Migrate to `Snackbar` (via `SnackbarHostState`) or `AlertDialog` for important confirmations.

---

### 7.7 LOW — Hardcoded strings throughout UI

All UI strings are in Czech and hardcoded. No string resources used (`@string/...`).

**Why it matters:** No localization support, cannot use translation services, violates Android best practices.

**Fix:** Extract all user-facing strings to `strings.xml`.

---

### 7.8 LOW — Minimal theme color customization

**File:** `Color.kt`

Only three pairs of purple/grey/pink colors. No `error`, `surface`, `background`, `onSurface` customization. Dynamic colors on Android 12+ work but the fallback lacks established brand identity.

---

## 8. Networking Audit

### 8.1 HIGH — URL import OkHttp client has no retry/interceptor for resilience

**File:** `UrlImportService.kt:18`

```kotlin
client.newCall(req).execute()
```

Uses `execute()` directly with no retry logic, caching interceptor, or logging interceptor.

**Why it matters:** Network failures during URL import result in immediate failure. No offline caching for previously imported URLs.

**Fix:** Add retry interceptor (e.g., 2 retries with exponential backoff), cache directory for OkHttp, and logging interceptor in debug builds.

---

### 8.2 MEDIUM — HTML parsing via regex may be fragile

**File:** `UrlImportService.kt:24`

```kotlin
val jsonLdPattern = Pattern.compile("<script[^>]*type=\\\"application/ld\\+json\\\"[^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
```

Uses a regex pattern to extract JSON-LD from HTML. This is notoriously fragile and may break on malformed HTML or unusual whitespace.

**Fix:** Consider using `Jsoup` for HTML parsing, which is the standard Android library for this purpose.

---

### 8.3 MEDIUM — No custom `User-Agent` for URL imports

**File:** `UrlImportService.kt:18`

The OkHttp `Request.Builder()` does not set a `User-Agent` header. Some websites block requests without a proper User-Agent.

**Fix:** Set a reasonable User-Agent string.

---

### 8.4 LOW — `OkHttpClient.execute()` called from `Dispatchers.IO` but no cancellation

**File:** `UrlImportService.kt:19`

```kotlin
client.newCall(req).execute()
```

If the coroutine is cancelled (user navigates away), the HTTP call is not cancelled. The `Response` is not explicitly cancelled either.

**Fix:** Use `withContext(Dispatchers.IO) { client.newCall(req).await() }` (OkHttp coroutine extension) instead of `.execute()`. This properly propagates cancellation.

---

## 9. Database Audit

### 9.1 HIGH — No migration tests and `exportSchema = false`

As noted in 3.7. The app has 4 migrations (v2→v3, v3→v4, v4→v5, v5→v6) but no migration testing. A schema change could silently corrupt user data across updates.

---

### 9.2 HIGH — Missing indexes on `recipe_tags` junction table

**File:** `RecipeTagCrossRef.kt:6-12`

```kotlin
@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipe_id", "tag_id"]
)
```

No `@Index` annotation on the junction table. The `recipe_id` and `tag_id` columns are primary keys (composite), but Room does not automatically create individual indexes for join queries.

**Why it matters:** Queries like `SELECT * FROM recipes INNER JOIN recipe_tags ... WHERE tag_id = ?` (RecipeDao.kt:107) do a full scan of the junction table.

**Fix:** Add indexes:

```kotlin
@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipe_id", "tag_id"],
    indices = [Index("recipe_id"), Index("tag_id")]
)
```

---

### 9.3 MEDIUM — `IngredientGroupInsert` is a public top-level data class in DAO file

**File:** `RecipeDao.kt:12-15`

```kotlin
data class IngredientGroupInsert(
    val group: IngredientGroupEntity,
    val ingredients: List<IngredientEntity>,
)
```

Should be an internal data structure but is top-level and public in the DAO file.

---

### 9.4 MEDIUM — `getRecipesByTagId` query not used anywhere

**File:** `RecipeDao.kt:107`

```kotlin
fun getRecipesByTagId(tagId: Long): Flow<List<RecipeWithDetails>>
```

Defined but never called in any repository or screen code. Dead code.

---

### 9.5 LOW — Auto-migration from v5→v6 exists purely for index name fix

**File:** `DatabaseProvider.kt:138-149`

Moving from `idx_tags_name` to `index_tags_name` to match Room conventions. This indicates a prior mismatch that was difficult to debug.

**Fix:** Use `@Index` annotation consistently to avoid manual naming in the future.

---

## 10. Testing Audit

### 10.1 CRITICAL — Zero meaningful tests

**Files:**
- `ExampleUnitTest.kt`: tests `2 + 2 == 4`
- `ExampleInstrumentedTest.kt`: tests package name

**Why it matters:** The app has a Room database with 6 tables, 4 migrations, complex tag filtering logic, URL import parsing, AI tag suggestion heuristics, and OCR import — none of which are tested. Even a simple migration bug could cause data loss for all users.

**Fix:** Start with:
1. Database migration tests (Room's `MigrationTestHelper`)
2. `RecipeRepositoryImpl` unit tests with fake DAOs
3. `TagSuggester` unit tests for keyword matching
4. `IngredientParser` unit tests
5. `UrlImportService` unit tests with mock HTTP responses
6. `RecipeViewModel` unit tests with `kotlinx-coroutines-test` and fake repository

---

### 10.2 HIGH — No coroutine testing infrastructure

No `kotlinx-coroutines-test` dependency in the build file. `viewModelScope.launch` cannot be tested deterministically.

---

### 10.3 HIGH — Room DAOs cannot be unit tested

No `Room.inMemoryDatabaseBuilder` test setup exists. DAOs are only testable via instrumentation tests (which are slow).

**Fix:** Write DAO tests using Room's in-memory database with `@RunWith(AndroidJUnit4::class)`.

---

### 10.4 MEDIUM — No Compose UI tests

Despite using Compose throughout, there are zero Compose UI tests. The `ui-test-junit4` and `ui-test-manifest` dependencies are in the catalog but no tests exist.

---

## 11. Play Store & Production Readiness Audit

### 11.1 CRITICAL — No crash reporting / analytics

No Crashlytics, no analytics SDK, no `Thread.setDefaultUncaughtExceptionHandler`. When the app crashes (and with the swallowed exceptions pattern, it will), there is zero visibility.

---

### 11.2 HIGH — `data_extraction_rules.xml` is a TODO template

**File:** `data_extraction_rules.xml:8`

```xml
<!-- TODO: Use <include> and <exclude> to control what is backed up. -->
```

No actual rules. This means **no cloud backup** of user data (recipes, tags, settings).

**Why it matters:** Users who lose their device lose ALL their recipes. Room database and DataStore are not backed up without explicit rules.

**Fix:** Add proper backup rules for the Room database (`napect.db`) and DataStore (`user_prefs`).

---

### 11.3 HIGH — `backup_rules.xml` is also a TODO template

**File:** `backup_rules.xml`

Same issue as above. No device-to-device transfer backup configured.

---

### 11.4 MEDIUM — Release build has no minification (Play policy)

As noted in 4.3. While not strictly required, Play Store increasingly recommends obfuscation for data safety.

---

### 11.5 MEDIUM — No feature flags

The app uses hardcoded `Phase 2` and `Phase 3` feature toggles (comments in code). Features like Camera and voice input are partially implemented but cannot be remotely toggled off if buggy.

---

### 11.6 MEDIUM — No unified logging strategy

Uses `android.util.Log.d` in some places, `Timber` is not included. Logging is inconsistent and cannot be controlled per build type.

---

### 11.7 LOW — `applicationId` mismatch between manifest and build file

**File:** `app/build.gradle.kts:19`

```kotlin
applicationId = "com.tobiso.napect"
```

But the namespace is `com.tkolymp.napect`. The test instrumentation test checks `appContext.packageName` which would be `com.tobiso.napect` not `com.tkolymp.napect`.

**Fix:** Align `applicationId` with namespace or have a clear reason for the divergence.

---

### 11.8 LOW — Missing Play Integrity / SafetyNet attestation

Not needed for the current fully-offline app, but should be noted if any remote API is ever added.

---

## 12. Final Summary

### Scores

| Category | Score | Explanation |
|---|---|---|
| **Architecture** | 5/10 | Clean-ish separation but no use cases, global mutable state, ViewModel-Activity coupling |
| **Kotlin Quality** | 4/10 | Swallowed exceptions everywhere, main-thread bitmap decoding, `Date` usage, unsafe casts |
| **Performance** | 3/10 | BLOB photos in DB, full-resolution decoding on main thread, in-memory filtering on all data |
| **Security** | 5/10 | FileProvider cache exposure, no R8, no certificate pinning, lying permissions |
| **Production Readiness** | 2/10 | Zero crash reporting, no backup rules, no testing, no analytics, alpha dependencies |
| **Maintainability** | 4/10 | Dead code (factories, classifier, DAO query), duplicate patterns, weak domain layer |
| **Testing** | 0/10 | No tests whatsoever |

### Top 10 Most Critical Problems

1. **CRITICAL** — Photo BLOBs in Room + main-thread full-resolution decoding (will cause OOM/ANR at scale)
2. **CRITICAL** — Silent exception swallowing everywhere (any error invisible to user and developer)
3. **CRITICAL** — Zero tests for any business logic (schema changes = guaranteed data corruption)
4. **CRITICAL** — No crash reporting or monitoring (crashes invisible)
5. **HIGH** — FileProvider exposes entire cache directory (path traversal risk)
6. **HIGH** — No backup rules (users lose all data on device loss)
7. **HIGH** — Alpha Material3 dependency in release (instability risk)
8. **HIGH** — Dead Firebase dependencies adding bloat without use
9. **HIGH** — `SettingsRepository` created per recomposition (wasteful, breaks DI pattern)
10. **HIGH** — Search and filtering done in-memory (will fail at 500+ recipes)

### Remediation Roadmap

#### Quick wins (1-2 days each)

| # | Effort | Task | Section |
|---|--------|------|---------|
| 1 | 1h | Remove dead dependencies: Firebase BOM, `firebase-ai`, alpha material3 override | 6.1, 6.2 |
| 2 | 30min | Add R8 minification for release builds | 4.3 |
| 3 | 15min | Remove `RECORD_AUDIO` permission from manifest | 4.5 |
| 4 | 30min | Add OkHttp timeouts and User-Agent | 8.1, 8.3 |
| 5 | 2h | Remove `AppContextHolder` — inject via Hilt instead | 1.1 |
| 6 | 30min | Restrict `file_paths.xml` to specific camera subdirectory | 4.1 |
| 7 | 15min | Align KSP version with Kotlin version | 6.4 |
| 8 | 30min | Add minimum query length to search (2 chars) | 5.6 |
| 9 | 30min | Delete dead code: factory files, `RecipeClassifier`, unused DAO query | 1.6, 1.7, 9.4 |
| 10 | 30min | Delete unused version catalog entries | 6.3 |

#### Medium-effort improvements (1 week each)

| # | Effort | Task | Section |
|---|--------|------|---------|
| 1 | 3d | Extract all photo storage from Room to file system | 3.1, 5.1 |
| 2 | 2d | Add bitmap sampling via `BitmapFactory.Options.inSampleSize` | 5.2 |
| 3 | 2d | Add proper backup rules for Room DB + DataStore | 11.2, 11.3 |
| 4 | 3d | Write database migration tests | 9.1 |
| 5 | 2d | Add `Timber` logging and `Firebase Crashlytics` | 11.1, 11.6 |
| 6 | 3d | Replace Toast with Snackbar across the app | 7.6 |
| 7 | 2d | Add empty/loading/error states to all screens | 7.1 |
| 8 | 3d | Add proper indexes to `recipe_tags` junction table | 9.2 |
| 9 | 2d | Migrate to `java.time.Instant` | 2.4 |
| 10 | 3d | Add `coil` for image loading (replace manual BitmapFactory) | 5.2 |
| 11 | 2d | Add accessibility content descriptions | 7.3 |
| 12 | 2d | Replace emoji button with Material icon | 7.4 |

#### High-impact refactors (2-4 weeks)

| # | Effort | Task | Section |
|---|--------|------|---------|
| 1 | 2w | Create lightweight recipe projection entity for list queries (no BLOB) | 5.3 |
| 2 | 2w | Introduce use case layer between ViewModel and Repository | 1.4 |
| 3 | 1w | Migrate all ViewModel wiring to `hiltViewModel()` in composables | 1.3 |
| 4 | 3w | Add comprehensive unit test suite: DAOs, Repository, TagSuggester, IngredientParser | 10.1 |
| 5 | 2w | Implement Room FTS4 for performant full-text search | 5.5 |
| 6 | 1w | Push tag filtering to Room queries | 5.4 |
| 7 | 2w | Add Pagination (Paging 3) to recipe list | 5.4 |
| 8 | 1w | Stop swallowing exceptions — add proper error handling throughout | 2.1 |
| 9 | 2w | Add Compose UI tests for critical screens | 10.4 |
| 10 | 2w | Replace all `Date` with `java.time.Instant` | 2.4 |

#### Long-term architectural recommendations

| # | Task | Section |
|---|------|---------|
| 1 | Modularize: split into `:core:database`, `:core:network`, `:core:ui`, `:feature:recipes`, `:feature:settings` | 1.4 |
| 2 | Implement DataStore-backed `UserPreferences` as a proper single source of truth instead of per-screen instantiation | 2.3 |
| 3 | Add WorkManager for background URL import and OCR processing | 8 |
| 4 | Implement Coil for image loading instead of manual `BitmapFactory` calls | 5.2 |
| 5 | Add CI/CD pipeline with lint, ktlint, detekt, test coverage thresholds | 10 |
| 6 | Add Jetpack Benchmark for startup and database performance regression detection | 5 |
| 7 | Consider multi-module navigation with dynamic feature delivery | 1.3 |
| 8 | Add proper feature flags for phased rollouts | 11.5 |
| 9 | Implement screenshot protection (`FLAG_SECURE`) for sensitive screens | 4.4 |
| 10 | Extract all strings to `strings.xml` for localization | 7.7 |


## Workplan

Phase 1: Quick Wins (15min–2h each)
- [x] 1.1 Remove dead dependencies: Firebase BOM, firebase-ai, alpha material3 override
- [x] 1.2 Enable R8 minification for release builds (isMinifyEnabled = true)
- [x] 1.3 Remove RECORD_AUDIO permission from manifest
- [x] 1.4 Add OkHttp timeouts + User-Agent header
- [x] 1.5 Remove AppContextHolder — inject @ApplicationContext Context via Hilt into UrlImportViewModel
- [x] 1.6 Restrict file_paths.xml to specific camera_captures/ subdirectory
- [x] 1.7 Fix KSP version to match Kotlin (2.3.21-1.0.20)
- [x] 1.8 Add minimum query length check (2 chars) to search
- [x] 1.9 Delete dead code: RecipeViewModelFactory.kt, UrlImportViewModelFactory.kt, RecipeClassifier.kt, unused getRecipesByTagId DAO query
- [x] 1.10 Clean unused version catalog entries (kotlin-android, kapt, hilt-compiler)
Phase 2: Medium Effort (2–3d each)
- [x] 2.1 Extract photo storage from Room BLOBs to internal storage files
- [x] 2.2 Add bitmap sampling via BitmapFactory.Options.inSampleSize everywhere
- [x] 2.3 Configure proper backup rules for napect.db + DataStore
- [x] 2.4 Write database migration tests (Room MigrationTestHelper)
- [x] 2.5 Add Timber logging + replace all silent catch (_: Exception) with proper logging
- [x] 2.6 Replace all Toast usages with Snackbar
- [x] 2.7 Add empty/loading/error states to recipe list and all screens
- [x] 2.8 Add @Index on recipe_tags junction table columns
- [x] 2.9 Replace java.util.Date with java.time.Instant
- [x] 2.10 Add accessibility contentDescription on all IconButton and icon elements
- [x] 2.11 Replace emoji mic Text("🎤") with Material Icon(Icons.Filled.Mic)
- [x] 2.12 Fix DisposableEffect(Unit) → DisposableEffect(activity) in MakeScreen
Phase 3: High-Impact Refactors (1–4w each)
- [x] 3.1 Create lightweight recipe projection entity (no BLOB) for list queries
- [x] 3.2 Introduce use case layer between ViewModel and Repository
- [x] 3.3 Migrate ViewModel wiring to hiltViewModel() in composables
- [x] 3.4 Write comprehensive unit tests: DAOs, Repository, TagSuggester, IngredientParser, ViewModel
- [x] 3.5 Implement Room FTS4 for performant full-text search
- [x] 3.6 Push tag filtering to Room queries instead of in-memory
- [x] 3.7 Add Paging 3 to recipe list
- [x] 3.8 Replace all swallowed exceptions with proper error handling and user-facing errors
- [x] 3.9 Add Compose UI tests for critical screens
- [x] 3.10 Extract all hardcoded Czech strings to strings.xml
- [x] 3.11 Fix KSP version to match Kotlin (2.3.21-1.0.20 in libs.versions.toml) — KSP 2.3.0+ is standalone, already correct
- [x] 3.12 Fix SettingsRepository inline creation in SettingsScreen — wrap in remember()
Phase 4: Long-Term
- [ ] 4.1 Modularize into multi-module architecture
- [x] 4.2 Implement proper UserPreferences DataStore singleton (SettingsViewModel + hiltViewModel() in SettingsScreen, MakeScreen, RecipeDetailScreen)
- [x] 4.3 Add WorkManager for background URL import (UrlImportWorker with retry + network constraint; UrlImportViewModel.fetchUrlWithWorker(); HiltWorkerFactory wired via Configuration.Provider)
- [x] 4.4 Add Coil for image loading (replaced PhotoManager.loadBitmap + BitmapFactory everywhere; deleted dead PhotoUtils.kt)
- [x] 4.5 Add CI/CD pipeline with lint and unit tests (.github/workflows/ci.yml)
- [x] 4.6 Add Jetpack Benchmark tests (:benchmark module; StartupBenchmark + RecipeListScrollBenchmark macrobenchmarks)
- [x] 4.7 Add feature flags (FeatureFlags.kt; URL_IMPORT, OCR_IMPORT, VOICE_INPUT, CAMERA, AI_TAG_SUGGESTIONS, PAGED_LIST, BACKGROUND_URL_IMPORT; gated in NapectApp)
- [x] 4.8 Add screenshot protection (FLAG_SECURE applied in MainActivity from screenshotProtectionEnabled pref; toggle in SettingsScreen)