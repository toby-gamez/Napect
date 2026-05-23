package com.tkolymp.napect.di

import android.content.Context
import com.tkolymp.napect.data.ai.DefaultAiClient
import com.tkolymp.napect.data.ai.GeminiNanoService
import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.data.network.UrlImportService
import com.tkolymp.napect.data.local.DatabaseProvider
import com.tkolymp.napect.data.local.SettingsRepository
import com.tkolymp.napect.domain.repository.RecipeRepository
import com.tkolymp.napect.data.repository.RecipeRepositoryImpl
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import com.tkolymp.napect.data.local.NapectDatabase
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideUrlImportService(client: OkHttpClient): UrlImportService = UrlImportService(client)

    @Provides
    @Singleton
    fun provideGeminiService(@ApplicationContext ctx: Context, service: UrlImportService): GeminiNanoService = GeminiNanoService(ctx, service)

    @Provides
    @Singleton
    fun provideAiClient(gemini: GeminiNanoService): AiClient = DefaultAiClient(gemini)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context) = DatabaseProvider.getDatabase(ctx)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext ctx: Context): SettingsRepository = SettingsRepository(ctx)

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager = WorkManager.getInstance(ctx)
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideRecipeRepository(db: NapectDatabase): RecipeRepository =
        RecipeRepositoryImpl(db.recipeDao(), db.tagDao())
}
