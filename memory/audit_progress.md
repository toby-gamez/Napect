---
name: audit-progress
description: AUDIT.md remediation status — phases completed and what's left
metadata:
  type: project
---

Full audit in AUDIT.md. Phases 1–4 implemented (except 4.1 multi-module).

Phase 4 completed items:
- 4.2 SettingsViewModel + hiltViewModel() replacing inline SettingsRepository in SettingsScreen, MakeScreen, RecipeDetailScreen
- 4.4 Coil replacing all BitmapFactory/PhotoManager.loadBitmap in UI; PhotoUtils.kt deleted
- 4.5 GitHub Actions CI (.github/workflows/ci.yml): lint → unit-tests → build APK
- 4.6 :benchmark module with StartupBenchmark + RecipeListScrollBenchmark (macrobenchmark)
- 4.7 FeatureFlags.kt singleton; BACKGROUND_URL_IMPORT gated in NapectApp
- 4.8 FLAG_SECURE toggle in SettingsScreen ("Ochrana snímků obrazovky"); applied via SideEffect in MainActivity
- 4.3 UrlImportWorker (@HiltWorker); NetworkType.CONNECTED constraint + exponential retry; HiltWorkerFactory via Configuration.Provider in NapectApplication

**Why:** Systematic audit remediation to bring production readiness from 2/10 to acceptable.

**How to apply:** When adding new features, check FeatureFlags.kt first. When adding background tasks, use UrlImportWorker as the pattern — HiltWorker + WORK_NAME-keyed unique work + JSON file result.

Remaining: 4.1 Multi-module architecture (weeks of work, not started).
