package com.tkolymp.napect.data.local.entity

import androidx.room.ColumnInfo

data class RecipeListItemEntity(
    val id: Long,
    val title: String,
    val summary: String? = null,
    @ColumnInfo(name = "photo_path") val photoPath: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    val category: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int? = null,
)
