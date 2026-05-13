package com.tkolymp.napect.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase

object DatabaseProvider {
    @Volatile
    private var INSTANCE: NapectDatabase? = null

    fun getDatabase(context: Context): NapectDatabase {
        return INSTANCE ?: synchronized(this) {
            // Use centralized default seed tags list
            val seedInserts = com.tkolymp.napect.data.local.DEFAULT_TAGS.map { (name, group) -> Pair(name, group.name) }

            // Provide an explicit migration from v2 -> v3 that adds tags tables.
            val migration2to3 = object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Create tags table
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `tags` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `name` TEXT NOT NULL,
                          `group_name` TEXT NOT NULL,
                          `is_ai_generated` INTEGER NOT NULL DEFAULT 0,
                          `is_user_created` INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                    // Ensure a case-insensitive unique index on tag names so duplicate tags are avoided
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_name ON tags(name COLLATE NOCASE)")

                    // Create junction table recipe_tags WITHOUT explicit foreign key constraints so it
                    // matches the Room-generated schema for the simple cross-ref entity.
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `recipe_tags` (
                          `recipe_id` INTEGER NOT NULL,
                          `tag_id` INTEGER NOT NULL,
                          PRIMARY KEY(`recipe_id`, `tag_id`)
                        )
                        """.trimIndent()
                    )
                    // Insert seed tags during migration so upgraded databases also get defaults
                    database.beginTransaction()
                    try {
                        val stmt = database.compileStatement("INSERT OR IGNORE INTO tags(name, group_name, is_ai_generated, is_user_created) VALUES (?, ?, 0, 0)")
                        for ((name, group) in seedInserts) {
                            stmt.bindString(1, name)
                            stmt.bindString(2, group)
                            stmt.executeInsert()
                        }
                        database.setTransactionSuccessful()
                    } finally {
                        database.endTransaction()
                    }
                }
            }

            // Seed callback to insert default tags on fresh database creation
            val callback = object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Insert seed tags
                    // Ensure unique index on name for fresh DB so seed inserts won't duplicate
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_name ON tags(name COLLATE NOCASE)")
                    db.beginTransaction()
                    try {
                        val stmt = db.compileStatement("INSERT OR IGNORE INTO tags(name, group_name, is_ai_generated, is_user_created) VALUES (?, ?, 0, 0)")
                        for ((name, group) in seedInserts) {
                            stmt.bindString(1, name)
                            stmt.bindString(2, group)
                            stmt.executeInsert()
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }

            val instance = Room.databaseBuilder(
                context.applicationContext,
                NapectDatabase::class.java,
                "napect.db"
            ).addMigrations(migration2to3).addCallback(callback).build()
            INSTANCE = instance
            instance
        }
    }
}
