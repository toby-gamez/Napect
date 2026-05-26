package com.tkolymp.napect.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tkolymp.napect.data.ai.AiClient
import com.tkolymp.napect.data.network.ImportedIngredient
import com.tkolymp.napect.data.network.ImportedIngredientGroup
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class UrlImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ai: AiClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure(
            workDataOf(KEY_ERROR to "Missing URL")
        )
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return Result.failure(workDataOf(KEY_ERROR to "Neplatná URL adresa: $url"))
        }

        return try {
            val result = ai.extractRecipeFromUrl(url).getOrThrow()

            val json = buildJson(result.title, result.description, result.sourceUrl, result.ingredientGroups, result.steps, result.caloriesKcal, result.fatG, result.carbsG, result.proteinsG, result.nutriScore, result.timeMinutes, result.imageUrl)
            val dir = File(applicationContext.filesDir, "pending_imports").also { it.mkdirs() }
            val file = File(dir, "$id.json")
            file.writeText(json)

            Timber.d("UrlImportWorker: saved result to %s", file.absolutePath)
            Result.success(workDataOf(KEY_RESULT_FILE to file.absolutePath))
        } catch (e: Exception) {
            Timber.w(e, "UrlImportWorker attempt %d failed", runAttemptCount)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown error")))
        }
    }

    private fun buildJson(
        title: String,
        description: String?,
        sourceUrl: String?,
        groups: List<ImportedIngredientGroup>,
        steps: List<String>,
        caloriesKcal: Double?,
        fatG: Double?,
        carbsG: Double?,
        proteinsG: Double?,
        nutriScore: String?,
        timeMinutes: Int?,
        imageUrl: String?,
    ): String {
        val root = JSONObject()
        root.put("title", title)
        root.put("description", description ?: JSONObject.NULL)
        root.put("sourceUrl", sourceUrl ?: JSONObject.NULL)
        val groupsArr = JSONArray()
        groups.forEach { g ->
            val gObj = JSONObject()
            gObj.put("name", g.name)
            val ings = JSONArray()
            g.ingredients.forEach { ings.put(it) }
            gObj.put("ingredients", ings)
            val strucArr = JSONArray()
            g.structuredIngredients.forEach { ing ->
                val ingObj = JSONObject()
                ingObj.put("amount", ing.amount ?: JSONObject.NULL)
                ingObj.put("unit", ing.unit ?: JSONObject.NULL)
                ingObj.put("name", ing.name)
                strucArr.put(ingObj)
            }
            gObj.put("structuredIngredients", strucArr)
            groupsArr.put(gObj)
        }
        root.put("ingredientGroups", groupsArr)
        val stepsArr = JSONArray()
        steps.forEach { stepsArr.put(it) }
        root.put("steps", stepsArr)
        root.put("caloriesKcal", caloriesKcal ?: JSONObject.NULL)
        root.put("fatG", fatG ?: JSONObject.NULL)
        root.put("carbsG", carbsG ?: JSONObject.NULL)
        root.put("proteinsG", proteinsG ?: JSONObject.NULL)
        root.put("nutriScore", nutriScore ?: JSONObject.NULL)
        root.put("timeMinutes", timeMinutes ?: JSONObject.NULL)
        root.put("imageUrl", imageUrl ?: JSONObject.NULL)
        return root.toString()
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_RESULT_FILE = "result_file"
        const val KEY_ERROR = "error"
        const val WORK_NAME = "url_import"
        private const val MAX_RETRIES = 2

        fun buildRequest(url: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<UrlImportWorker>()
                .setInputData(workDataOf(KEY_URL to url))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
    }
}
