package com.tkolymp.napect.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Minimal URL importer that fetches an HTML page and attempts to extract a Schema.org Recipe
 * from JSON-LD script tags. Falls back to the page title if nothing found.
 */
open class UrlImportService(private val client: OkHttpClient = OkHttpClient()) {
    open suspend fun fetchHtml(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get()
                .header("User-Agent", "Napect/1.0 (Android; Recipe Manager)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                Result.success(resp.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun importFromUrl(url: String): Result<ImportedRecipeData> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get()
                .header("User-Agent", "Napect/1.0 (Android; Recipe Manager)")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                val body = resp.body?.string().orEmpty()

                // find <script type="application/ld+json"> blocks
                val jsonLdPattern = Pattern.compile("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
                val matcher = jsonLdPattern.matcher(body)

                while (matcher.find()) {
                    val jsonText = matcher.group(1).trim()
                    try {
                        // JSON-LD could be an array or an object
                        val root = if (jsonText.trim().startsWith("[")) JSONArray(jsonText) else JSONObject(jsonText)
                        // try to find a Recipe object inside
                        val recipe = extractRecipeFromJsonLd(root)
                        if (recipe != null) return@withContext Result.success(recipe.copy(sourceUrl = url))
                    } catch (e: Exception) {
                        // ignore parse errors, try next script
                    }
                }

                // fallback: extract <title>
                val title = extractTitle(body) ?: "Imported Recipe"
                return@withContext Result.success(ImportedRecipeData(title = title, sourceUrl = url))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts nutrition values from JSON-LD blocks already in the fetched HTML.
     * Returns a partial [ImportedRecipeData] with only nutrition fields set; all others are defaults.
     * Used by [DefaultAiClient] to supplement AI results when nutrition fields come back null.
     */
    fun parseNutritionFromHtml(html: String): ImportedRecipeData? {
        val jsonLdPattern = Pattern.compile("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
        val matcher = jsonLdPattern.matcher(html)
        while (matcher.find()) {
            val jsonText = matcher.group(1)?.trim() ?: continue
            try {
                val root = if (jsonText.startsWith("[")) JSONArray(jsonText) else JSONObject(jsonText)
                val nutrition = findNutrition(root) ?: continue
                val calories = nutrition.optString("calories").ifBlank { null }?.let { parseNutritionNumber(it) }
                val fat = nutrition.optString("fatContent").ifBlank { null }?.let { parseNutritionNumber(it) }
                val carbs = nutrition.optString("carbohydrateContent").ifBlank { null }?.let { parseNutritionNumber(it) }
                val proteins = nutrition.optString("proteinContent").ifBlank { null }?.let { parseNutritionNumber(it) }
                if (calories != null || fat != null || carbs != null || proteins != null) {
                    return ImportedRecipeData(title = "", caloriesKcal = calories, fatG = fat, carbsG = carbs, proteinsG = proteins)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Parses total recipe time (in minutes) from JSON-LD blocks in the fetched HTML,
     * falling back to plain-text HTML patterns if JSON-LD yields nothing.
     * ISO 8601 duration format: PT30M=30, PT1H=60, PT1H30M=90.
     */
    fun parseTimeFromHtml(html: String): Int? {
        val jsonLdPattern = Pattern.compile(
            "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        val matcher = jsonLdPattern.matcher(html)
        while (matcher.find()) {
            val jsonText = matcher.group(1)?.trim() ?: continue
            try {
                val root = if (jsonText.startsWith("[")) JSONArray(jsonText) else JSONObject(jsonText)
                val minutes = findTimeInJsonLd(root)
                if (minutes != null && minutes > 0) return minutes
            } catch (_: Exception) {}
        }
        return parseTimeFromHtmlText(html)
    }

    private fun parseTimeFromHtmlText(html: String): Int? {
        // Strip tags to get visible text, then search for time patterns.
        val text = html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
        // "1 hod 30 min", "1 h 30 min", "1hodina 30minut", etc.
        val hoursAndMinutes = Regex(
            """(\d+)\s*(?:hod(?:in[ay]?)?|h(?:our)?s?)\s*(\d+)\s*(?:min(?:ut[ay]?)?|m)\b""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (hoursAndMinutes != null) {
            val h = hoursAndMinutes.groupValues[1].toIntOrNull() ?: 0
            val m = hoursAndMinutes.groupValues[2].toIntOrNull() ?: 0
            if (h * 60 + m > 0) return h * 60 + m
        }
        // "90 min", "45 minut", "30 minutes"
        val minutesOnly = Regex(
            """(\d+)\s*(?:min(?:ut[ay]?)?|minutes?)\b""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (minutesOnly != null) {
            val m = minutesOnly.groupValues[1].toIntOrNull() ?: 0
            if (m > 0) return m
        }
        // "2 hodiny", "1 hodina" (whole hours, no minutes part)
        val hoursOnly = Regex(
            """(\d+)\s*(?:hod(?:in[ay]?)?|hours?)\b""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (hoursOnly != null) {
            val h = hoursOnly.groupValues[1].toIntOrNull() ?: 0
            if (h > 0) return h * 60
        }
        return null
    }

    private fun findTimeInJsonLd(root: Any): Int? {
        if (root is JSONArray) {
            for (i in 0 until root.length()) findTimeInJsonLd(root.get(i))?.let { return it }
        } else if (root is JSONObject) {
            val type = root.opt("@type")?.toString() ?: ""
            if (type.contains("Recipe", ignoreCase = true)) {
                val total = root.optString("totalTime").ifBlank { null }?.let { parseIso8601Duration(it) }
                if (total != null && total > 0) return total
                val prep = root.optString("prepTime").ifBlank { null }?.let { parseIso8601Duration(it) } ?: 0
                val cook = root.optString("cookTime").ifBlank { null }?.let { parseIso8601Duration(it) } ?: 0
                if (prep + cook > 0) return prep + cook
            }
            root.keys().forEach { k -> findTimeInJsonLd(root.get(k))?.let { return it } }
        }
        return null
    }

    private fun parseIso8601Duration(iso: String): Int? {
        val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""", RegexOption.IGNORE_CASE).find(iso) ?: return null
        val hours = m.groupValues[1].toIntOrNull() ?: 0
        val minutes = m.groupValues[2].toIntOrNull() ?: 0
        return hours * 60 + minutes
    }

    private fun findNutrition(root: Any): JSONObject? {
        if (root is JSONArray) {
            for (i in 0 until root.length()) findNutrition(root.get(i))?.let { return it }
        } else if (root is JSONObject) {
            root.optJSONObject("nutrition")?.let { return it }
            val type = root.opt("@type")?.toString() ?: root.opt("type")?.toString() ?: ""
            if (type.contains("Recipe", ignoreCase = true)) {
                root.optJSONObject("nutrition")?.let { return it }
            }
            root.keys().forEach { k -> findNutrition(root.get(k))?.let { return it } }
        }
        return null
    }

    private fun extractRecipeFromJsonLd(root: Any): ImportedRecipeData? {
        try {
            if (root is JSONArray) {
                for (i in 0 until root.length()) {
                    val elem = root.get(i)
                    val found = extractRecipeFromJsonLd(elem)
                    if (found != null) return found
                }
            } else if (root is JSONObject) {
                // if @type == Recipe or type contains Recipe
                val type = when {
                    root.has("@type") -> root.get("@type").toString()
                    root.has("type") -> root.get("type").toString()
                    else -> ""
                }
                if (type.contains("Recipe", ignoreCase = true)) {
                    val title = root.optString("name", root.optString("headline", "Imported Recipe"))
                    val description = root.optString("description", null)
                    val ingredients = mutableListOf<String>()
                    if (root.has("recipeIngredient")) {
                        val arr = root.get("recipeIngredient")
                        if (arr is JSONArray) {
                            for (i in 0 until arr.length()) ingredients.add(arr.getString(i))
                        } else {
                            ingredients.add(arr.toString())
                        }
                    }
                    val steps = mutableListOf<String>()
                    if (root.has("recipeInstructions")) {
                        val instr = root.get("recipeInstructions")
                        when (instr) {
                            is JSONArray -> {
                                for (i in 0 until instr.length()) {
                                    val step = instr.get(i)
                                    if (step is JSONObject && step.has("text")) steps.add(step.getString("text"))
                                    else steps.add(step.toString())
                                }
                            }
                            is JSONObject -> if (instr.has("text")) steps.add(instr.getString("text"))
                            else -> steps.add(instr.toString())
                        }
                    }
                    val nutrition = root.optJSONObject("nutrition")
                    val calories = nutrition?.optString("calories")?.let { parseNutritionNumber(it) }
                    val fat = nutrition?.optString("fatContent")?.let { parseNutritionNumber(it) }
                    val carbs = nutrition?.optString("carbohydrateContent")?.let { parseNutritionNumber(it) }
                    val proteins = nutrition?.optString("proteinContent")?.let { parseNutritionNumber(it) }
                    return ImportedRecipeData(
                        title = title,
                        description = description,
                        ingredientGroups = groupIngredients(ingredients),
                        steps = steps,
                        caloriesKcal = calories,
                        fatG = fat,
                        carbsG = carbs,
                        proteinsG = proteins,
                    )
                }

                // try nested properties
                val keys = root.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = root.get(k)
                    val found = try { extractRecipeFromJsonLd(v) } catch (e: Exception) { null }
                    if (found != null) return found
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    private fun parseNutritionNumber(s: String): Double? =
        Regex("""[\d]+(?:[.,]\d+)?""").find(s)?.value?.replace(',', '.')?.toDoubleOrNull()

    private fun extractTitle(html: String): String? {
        val p = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val m = p.matcher(html)
        return if (m.find()) m.group(1).trim() else null
    }
}
