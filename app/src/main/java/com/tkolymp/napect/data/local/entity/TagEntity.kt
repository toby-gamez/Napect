package com.tkolymp.napect.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "group_name") val group: String,
    @ColumnInfo(name = "is_ai_generated") val isAiGenerated: Int = 0,
    @ColumnInfo(name = "is_user_created") val isUserCreated: Int = 0,
)
