package com.davizig.blazergateway.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.davizig.blazergateway.data.local.entity.CachedModelEntity
import com.davizig.blazergateway.data.local.entity.ConversationEntity
import com.davizig.blazergateway.data.local.entity.MessageEntity
import com.davizig.blazergateway.data.local.entity.UsageTrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getConversationById(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String)
}

@Dao
interface CachedModelDao {
    @Query("SELECT * FROM cached_models WHERE providerId = :providerId ORDER BY name ASC")
    fun getModelsForProviderFlow(providerId: String): Flow<List<CachedModelEntity>>

    @Query("SELECT * FROM cached_models WHERE providerId = :providerId ORDER BY name ASC")
    suspend fun getModelsForProvider(providerId: String): List<CachedModelEntity>

    @Query("SELECT * FROM cached_models ORDER BY name ASC")
    fun getAllModelsFlow(): Flow<List<CachedModelEntity>>

    @Query("SELECT * FROM cached_models ORDER BY name ASC")
    suspend fun getAllModels(): List<CachedModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<CachedModelEntity>)

    @Query("DELETE FROM cached_models WHERE providerId = :providerId")
    suspend fun clearForProvider(providerId: String)

    @Query("SELECT MAX(lastUpdated) FROM cached_models WHERE providerId = :providerId")
    suspend fun getLastUpdatedForProvider(providerId: String): Long?
}

@Dao
interface UsageTrackerDao {
    @Query("SELECT * FROM usage_trackers WHERE providerId = :providerId AND modelId = :modelId LIMIT 1")
    suspend fun getTracker(providerId: String, modelId: String): UsageTrackerEntity?

    @Query("SELECT * FROM usage_trackers WHERE providerId = :providerId")
    suspend fun getTrackersForProvider(providerId: String): List<UsageTrackerEntity>

    @Query("SELECT * FROM usage_trackers")
    fun getAllTrackersFlow(): Flow<List<UsageTrackerEntity>>

    @Query("SELECT * FROM usage_trackers")
    suspend fun getAllTrackers(): List<UsageTrackerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tracker: UsageTrackerEntity)
}
