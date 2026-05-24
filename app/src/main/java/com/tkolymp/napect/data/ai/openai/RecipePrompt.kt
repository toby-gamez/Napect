package com.tkolymp.napect.data.ai.openai

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RecipePrompt {
    private const val SYSTEM_EXTRACTION = """Jsi extraktor receptů. Z přiloženého HTML/textu extrahuj recept v češtině jako JSON podle daného schématu.
Pokud chybí pole, vrať prázdné pole nebo null.
Ingredience seskup do logických sekcí (těsto, krém, ozdoba…). Krok = jedna instrukce.
Náročnost odhadni z počtu kroků a ingrediencí: do 5 kroků a 6 ingrediencí = Jednoduché, nad 8 kroků nebo 12 ingrediencí = Náročné, jinak Střední.
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
                    add(JsonPrimitive("description"))
                    add(JsonPrimitive("ingredientGroups"))
                    add(JsonPrimitive("steps"))
                    add(JsonPrimitive("difficulty"))
                })
                put("properties", buildJsonObject {
                    put("title", buildJsonObject { put("type", "string") })
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
                                    put("items", buildJsonObject { put("type", "string") })
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
                })
            })
        })
    }

    fun buildExtractionMessages(html: String, sourceUrl: String?): List<ChatMessage> {
        val urlHint = if (!sourceUrl.isNullOrBlank()) "Zdroj: $sourceUrl\n\n" else ""
        return listOf(
            ChatMessage("system", SYSTEM_EXTRACTION),
            ChatMessage("user", "$urlHint${html.take(30_000)}"),
        )
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
