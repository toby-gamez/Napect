package com.tkolymp.napect.data.ai.openai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

interface OpenAiKeyProvider {
    fun getKey(): String?
}

class OpenAiKeyStore(private val context: Context) : OpenAiKeyProvider {
    private val prefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "napect_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse { e ->
            Timber.w(e, "EncryptedSharedPreferences unavailable, falling back to plain prefs")
            context.getSharedPreferences("napect_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val KEY = "openai_api_key"
    }

    override fun getKey(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun setKey(key: String) {
        prefs.edit().putString(KEY, key).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }
}
