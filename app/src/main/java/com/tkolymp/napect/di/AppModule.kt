package com.tkolymp.napect.di

import android.content.Context
import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.data.ai.DefaultAiClient
import com.tkolymp.napect.data.ai.openai.OpenAiKeyStore
import com.tkolymp.napect.data.ai.openai.OpenAiService
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
import javax.inject.Named
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
    @Named("openai")
    fun provideOpenAiOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideUrlImportService(client: OkHttpClient): UrlImportService = UrlImportService(client)

    @Provides
    @Singleton
    fun provideOpenAiKeyStore(@ApplicationContext ctx: Context): OpenAiKeyStore = OpenAiKeyStore(ctx)

    @Provides
    @Singleton
    fun provideOpenAiService(
        @Named("openai") okHttp: OkHttpClient,
        keyStore: OpenAiKeyStore,
        settings: SettingsRepository,
    ): OpenAiService = OpenAiService(okHttp, keyStore, settings)  // both implement their respective interfaces

    @Provides
    @Singleton
    fun provideAiClient(openAi: OpenAiService, keyStore: OpenAiKeyStore, urlImport: UrlImportService): AiClient =
        DefaultAiClient(openAi, keyStore, urlImport)

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
        RecipeRepositoryImpl(db.recipeDao(), db.tagDao(), db)
}
