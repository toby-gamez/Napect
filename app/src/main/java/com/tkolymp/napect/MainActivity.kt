package com.tkolymp.napect

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import com.tkolymp.napect.ui.theme.NapectTheme
import com.tkolymp.napect.data.local.SettingsRepository
import com.tkolymp.napect.data.local.ThemeMode
import com.tkolymp.napect.data.local.UserPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.ViewModelProvider
import com.tkolymp.napect.ui.recipes.RecipeViewModel
import com.tkolymp.napect.ui.recipes.RecipeViewModelFactory
import com.tkolymp.napect.data.network.UrlImportService
import com.tkolymp.napect.ui.recipes.UrlImportViewModelFactory
import com.tkolymp.napect.ui.recipes.UrlImportViewModel
import com.tkolymp.napect.data.local.DatabaseProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.tkolymp.napect.data.repository.RecipeRepositoryImpl

class MainActivity : ComponentActivity() {
    private lateinit var importVm: com.tkolymp.napect.ui.recipes.UrlImportViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // create DB and repository and ViewModel here (manual DI)
        val db = DatabaseProvider.getDatabase(applicationContext)
        val repo = RecipeRepositoryImpl(db.recipeDao(), db.tagDao())
        // Ensure default tags exist (idempotent) so fresh installs or upgrades get defaults
        lifecycleScope.launch {
            try {
                repo.ensureDefaultTags()
            } catch (_: Exception) { }
        }
        // URL import service & ViewModel
        val importService = UrlImportService()
        // AI client: prefer Gemini wrapper when available, fallback to local summarizer
        val geminiService = com.tkolymp.napect.data.ai.GeminiNanoService(applicationContext, importService)
        val aiClient = com.tkolymp.napect.data.ai.DefaultAiClient(geminiService)

        val vm: RecipeViewModel = ViewModelProvider(this, RecipeViewModelFactory(repo, aiClient)).get(RecipeViewModel::class.java)

        // URL import service & ViewModel
        // Gemini Nano service wrapper (uses fallback when Gemini not available)
        importVm = ViewModelProvider(this, UrlImportViewModelFactory(importService, repo, geminiService, aiClient)).get(UrlImportViewModel::class.java)

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
            // set the app context for ViewModels that need to access resources like content resolver
            AppContextHolder.context = applicationContext
            val settingsRepo = SettingsRepository(applicationContext)
            val prefs by settingsRepo.prefsFlow.collectAsState(initial = UserPreferences())
            val dark = when (prefs.themeMode) {
                ThemeMode.AUTO -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            NapectTheme(darkTheme = dark) {
                // deliver the initial shared values to the ViewModel before composing the app so the import screen
                // can act on them. Use receiveShared* APIs so the ViewModel exposes proper state flows.
                importVm.receiveSharedUrl(sharedUrl)
                importVm.receiveSharedImageUri(sharedImageUri)
                NapectApp(vm, importVm, sharedUrl, sharedImageUri)
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
