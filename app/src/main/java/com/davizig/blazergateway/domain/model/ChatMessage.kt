package com.davizig.blazergateway.domain.model

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val imageBase64: String? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val isStreaming: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String,
    val title: String,
    val systemPrompt: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val providerId: String = "openrouter",
    val modelId: String = "auto",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class HealthStatus {
    UNTESTED,
    TESTING,
    HEALTHY,
    ERROR
}

data class ProviderHealth(
    val providerId: String,
    val status: HealthStatus,
    val latencyMs: Long? = null,
    val errorMessage: String? = null
)
