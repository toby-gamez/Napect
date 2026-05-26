package com.tkolymp.napect.data.ai.openai

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RecipePrompt {
    private const val SYSTEM_EXTRACTION = """Jsi extraktor receptů. Z přiloženého HTML/textu extrahuj recept v češtině jako JSON podle daného schématu.
Pokud chybí pole, vrať prázdné pole nebo null.
timeMinutes = celkový čas receptu v minutách (celé číslo nebo null). Hledej v JSON-LD polích totalTime, nebo sečti prepTime+cookTime (ISO 8601: PT30M=30, PT1H=60, PT1H30M=90), případně v textu stránky. Nevymýšlej.
Ingredience seskup do logických sekcí (těsto, krém, ozdoba…). Každou ingredienci rozlož na amount (číslo nebo null), unit (jednotka nebo null), name (název). Krok = jedna instrukce.
Náročnost odhadni z počtu kroků a ingrediencí: do 5 kroků a 6 ingrediencí = Jednoduché, nad 8 kroků nebo 12 ingrediencí = Náročné, jinak Střední.
Nutriční hodnoty: pokud jsou v HTML uvedeny nutriční hodnoty (kalorie/energie, tuky, sacharidy, bílkoviny, Nutri-Score), extrahuj je přesně jak jsou uvedeny — mohou být celkové (za celý recept) nebo na porci, obojí je v pořádku.
caloriesKcal = energie v kcal, fatG = tuky v gramech, carbsG = sacharidy v gramech, proteinsG = bílkoviny v gramech.
Pokud nutriční hodnoty nejsou v textu uvedeny, vrať null. Nevymýšlej je.
imageUrl = absolutní URL hlavní fotografie receptu ze stránky (nebo null). Vrať pouze absolutní URL začínající http/https, jinak null.
Odpovídej POUZE platným JSON podle schématu. Žádný markdown, žádné bloky ```json, žádné komentáře."""

    val responseFormat: JsonElement = buildJsonObject {
        put("type", "json_schema")
        put("json_schema", buildJsonObject {
            put("name", "imported_recipe")
            put("strict", true)
            put("schema", buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                put("required", buildJsonArray {
                    add(JsonPrimitive("title"))
                    add(JsonPrimitive("timeMinutes"))
                    add(JsonPrimitive("description"))
                    add(JsonPrimitive("ingredientGroups"))
                    add(JsonPrimitive("steps"))
                    add(JsonPrimitive("difficulty"))
                    add(JsonPrimitive("caloriesKcal"))
                    add(JsonPrimitive("fatG"))
                    add(JsonPrimitive("carbsG"))
                    add(JsonPrimitive("proteinsG"))
                    add(JsonPrimitive("nutriScore"))
                    add(JsonPrimitive("imageUrl"))
                })
                put("properties", buildJsonObject {
                    put("title", buildJsonObject { put("type", "string") })
                    put("timeMinutes", buildJsonObject {
                        put("anyOf", buildJsonArray {
                            add(buildJsonObject { put("type", "integer") })
                            add(buildJsonObject { put("type", "null") })
                        })
                    })
                    put("description", buildJsonObject {
                        put("anyOf", buildJsonArray {
                            add(buildJsonObject { put("type", "string") })
                            add(buildJsonObject { put("type", "null") })
                        })
                    })
                    put("ingredientGroups", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject {
                            put("type", "object")
                            put("additionalProperties", false)
                            put("required", buildJsonArray {
                                add(JsonPrimitive("name"))
                                add(JsonPrimitive("ingredients"))
                            })
                            put("properties", buildJsonObject {
                                put("name", buildJsonObject { put("type", "string") })
                                put("ingredients", buildJsonObject {
                                    put("type", "array")
                                    put("items", buildJsonObject {
                                        put("type", "object")
                                        put("additionalProperties", false)
                                        put("required", buildJsonArray {
                                            add(JsonPrimitive("amount"))
                                            add(JsonPrimitive("unit"))
                                            add(JsonPrimitive("name"))
                                        })
                                        put("properties", buildJsonObject {
                                            val nullableNum = buildJsonArray {
                                                add(buildJsonObject { put("type", "number") })
                                                add(buildJsonObject { put("type", "null") })
                                            }
                                            val nullableStr = buildJsonArray {
                                                add(buildJsonObject { put("type", "string") })
                                                add(buildJsonObject { put("type", "null") })
                                            }
                                            put("amount", buildJsonObject { put("anyOf", nullableNum) })
                                            put("unit", buildJsonObject { put("anyOf", nullableStr) })
                                            put("name", buildJsonObject { put("type", "string") })
                                        })
                                    })
                                })
                            })
                        })
                    })
                    put("steps", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                    })
                    put("difficulty", buildJsonObject {
                        put("anyOf", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "string")
                                put("enum", buildJsonArray {
                                    add(JsonPrimitive("Jednoduché"))
                                    add(JsonPrimitive("Střední"))
                                    add(JsonPrimitive("Náročné"))
                                })
                            })
                            add(buildJsonObject { put("type", "null") })
                        })
                    })
                    val nullableNumber = buildJsonArray {
                        add(buildJsonObject { put("type", "number") })
                        add(buildJsonObject { put("type", "null") })
                    }
                    put("caloriesKcal", buildJsonObject { put("anyOf", nullableNumber) })
                    put("fatG", buildJsonObject { put("anyOf", nullableNumber) })
                    put("carbsG", buildJsonObject { put("anyOf", nullableNumber) })
                    put("proteinsG", buildJsonObject { put("anyOf", nullableNumber) })
                    put("nutriScore", buildJsonObject {
                        put("anyOf", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "string")
                                put("enum", buildJsonArray {
                                    add(JsonPrimitive("A"))
                                    add(JsonPrimitive("B"))
                                    add(JsonPrimitive("C"))
                                    add(JsonPrimitive("D"))
                                    add(JsonPrimitive("E"))
                                })
                            })
                            add(buildJsonObject { put("type", "null") })
                        })
                    })
                    put("imageUrl", buildJsonObject {
                        put("anyOf", buildJsonArray {
                            add(buildJsonObject { put("type", "string") })
                            add(buildJsonObject { put("type", "null") })
                        })
                    })
                })
            })
        })
    }

    fun buildExtractionMessages(html: String, sourceUrl: String?): List<ChatMessage> {
        val urlHint = if (!sourceUrl.isNullOrBlank()) "Zdroj: $sourceUrl\n\n" else ""
        val jsonLd = extractJsonLd(html)
        val jsonLdSection = if (jsonLd.isNotBlank()) "=== JSON-LD strukturovaná data ===\n$jsonLd\n\n=== HTML stránky ===\n" else ""
        val htmlSnippet = html.take(24_000)
        return listOf(
            ChatMessage("system", SYSTEM_EXTRACTION),
            ChatMessage("user", "$urlHint$jsonLdSection$htmlSnippet"),
        )
    }

    private fun extractJsonLd(html: String): String {
        val pattern = Regex(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return pattern.findAll(html)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    fun buildSummaryMessages(title: String, ingredients: List<String>, steps: List<String>): List<ChatMessage> {
        val text = buildString {
            appendLine("Název: $title")
            if (ingredients.isNotEmpty()) appendLine("Ingredience: ${ingredients.take(10).joinToString(", ")}")
            if (steps.isNotEmpty()) appendLine("Postup: ${steps.take(3).joinToString(". ")}")
        }
        return listOf(
            ChatMessage("system", "Napiš 2–3 věty česky shrnující přiložený recept. Odpovídej pouze textem shrnutí, bez uvozovek."),
            ChatMessage("user", text),
        )
    }

    fun buildDifficultyMessages(title: String, ingredientCount: Int, stepCount: Int): List<ChatMessage> {
        val text = "Název: $title. Počet ingrediencí: $ingredientCount. Počet kroků: $stepCount."
        return listOf(
            ChatMessage("system", "Odhadni náročnost receptu. Odpovídej POUZE jedním slovem: Jednoduché, Střední nebo Náročné. Žádný jiný text."),
            ChatMessage("user", text),
        )
    }
}
