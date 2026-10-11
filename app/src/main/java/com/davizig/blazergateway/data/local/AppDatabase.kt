package com.davizig.blazergateway.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.davizig.blazergateway.data.local.dao.CachedModelDao
import com.davizig.blazergateway.data.local.dao.ConversationDao
import com.davizig.blazergateway.data.local.dao.MessageDao
import com.davizig.blazergateway.data.local.dao.UsageTrackerDao
import com.davizig.blazergateway.data.local.entity.CachedModelEntity
import com.davizig.blazergateway.data.local.entity.ConversationEntity
import com.davizig.blazergateway.data.local.entity.MessageEntity
import com.davizig.blazergateway.data.local.entity.UsageTrackerEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        CachedModelEntity::class,
        UsageTrackerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun cachedModelDao(): CachedModelDao
    abstract fun usageTrackerDao(): UsageTrackerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blazergateway.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
