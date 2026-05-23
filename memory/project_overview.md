---
name: napect-overview
description: Czech recipe management app — tech stack and current state
metadata:
  type: project
---

Napect is an Android recipe management app (Czech language, fully offline). Stack: Jetpack Compose, Room (6 migrations, v1→v6), Hilt, Paging 3, Coil, WorkManager, Timber, ML Kit OCR, Gemini Nano.

Photos stored as files in `filesDir/photos/` (migrated from BLOBs in Phase 2). All UI image loading via Coil AsyncImage.

ApplicationId = `com.tobiso.napect` (diverges from namespace `com.tkolymp.napect` intentionally).

**Why:** Started as a personal app, the applicationId reflects an older package name that was never updated to avoid Play Store issues.

**How to apply:** Don't assume namespace == applicationId when checking test configs or benchmark packageName.
