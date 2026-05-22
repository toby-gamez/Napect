package com.tkolymp.napect.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private lateinit var context: Context
    private val dbName = "test_migration.db"
    private var migratedDb: NapectDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        migratedDb?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate2to3_addsTagsAndRecipeTags() {
        createDbAtVersion(2, *SQL_V2)
        insertSql("INSERT INTO recipes (title, is_favorite, servings_base, created_at, updated_at) VALUES ('Test recipe', 0, 1, 1, 1)")
        runMigrations(*ALL_MIGRATIONS)

        assertTrue("Expected default tags", queryInt("SELECT COUNT(*) FROM tags") > 0)
        exec("INSERT INTO recipe_tags (recipe_id, tag_id) VALUES (1, 1)")
        assertEquals(1, queryInt("SELECT COUNT(*) FROM recipe_tags WHERE recipe_id = 1"))
    }

    @Test
    fun migrate3to4_addsIngredientGroupsAndMigratesData() {
        createDbAtVersion(3, *SQL_V3)
        insertSql("INSERT INTO recipes (title, is_favorite, servings_base, created_at, updated_at) VALUES ('Test', 0, 1, 1, 1)")
        insertSql("INSERT INTO ingredients (recipe_id, amount, unit, name, sort_order) VALUES (1, 2.5, 'cups', 'flour', 1)")
        runMigrations(*ALL_MIGRATIONS)

        val c = query("SELECT id, recipe_id, name FROM ingredient_groups WHERE recipe_id = 1")
        c.moveToFirst()
        val groupId = c.getLong(0)
        assertEquals(1, c.getLong(1))
        assertEquals("", c.getString(2))
        c.close()

        val c2 = query("SELECT group_id, amount, name FROM ingredients WHERE id = 1")
        c2.moveToFirst()
        assertEquals(groupId, c2.getLong(0))
        assertEquals(2.5, c2.getDouble(1), 0.001)
        assertEquals("flour", c2.getString(2))
        c2.close()
    }

    @Test
    fun migrate4to5_fixesIndexNames() {
        createDbAtVersion(4, *SQL_V4)
        runMigrations(*ALL_MIGRATIONS)
        assertIndexExists("index_ingredient_groups_recipe_id")
        assertIndexExists("index_ingredients_group_id")
    }

    @Test
    fun migrate5to6_fixesTagsIndexNameAndAddsRecipeTagIndices() {
        createDbAtVersion(5, *SQL_V5)
        runMigrations(*ALL_MIGRATIONS)
        assertIndexMissing("idx_tags_name")
        assertIndexExists("index_tags_name")
        assertIndexExists("index_recipe_tags_recipe_id")
        assertIndexExists("index_recipe_tags_tag_id")
    }

    @Test
    fun migrate6to7_addsPhotoPathColumn() {
        createDbAtVersion(6, *SQL_V6)
        insertSql("INSERT INTO recipes (title, is_favorite, servings_base, created_at, updated_at) VALUES ('Test', 0, 1, 1, 1)")
        runMigrations(*ALL_MIGRATIONS)
        exec("UPDATE recipes SET photo_path = 'test.jpg' WHERE id = 1")
        val c = query("SELECT photo_path FROM recipes WHERE id = 1")
        c.moveToFirst()
        assertEquals("test.jpg", c.getString(0))
        c.close()
    }

    @Test
    fun migrate7to8_addsFts4TableAndPopulates() {
        createDbAtVersion(7, *SQL_V7)
        insertSql("INSERT INTO recipes (title, summary, is_favorite, servings_base, created_at, updated_at) VALUES ('Test recipe', 'A test summary', 0, 1, 1, 1)")
        runMigrations(*ALL_MIGRATIONS)
        assertTrue("FTS table should exist", queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='recipe_fts'") > 0)
        assertEquals("FTS should have 1 row", 1, queryInt("SELECT COUNT(*) FROM recipe_fts"))
        assertTrue("FTS MATCH should find 'test'", queryInt("SELECT COUNT(*) FROM recipe_fts WHERE recipe_fts MATCH 'test'") > 0)
    }

    @Test
    fun migrateFullChain2to6_preservesData() {
        createDbAtVersion(2, *SQL_V2)
        insertSql("INSERT INTO recipes (title, summary, is_favorite, servings_base, created_at, updated_at) VALUES ('Pancakes', 'Fluffy pancakes', 1, 4, 1, 1)")
        insertSql("INSERT INTO ingredients (recipe_id, amount, unit, name, sort_order) VALUES (1, 2.0, 'cups', 'flour', 1)")
        insertSql("INSERT INTO ingredients (recipe_id, amount, unit, name, sort_order) VALUES (1, 1.0, 'tbsp', 'sugar', 2)")
        insertSql("INSERT INTO steps (recipe_id, step_number, instruction) VALUES (1, 1, 'Mix dry ingredients')")
        insertSql("INSERT INTO steps (recipe_id, step_number, instruction) VALUES (1, 2, 'Add milk and eggs')")
        runMigrations(*ALL_MIGRATIONS)

        val c = query("SELECT title, summary, is_favorite, servings_base FROM recipes WHERE id = 1")
        c.moveToFirst()
        assertEquals("Pancakes", c.getString(0))
        assertEquals("Fluffy pancakes", c.getString(1))
        assertEquals(1, c.getInt(2))
        assertEquals(4, c.getInt(3))
        c.close()

        assertEquals(2, queryInt("SELECT COUNT(*) FROM steps WHERE recipe_id = 1"))

        val c2 = query(
            "SELECT i.name, i.amount, ig.name FROM ingredients i " +
            "JOIN ingredient_groups ig ON i.group_id = ig.id " +
            "WHERE ig.recipe_id = 1 ORDER BY i.sort_order"
        )
        c2.moveToFirst()
        assertEquals("flour", c2.getString(0))
        assertEquals(2.0, c2.getDouble(1), 0.001)
        c2.moveToNext()
        assertEquals("sugar", c2.getString(0))
        assertEquals(1.0, c2.getDouble(1), 0.001)
        c2.close()

        assertTrue("Expected default tags", queryInt("SELECT COUNT(*) FROM tags") > 0)
    }

    companion object {
        private val ALL_MIGRATIONS = arrayOf(
            DatabaseProvider.migration2to3,
            DatabaseProvider.migration3to4,
            DatabaseProvider.migration4to5,
            DatabaseProvider.migration5to6,
            DatabaseProvider.migration6to7,
            DatabaseProvider.migration7to8
        )

        private val SQL_V2 = arrayOf(
            """CREATE TABLE IF NOT EXISTS `recipes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL, `summary` TEXT, `source_url` TEXT, `source_note` TEXT,
                `is_favorite` INTEGER NOT NULL, `category` TEXT, `photo` BLOB,
                `servings_base` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL
            )""",
            """CREATE TABLE IF NOT EXISTS `ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `amount` REAL NOT NULL, `unit` TEXT,
                `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_ingredients_recipe_id` ON `ingredients`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `steps` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `step_number` INTEGER NOT NULL, `instruction` TEXT NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_steps_recipe_id` ON `steps`(`recipe_id`)"
        )

        private val SQL_V3 = SQL_V2 + arrayOf(
            """CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `group_name` TEXT NOT NULL,
                `is_ai_generated` INTEGER NOT NULL, `is_user_created` INTEGER NOT NULL
            )""",
            "CREATE UNIQUE INDEX IF NOT EXISTS `idx_tags_name` ON `tags`(`name` COLLATE NOCASE)",
            """CREATE TABLE IF NOT EXISTS `recipe_tags` (
                `recipe_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL,
                PRIMARY KEY(`recipe_id`, `tag_id`)
            )"""
        )

        private val SQL_V4 = arrayOf(
            """CREATE TABLE IF NOT EXISTS `recipes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL, `summary` TEXT, `source_url` TEXT, `source_note` TEXT,
                `is_favorite` INTEGER NOT NULL, `category` TEXT, `photo` BLOB,
                `servings_base` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL
            )""",
            """CREATE TABLE IF NOT EXISTS `ingredient_groups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `idx_ingredient_groups_recipe_id` ON `ingredient_groups`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `group_id` INTEGER NOT NULL, `amount` REAL NOT NULL, `unit` TEXT,
                `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`group_id`) REFERENCES `ingredient_groups`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `idx_ingredients_group_id` ON `ingredients`(`group_id`)",
            """CREATE TABLE IF NOT EXISTS `steps` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `step_number` INTEGER NOT NULL, `instruction` TEXT NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_steps_recipe_id` ON `steps`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `group_name` TEXT NOT NULL,
                `is_ai_generated` INTEGER NOT NULL, `is_user_created` INTEGER NOT NULL
            )""",
            "CREATE UNIQUE INDEX IF NOT EXISTS `idx_tags_name` ON `tags`(`name` COLLATE NOCASE)",
            """CREATE TABLE IF NOT EXISTS `recipe_tags` (
                `recipe_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL,
                PRIMARY KEY(`recipe_id`, `tag_id`)
            )"""
        )

        private val SQL_V6 = arrayOf(
            """CREATE TABLE IF NOT EXISTS `recipes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL, `summary` TEXT, `source_url` TEXT, `source_note` TEXT,
                `is_favorite` INTEGER NOT NULL, `category` TEXT, `photo` BLOB,
                `servings_base` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL
            )""",
            """CREATE TABLE IF NOT EXISTS `ingredient_groups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_ingredient_groups_recipe_id` ON `ingredient_groups`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `group_id` INTEGER NOT NULL, `amount` REAL NOT NULL, `unit` TEXT,
                `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`group_id`) REFERENCES `ingredient_groups`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_ingredients_group_id` ON `ingredients`(`group_id`)",
            """CREATE TABLE IF NOT EXISTS `steps` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `step_number` INTEGER NOT NULL, `instruction` TEXT NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_steps_recipe_id` ON `steps`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `group_name` TEXT NOT NULL,
                `is_ai_generated` INTEGER NOT NULL, `is_user_created` INTEGER NOT NULL
            )""",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags`(`name` COLLATE NOCASE)",
            """CREATE TABLE IF NOT EXISTS `recipe_tags` (
                `recipe_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL,
                PRIMARY KEY(`recipe_id`, `tag_id`)
            )""",
            "CREATE INDEX IF NOT EXISTS `index_recipe_tags_recipe_id` ON `recipe_tags`(`recipe_id`)",
            "CREATE INDEX IF NOT EXISTS `index_recipe_tags_tag_id` ON `recipe_tags`(`tag_id`)"
        )

        private val SQL_V7 = arrayOf(
            """CREATE TABLE IF NOT EXISTS `recipes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL, `summary` TEXT, `source_url` TEXT, `source_note` TEXT,
                `is_favorite` INTEGER NOT NULL, `category` TEXT, `photo` BLOB,
                `photo_path` TEXT, `servings_base` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL
            )""",
            """CREATE TABLE IF NOT EXISTS `ingredient_groups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_ingredient_groups_recipe_id` ON `ingredient_groups`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `group_id` INTEGER NOT NULL, `amount` REAL NOT NULL, `unit` TEXT,
                `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`group_id`) REFERENCES `ingredient_groups`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_ingredients_group_id` ON `ingredients`(`group_id`)",
            """CREATE TABLE IF NOT EXISTS `steps` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `step_number` INTEGER NOT NULL, `instruction` TEXT NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_steps_recipe_id` ON `steps`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `group_name` TEXT NOT NULL,
                `is_ai_generated` INTEGER NOT NULL, `is_user_created` INTEGER NOT NULL
            )""",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags`(`name` COLLATE NOCASE)",
            """CREATE TABLE IF NOT EXISTS `recipe_tags` (
                `recipe_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL,
                PRIMARY KEY(`recipe_id`, `tag_id`)
            )""",
            "CREATE INDEX IF NOT EXISTS `index_recipe_tags_recipe_id` ON `recipe_tags`(`recipe_id`)",
            "CREATE INDEX IF NOT EXISTS `index_recipe_tags_tag_id` ON `recipe_tags`(`tag_id`)"
        )

        private val SQL_V5 = arrayOf(
            """CREATE TABLE IF NOT EXISTS `recipes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL, `summary` TEXT, `source_url` TEXT, `source_note` TEXT,
                `is_favorite` INTEGER NOT NULL, `category` TEXT, `photo` BLOB,
                `servings_base` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL
            )""",
            """CREATE TABLE IF NOT EXISTS `ingredient_groups` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_ingredient_groups_recipe_id` ON `ingredient_groups`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `ingredients` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `group_id` INTEGER NOT NULL, `amount` REAL NOT NULL, `unit` TEXT,
                `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL,
                FOREIGN KEY(`group_id`) REFERENCES `ingredient_groups`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_ingredients_group_id` ON `ingredients`(`group_id`)",
            """CREATE TABLE IF NOT EXISTS `steps` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `recipe_id` INTEGER NOT NULL, `step_number` INTEGER NOT NULL, `instruction` TEXT NOT NULL,
                FOREIGN KEY(`recipe_id`) REFERENCES `recipes`(`id`) ON DELETE CASCADE
            )""",
            "CREATE INDEX IF NOT EXISTS `index_steps_recipe_id` ON `steps`(`recipe_id`)",
            """CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL, `group_name` TEXT NOT NULL,
                `is_ai_generated` INTEGER NOT NULL, `is_user_created` INTEGER NOT NULL
            )""",
            "CREATE UNIQUE INDEX IF NOT EXISTS `idx_tags_name` ON `tags`(`name` COLLATE NOCASE)",
            """CREATE TABLE IF NOT EXISTS `recipe_tags` (
                `recipe_id` INTEGER NOT NULL, `tag_id` INTEGER NOT NULL,
                PRIMARY KEY(`recipe_id`, `tag_id`)
            )"""
        )
    }

    // ---- helpers ----

    private fun createDbAtVersion(version: Int, vararg stmts: String) {
        val raw = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL("PRAGMA user_version = $version")
        for (sql in stmts) raw.execSQL(sql)
        raw.close()
    }

    private fun runMigrations(vararg migrations: Migration) {
        migratedDb = Room.databaseBuilder(context, NapectDatabase::class.java, dbName)
            .addMigrations(*migrations)
            .build()
        migratedDb!!.openHelper.writableDatabase
    }

    private fun insertSql(sql: String) {
        val raw = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.execSQL(sql)
        raw.close()
    }

    private fun queryInt(sql: String): Int {
        val db = migratedDb ?: throw IllegalStateException("Migrations not run yet")
        val c = db.openHelper.writableDatabase.query(sql)
        c.moveToFirst()
        val result = c.getInt(0)
        c.close()
        return result
    }

    private fun query(sql: String): android.database.Cursor {
        val db = migratedDb ?: throw IllegalStateException("Migrations not run yet")
        return db.openHelper.writableDatabase.query(sql)
    }

    private fun exec(sql: String) {
        val db = migratedDb ?: throw IllegalStateException("Migrations not run yet")
        db.openHelper.writableDatabase.execSQL(sql)
    }

    private fun assertIndexExists(name: String) =
        assertTrue("Expected index '$name'", queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='$name'") > 0)

    private fun assertIndexMissing(name: String) =
        assertEquals("Expected index '$name' absent", 0, queryInt("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='$name'"))
}
