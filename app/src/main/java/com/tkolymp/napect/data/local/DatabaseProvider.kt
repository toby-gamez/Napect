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

            // Migration from v2 -> v3: adds tags + recipe_tags tables.
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
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_name ON tags(name COLLATE NOCASE)")
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

            // Migration from v3 -> v4: introduce ingredient_groups table and rework
            // ingredients to reference groups instead of recipes directly.
            val migration3to4 = object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 1. Create ingredient_groups table
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `ingredient_groups` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `recipe_id` INTEGER NOT NULL,
                          `name` TEXT NOT NULL,
                          `sort_order` INTEGER NOT NULL DEFAULT 0,
                          FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS idx_ingredient_groups_recipe_id ON ingredient_groups(recipe_id)"
                    )

                    // 2. Insert a default (unnamed) group for every existing recipe
                    database.execSQL(
                        "INSERT INTO ingredient_groups (recipe_id, name, sort_order) SELECT id, '', 0 FROM recipes"
                    )

                    // 3. Create new ingredients table referencing group_id
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `ingredients_new` (
                          `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                          `group_id` INTEGER NOT NULL,
                          `amount` REAL NOT NULL DEFAULT 0.0,
                          `unit` TEXT,
                          `name` TEXT NOT NULL,
                          `sort_order` INTEGER NOT NULL DEFAULT 0,
                          FOREIGN KEY(`group_id`) REFERENCES `ingredient_groups`(`id`) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    // 4. Migrate existing ingredient rows into the new table, mapping
                    //    each ingredient to the default group that was just created for
                    //    its recipe. At this point every recipe has exactly one group.
                    database.execSQL(
                        """
                        INSERT INTO ingredients_new (id, group_id, amount, unit, name, sort_order)
                        SELECT i.id, ig.id, i.amount, i.unit, i.name, i.sort_order
                        FROM ingredients i
                        JOIN ingredient_groups ig ON ig.recipe_id = i.recipe_id
                        """.trimIndent()
                    )

                    // 5. Replace old table with new one
                    database.execSQL("DROP TABLE IF EXISTS ingredients")
                    database.execSQL("ALTER TABLE ingredients_new RENAME TO ingredients")
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS idx_ingredients_group_id ON ingredients(group_id)"
                    )
                }
            }

            // Migration from v4 -> v5: fix index names so they match Room's auto-generated
            // convention (index_<table>_<col>) instead of the custom idx_ prefix used in
            // migration3to4.  This mismatch caused Room's post-migration schema validation
            // to throw IllegalStateException, silently making every Flow emit an empty list.
            // Using DROP+CREATE with IF EXISTS / IF NOT EXISTS makes this safe for both
            // users who ran migration3to4 (idx_ prefix) and any fresh-install v4 users
            // (index_ prefix, so CREATE is a no-op because the right index already exists).
            val migration4to5 = object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Fix ingredient_groups index
                    database.execSQL("DROP INDEX IF EXISTS `idx_ingredient_groups_recipe_id`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredient_groups_recipe_id` ON `ingredient_groups`(`recipe_id`)")
                    // Fix ingredients index
                    database.execSQL("DROP INDEX IF EXISTS `idx_ingredients_group_id`")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredients_group_id` ON `ingredients`(`group_id`)")
                }
            }

            // Migration from v5 -> v6: fix the tags table unique index.
            // migration2to3 created idx_tags_name but TagEntity declares @Index so Room
            // auto-generates index_tags_name.  The name mismatch (idx_ vs index_) caused
            // onValidateSchema to fail with IllegalStateException on every DB open, which
            // was silently swallowed by .catch { emit(emptyList()) } making all tags
            // invisible.  Drop the old name and create the Room-expected one.
            val migration5to6 = object : Migration(5, 6) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("DROP INDEX IF EXISTS `idx_tags_name`")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags`(`name`)")
                }
            }

            // Seed callback: insert default tags on fresh database creation.
            // NOTE: do NOT create idx_tags_name here — Room now manages the index via
            // the @Index annotation on TagEntity (generates index_tags_name via createAllTables).
            val callback = object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
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
            )
                .addMigrations(migration2to3, migration3to4, migration4to5, migration5to6)
                .addCallback(callback)
                .build()
            INSTANCE = instance
            instance
        }
    }
}
