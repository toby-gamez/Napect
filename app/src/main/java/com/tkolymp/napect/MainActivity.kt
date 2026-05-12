package com.tkolymp.napect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import com.tkolymp.napect.ui.theme.NapectTheme
import androidx.lifecycle.ViewModelProvider
import com.tkolymp.napect.ui.recipes.RecipeViewModel
import com.tkolymp.napect.ui.recipes.RecipeViewModelFactory
import com.tkolymp.napect.data.network.UrlImportService
import com.tkolymp.napect.ui.recipes.UrlImportViewModelFactory
import com.tkolymp.napect.ui.recipes.UrlImportViewModel
import com.tkolymp.napect.data.local.DatabaseProvider
import com.tkolymp.napect.data.repository.RecipeRepositoryImpl

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // create DB and repository and ViewModel here (manual DI)
        val db = DatabaseProvider.getDatabase(applicationContext)
        val repo = RecipeRepositoryImpl(db.recipeDao())
        // URL import service & ViewModel
        val importService = UrlImportService()
        // AI client: prefer Gemini wrapper when available, fallback to local summarizer
        val geminiService = com.tkolymp.napect.data.ai.GeminiNanoService(applicationContext, importService)
        val aiClient = com.tkolymp.napect.data.ai.DefaultAiClient(geminiService)

        val vm: RecipeViewModel = ViewModelProvider(this, RecipeViewModelFactory(repo, aiClient)).get(RecipeViewModel::class.java)

        // URL import service & ViewModel
        // Gemini Nano service wrapper (uses fallback when Gemini not available)
        val importVm: UrlImportViewModel = ViewModelProvider(this, UrlImportViewModelFactory(importService, repo, geminiService, aiClient)).get(UrlImportViewModel::class.java)

        // detect shared URL/text
        val sharedUrl: String? = intent?.let { i ->
            if (i.action == android.content.Intent.ACTION_SEND && i.type == "text/plain") {
                i.getStringExtra(android.content.Intent.EXTRA_TEXT)
            } else null
        }

        setContent {
            NapectTheme {
                NapectApp(vm, importVm, sharedUrl)
            }
        }
    }
}
