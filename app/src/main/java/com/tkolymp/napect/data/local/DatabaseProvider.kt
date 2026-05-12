package com.tkolymp.napect.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var INSTANCE: NapectDatabase? = null

    fun getDatabase(context: Context): NapectDatabase {
        return INSTANCE ?: synchronized(this) {
            // Use destructive migration during development / early releases to avoid crashes
            // when the schema changes. For production, replace with proper Migration objects.
            val instance = Room.databaseBuilder(
                context.applicationContext,
                NapectDatabase::class.java,
                "napect.db"
            ).fallbackToDestructiveMigration().build()
            INSTANCE = instance
            instance
        }
    }
}
