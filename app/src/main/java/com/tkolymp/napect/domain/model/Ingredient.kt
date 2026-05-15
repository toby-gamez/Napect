package com.tkolymp.napect.domain.model

data class Ingredient(
    val id: Long = 0L,
    val groupId: Long = 0L,
    val amount: Double = 0.0,
    val unit: String? = null,
    val name: String,
    val sortOrder: Int = 0,
)
