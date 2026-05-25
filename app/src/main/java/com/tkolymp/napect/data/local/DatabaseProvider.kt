package com.tkolymp.napect.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import timber.log.Timber

object DatabaseProvider {
    @Volatile
    private var INSTANCE: NapectDatabase? = null

    // Migration from v2 -> v3: adds tags + recipe_tags tables.
    val migration2to3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val seedInserts = DEFAULT_TAGS.map { (name, group) -> Pair(name, group.name) }
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
            database.execSQL(
                "INSERT INTO ingredient_groups (recipe_id, name, sort_order) SELECT id, '', 0 FROM recipes"
            )
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
            database.execSQL(
                """
                INSERT INTO ingredients_new (id, group_id, amount, unit, name, sort_order)
                SELECT i.id, ig.id, i.amount, i.unit, i.name, i.sort_order
                FROM ingredients i
                JOIN ingredient_groups ig ON ig.recipe_id = i.recipe_id
                """.trimIndent()
            )
            database.execSQL("DROP TABLE IF EXISTS ingredients")
            database.execSQL("ALTER TABLE ingredients_new RENAME TO ingredients")
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ingredients_group_id ON ingredients(group_id)"
            )
        }
    }

    // Migration from v4 -> v5: fix index names so they match Room's auto-generated
    // convention (index_<table>_<col>) instead of the custom idx_ prefix used in
    // migration3to4.
    val migration4to5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP INDEX IF EXISTS `idx_ingredient_groups_recipe_id`")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredient_groups_recipe_id` ON `ingredient_groups`(`recipe_id`)")
            database.execSQL("DROP INDEX IF EXISTS `idx_ingredients_group_id`")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ingredients_group_id` ON `ingredients`(`group_id`)")
        }
    }

    // Migration from v5 -> v6: fix the tags table unique index.
    val migration5to6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP INDEX IF EXISTS `idx_tags_name`")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags`(`name`)")
            // Add indices on recipe_tags (added when @Index was declared on RecipeTagCrossRef)
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_tags_recipe_id` ON `recipe_tags`(`recipe_id`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_tags_tag_id` ON `recipe_tags`(`tag_id`)")
        }
    }

    // Migration from v6 -> v7: add photo_path column for file-based photo storage.
    val migration6to7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `recipes` ADD COLUMN `photo_path` TEXT DEFAULT NULL")
        }
    }

    // Migration from v7 -> v8: add FTS4 virtual table for full-text search.
    // NOTE: Do NOT create manual triggers — Room's @Fts4(contentEntity = ...)
    // auto-generates them and having both causes duplicate inserts on UPDATE.
    val migration7to8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `recipe_fts` USING fts4(" +
                "content=`recipes`, title, summary)"
            )
            // Populate existing data
            database.execSQL(
                "INSERT INTO `recipe_fts`(docid, title, summary) SELECT `id`, `title`, `summary` FROM `recipes`"
            )
        }
    }

    // Migration from v8 -> v9: drop custom FTS triggers that conflict with
    // Room's auto-generated ones (created by a bug in migration7to8).
    val migration8to9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TRIGGER IF EXISTS `recipe_fts_ai`")
            database.execSQL("DROP TRIGGER IF EXISTS `recipe_fts_ad`")
            database.execSQL("DROP TRIGGER IF EXISTS `recipe_fts_au`")
        }
    }

    // Migration from v9 -> v10: add nutritional value columns to recipes.
    val migration9to10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `recipes` ADD COLUMN `calories_kcal` REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE `recipes` ADD COLUMN `fat_g` REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE `recipes` ADD COLUMN `carbs_g` REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE `recipes` ADD COLUMN `proteins_g` REAL DEFAULT NULL")
            database.execSQL("ALTER TABLE `recipes` ADD COLUMN `nutri_score` TEXT DEFAULT NULL")
        }
    }

    // Migration from v10 -> v11: add prep/cook time columns to recipes.
    val migration10to11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `recipes` ADD COLUMN `prep_time_minutes` INTEGER DEFAULT NULL")
            database.execSQL("ALTER TABLE `recipes` ADD COLUMN `cook_time_minutes` INTEGER DEFAULT NULL")
        }
    }

    // Migration from v11 -> v12: replace prep_time_minutes + cook_time_minutes with a single
    // time_minutes column. SQLite requires a table recreation to remove columns.
    // FTS triggers that reference `recipes` are dropped with the old table and recreated here.
    val migration11to12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `recipes_new` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `title` TEXT NOT NULL,
                  `summary` TEXT,
                  `source_url` TEXT,
                  `source_note` TEXT,
                  `is_favorite` INTEGER NOT NULL,
                  `category` TEXT,
                  `photo` BLOB,
                  `photo_path` TEXT,
                  `servings_base` INTEGER NOT NULL,
                  `created_at` INTEGER NOT NULL,
                  `updated_at` INTEGER NOT NULL,
                  `calories_kcal` REAL,
                  `fat_g` REAL,
                  `carbs_g` REAL,
                  `proteins_g` REAL,
                  `nutri_score` TEXT,
                  `time_minutes` INTEGER
                )
            """.trimIndent())
            database.execSQL("""
                INSERT INTO `recipes_new`
                SELECT `id`, `title`, `summary`, `source_url`, `source_note`, `is_favorite`,
                       `category`, `photo`, `photo_path`, `servings_base`, `created_at`,
                       `updated_at`, `calories_kcal`, `fat_g`, `carbs_g`, `proteins_g`,
                       `nutri_score`,
                       CASE WHEN `prep_time_minutes` IS NULL AND `cook_time_minutes` IS NULL THEN NULL
                            ELSE COALESCE(`prep_time_minutes`, 0) + COALESCE(`cook_time_minutes`, 0)
                       END
                FROM `recipes`
            """.trimIndent())
            database.execSQL("DROP TABLE `recipes`")
            database.execSQL("ALTER TABLE `recipes_new` RENAME TO `recipes`")
            // Restore Room-generated FTS triggers that were dropped with the old table
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_recipe_fts_BEFORE_UPDATE` BEFORE UPDATE ON `recipes` BEGIN DELETE FROM `recipe_fts` WHERE `docid`=OLD.`rowid`; END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_recipe_fts_BEFORE_DELETE` BEFORE DELETE ON `recipes` BEGIN DELETE FROM `recipe_fts` WHERE `docid`=OLD.`rowid`; END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_recipe_fts_AFTER_UPDATE` AFTER UPDATE ON `recipes` BEGIN INSERT INTO `recipe_fts`(`docid`, `title`, `summary`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`summary`); END")
            database.execSQL("CREATE TRIGGER IF NOT EXISTS `room_fts_content_sync_recipe_fts_AFTER_INSERT` AFTER INSERT ON `recipes` BEGIN INSERT INTO `recipe_fts`(`docid`, `title`, `summary`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`summary`); END")
        }
    }

    fun getDatabase(context: Context): NapectDatabase {
        return INSTANCE ?: synchronized(this) {
            val callback = object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val seedInserts = DEFAULT_TAGS.map { (name, group) -> Pair(name, group.name) }
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
                .addMigrations(migration2to3, migration3to4, migration4to5, migration5to6, migration6to7, migration7to8, migration8to9, migration9to10, migration10to11, migration11to12)
                .addCallback(callback)
                .build()
            INSTANCE = instance
            migrateBlobsToFiles(context.applicationContext, instance)
            instance
        }
    }

    /**
     * One-shot migration of existing BLOB photos to files. Runs on a background thread
     * on each app launch until all existing BLOBs have been converted.
     */
    private fun migrateBlobsToFiles(context: Context, db: NapectDatabase) {
        Thread {
            try {
                val c = db.openHelper.writableDatabase.query(
                    "SELECT id, photo FROM recipes WHERE photo IS NOT NULL AND photo_path IS NULL"
                )
                val idsToUpdate = mutableListOf<Long>()
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val blob = c.getBlob(1)
                    if (blob != null && blob.isNotEmpty()) {
                        PhotoManager.savePhoto(context, id, blob)
                        idsToUpdate.add(id)
                    }
                }
                c.close()
                for (id in idsToUpdate) {
                    db.openHelper.writableDatabase.execSQL(
                        "UPDATE recipes SET photo_path = ?, photo = NULL WHERE id = ?",
                        arrayOf<Any?>(PhotoManager.getPhotoPath(context, id), id)
                    )
                }
                if (idsToUpdate.isNotEmpty()) {
                    Timber.d("Migrated %d recipe photos from BLOB to files", idsToUpdate.size)
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to migrate BLOB photos to files")
            }
        }.apply { isDaemon = true }.start()
    }
}
