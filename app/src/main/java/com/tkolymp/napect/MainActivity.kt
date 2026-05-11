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
import com.tkolymp.napect.data.local.DatabaseProvider
import com.tkolymp.napect.data.repository.RecipeRepositoryImpl

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // create DB and repository and ViewModel here (manual DI)
        val db = DatabaseProvider.getDatabase(applicationContext)
        val repo = RecipeRepositoryImpl(db.recipeDao())
        val vm: RecipeViewModel = ViewModelProvider(this, RecipeViewModelFactory(repo)).get(RecipeViewModel::class.java)

        setContent {
            NapectTheme {
                NapectApp(vm)
            }
        }
    }
}
