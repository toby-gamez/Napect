package com.tkolymp.napect

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.tkolymp.napect.data.local.DatabaseProvider
import com.tkolymp.napect.data.network.UrlImportService
import com.tkolymp.napect.ui.recipes.UrlImportViewModel
import com.tkolymp.napect.data.ai.GeminiNanoService
import com.tkolymp.napect.data.ai.AiClient
import androidx.compose.ui.Modifier
import com.tkolymp.napect.ui.theme.NapectTheme
import com.tkolymp.napect.data.local.SettingsRepository
import com.tkolymp.napect.data.local.ThemeMode
import com.tkolymp.napect.data.local.UserPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Needed for onNewIntent runtime shares (same instance as in NapectApp via hiltViewModel)
    private val importVm: UrlImportViewModel by viewModels()

    @Inject lateinit var importService: UrlImportService
    @Inject lateinit var geminiService: GeminiNanoService
    @Inject lateinit var aiClient: AiClient
    @Inject lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure default tags exist (idempotent) so fresh installs or upgrades get defaults
        val db = DatabaseProvider.getDatabase(applicationContext)
        lifecycleScope.launch {
            try {
                // repository's ensureDefaultTags is available via database-backed repo
                // call directly on a temporary RecipeRepositoryImpl to seed defaults
                val repo = com.tkolymp.napect.data.repository.RecipeRepositoryImpl(db.recipeDao(), db.tagDao())
                repo.ensureDefaultTags()
                repo.migrateEnglishTagsToCzech()
            } catch (e: Exception) {
                Timber.w(e, "Failed to ensure default tags on startup")
            }
        }

        // detect shared URL/text or shared image
        var sharedUrl: String? = null
        var sharedImageUri: android.net.Uri? = null
        intent?.let { i ->
            if (i.action == android.content.Intent.ACTION_SEND) {
                when (i.type) {
                    "text/plain" -> sharedUrl = i.getStringExtra(android.content.Intent.EXTRA_TEXT)
                    else -> if (i.type?.startsWith("image/") == true) {
                        val uri = i.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                        sharedImageUri = uri
                    }
                }
            }
        }

        setContent {
            val prefs by settingsRepo.prefsFlow.collectAsState(initial = UserPreferences())
            val dark = when (prefs.themeMode) {
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            NapectTheme(darkTheme = dark) {
                NapectApp(initialSharedUrl = sharedUrl, initialSharedImageUri = sharedImageUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // handle runtime share intents (app already running)
        if (intent.action == Intent.ACTION_SEND) {
            when (intent.type) {
                "text/plain" -> {
                    val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
                    importVm.receiveSharedUrl(sharedUrl)
                }
                else -> if (intent.type?.startsWith("image/") == true) {
                    val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    importVm.receiveSharedImageUri(uri)
                }
            }
        }
    }
}
