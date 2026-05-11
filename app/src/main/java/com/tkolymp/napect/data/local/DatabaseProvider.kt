package com.tkolymp.napect.data.local

import android.content.Context
import androidx.room.Room

object DatabaseProvider {
    @Volatile
    private var INSTANCE: NapectDatabase? = null

    fun getDatabase(context: Context): NapectDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(context.applicationContext, NapectDatabase::class.java, "napect.db").build()
            INSTANCE = instance
            instance
        }
    }
}
