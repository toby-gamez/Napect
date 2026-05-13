package com.tkolymp.napect

import android.content.Context

/**
 * Small global holder to provide Application context to components that need it from non-Android
 * framework classes (ViewModels). This is intentionally minimal to avoid adding DI.
 */
object AppContextHolder {
    var context: Context? = null
}
