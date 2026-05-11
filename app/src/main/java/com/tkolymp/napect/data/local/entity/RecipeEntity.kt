package com.tkolymp.napect.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val summary: String? = null,
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    @ColumnInfo(name = "source_note") val sourceNote: String? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    val category: String? = null,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val photo: ByteArray? = null,
    @ColumnInfo(name = "servings_base") val servingsBase: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
