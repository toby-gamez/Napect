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
class UrlImportService(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun importFromUrl(url: String): Result<ImportedRecipeData> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(Exception("HTTP ${resp.code}"))
                val body = resp.body?.string().orEmpty()

                // find <script type="application/ld+json"> blocks
                val jsonLdPattern = Pattern.compile("<script[^>]*type=\\\"application/ld\\+json\\\"[^>]*>(.*?)</script>", Pattern.DOTALL or Pattern.CASE_INSENSITIVE)
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
                    return ImportedRecipeData(title = title, description = description, ingredients = ingredients, steps = steps)
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

    private fun extractTitle(html: String): String? {
        val p = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val m = p.matcher(html)
        return if (m.find()) m.group(1).trim() else null
    }
}
