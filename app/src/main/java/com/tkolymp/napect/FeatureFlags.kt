package com.tkolymp.napect

/**
 * Compile-time feature flags. Flip a flag to false to disable a feature across the app.
 * Intended as a single control point before remote config is introduced.
 */
object FeatureFlags {
    const val URL_IMPORT = true
    const val OCR_IMPORT = true
    const val VOICE_INPUT = true
    const val CAMERA = true
    const val AI_TAG_SUGGESTIONS = true
    const val PAGED_LIST = true
    const val BACKGROUND_URL_IMPORT = true
}
