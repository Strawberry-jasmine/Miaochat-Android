package com.example.relaychat.data.threads

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ThreadEntity::class, MessageEntity::class, AttachmentEntity::class],
    version = 2,
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
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN imageGenerationJson TEXT")
                db.execSQL("ALTER TABLE attachments ADD COLUMN filePath TEXT")
            }
        }
    }
}
