package com.davizig.blazergateway.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val systemPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    val providerId: String,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val imageBase64: String?,
    val providerId: String?,
    val modelId: String?,
    val isStreaming: Boolean,
    val createdAt: Long
)

@Entity(tableName = "cached_models", primaryKeys = ["id", "providerId"])
data class CachedModelEntity(
    val id: String,
    val providerId: String,
    val name: String,
    val isFree: Boolean,
    val hasVision: Boolean,
    val contextLength: Int,
    val promptPrice: String?,
    val completionPrice: String?,
    val lastUpdated: Long
)

@Entity(tableName = "usage_trackers", primaryKeys = ["providerId", "modelId"])
data class UsageTrackerEntity(
    val providerId: String,
    val modelId: String,
    val requestsLastMinute: Int,
    val minuteWindowStart: Long,
    val dailyRequestsCount: Int,
    val dailyDateString: String,
    val consecutiveFailures: Int,
    val cooldownUntil: Long
)
