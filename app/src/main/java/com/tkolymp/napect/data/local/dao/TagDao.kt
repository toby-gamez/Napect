package com.tkolymp.napect.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tkolymp.napect.data.local.entity.TagEntity

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun getAllTags(): kotlinx.coroutines.flow.Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE group_name = :group ORDER BY name COLLATE NOCASE ASC")
    fun getTagsByGroup(group: String): kotlinx.coroutines.flow.Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<TagEntity>): List<Long>
}
