package com.tkolymp.napect.data.network

data class ImportedRecipeData(
    val title: String,
    val description: String? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val sourceUrl: String? = null
)
