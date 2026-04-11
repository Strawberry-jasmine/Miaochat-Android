package com.example.relaychat.data.threads

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ThreadEntity::class, MessageEntity::class, AttachmentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ThreadDatabase : RoomDatabase() {
    abstract fun threadDao(): ThreadDao

    companion object {
        @Volatile
        private var instance: ThreadDatabase? = null

        fun getInstance(context: Context): ThreadDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThreadDatabase::class.java,
                    "relaychat_threads.db",
                ).build().also { instance = it }
            }
    }
}
