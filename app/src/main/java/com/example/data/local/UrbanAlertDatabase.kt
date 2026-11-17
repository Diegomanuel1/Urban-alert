package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DraftEntity::class,
        ConversationEntity::class,
        ConversationMessageEntity::class,
        LocalMediaEntity::class,
        PendingSyncEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class UrbanAlertDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun conversationDao(): ConversationDao
    abstract fun localMediaDao(): LocalMediaDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        private const val DB_NAME = "urban_alert_offline.db"

        @Volatile
        private var instance: UrbanAlertDatabase? = null

        fun getInstance(context: Context): UrbanAlertDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UrbanAlertDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
        }
    }
}
