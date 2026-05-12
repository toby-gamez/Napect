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
                    val inserts = listOf(
                        // DIFFICULTY
                        Pair("Easy", "DIFFICULTY"),
                        Pair("Medium", "DIFFICULTY"),
                        Pair("Hard", "DIFFICULTY"),
                        // TIME
                        Pair("15 min", "TIME"),
                        Pair("30 min", "TIME"),
                        Pair("1 h", "TIME"),
                        Pair("2 h+", "TIME"),
                        // DIET
                        Pair("Vegan", "DIET"),
                        Pair("Vegetarian", "DIET"),
                        Pair("Gluten-Free", "DIET"),
                        Pair("Dairy-Free", "DIET"),
                        // CUISINE
                        Pair("Italian", "CUISINE"),
                        Pair("Chinese", "CUISINE"),
                        Pair("Mexican", "CUISINE"),
                        Pair("Indian", "CUISINE"),
                        Pair("French", "CUISINE"),
                        Pair("Czech", "CUISINE"),
                        Pair("American", "CUISINE"),
                        Pair("Japanese", "CUISINE"),
                        // METHOD
                        Pair("Fried", "METHOD"),
                        Pair("Baked", "METHOD"),
                        Pair("Grilled", "METHOD"),
                        Pair("Steamed", "METHOD"),
                        Pair("Raw", "METHOD"),
                        // MEAL
                        Pair("Breakfast", "MEAL"),
                        Pair("Lunch", "MEAL"),
                        Pair("Dinner", "MEAL"),
                        Pair("Snack", "MEAL"),
                        // OTHER
                        Pair("Kid-Friendly", "OTHER"),
                        Pair("One Pot", "OTHER"),
                        Pair("Meal Prep", "OTHER"),
                        Pair("Budget", "OTHER"),
                        Pair("Holiday", "OTHER")
                    )
                    database.beginTransaction()
                    try {
                        val stmt = database.compileStatement("INSERT OR IGNORE INTO tags(name, group_name, is_ai_generated, is_user_created) VALUES (?, ?, 0, 0)")
                        for ((name, group) in inserts) {
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
                    val inserts = listOf(
                        // DIFFICULTY
                        Pair("Easy", "DIFFICULTY"),
                        Pair("Medium", "DIFFICULTY"),
                        Pair("Hard", "DIFFICULTY"),
                        // TIME
                        Pair("15 min", "TIME"),
                        Pair("30 min", "TIME"),
                        Pair("1 h", "TIME"),
                        Pair("2 h+", "TIME"),
                        // DIET
                        Pair("Vegan", "DIET"),
                        Pair("Vegetarian", "DIET"),
                        Pair("Gluten-Free", "DIET"),
                        Pair("Dairy-Free", "DIET"),
                        // CUISINE
                        Pair("Italian", "CUISINE"),
                        Pair("Chinese", "CUISINE"),
                        Pair("Mexican", "CUISINE"),
                        Pair("Indian", "CUISINE"),
                        Pair("French", "CUISINE"),
                        Pair("Czech", "CUISINE"),
                        Pair("American", "CUISINE"),
                        Pair("Japanese", "CUISINE"),
                        // METHOD
                        Pair("Fried", "METHOD"),
                        Pair("Baked", "METHOD"),
                        Pair("Grilled", "METHOD"),
                        Pair("Steamed", "METHOD"),
                        Pair("Raw", "METHOD"),
                        // MEAL
                        Pair("Breakfast", "MEAL"),
                        Pair("Lunch", "MEAL"),
                        Pair("Dinner", "MEAL"),
                        Pair("Snack", "MEAL"),
                        // OTHER
                        Pair("Kid-Friendly", "OTHER"),
                        Pair("One Pot", "OTHER"),
                        Pair("Meal Prep", "OTHER"),
                        Pair("Budget", "OTHER"),
                        Pair("Holiday", "OTHER")
                    )

                    db.beginTransaction()
                    try {
                        val stmt = db.compileStatement("INSERT OR IGNORE INTO tags(name, group_name, is_ai_generated, is_user_created) VALUES (?, ?, 0, 0)")
                        for ((name, group) in inserts) {
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
