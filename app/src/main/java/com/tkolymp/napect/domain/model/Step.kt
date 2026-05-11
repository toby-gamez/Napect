package com.tkolymp.napect.domain.model

data class Step(
    val id: Long = 0L,
    val recipeId: Long = 0L,
    val stepNumber: Int = 0,
    val instruction: String,
)
