package com.davizig.blazergateway.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpenAIModelsResponse(
    val data: List<OpenAIModelDto> = emptyList(),
    val models: List<OpenAIModelDto>? = null // Alguns gateways retornam 'models'
)

@Serializable
data class OpenAIModelDto(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val context_length: Int? = null,
    val pricing: OpenAIModelPricingDto? = null,
    val free: Boolean? = null,
    val tags: List<String>? = null,
    val architecture: OpenAIModelArchDto? = null
)

@Serializable
data class OpenAIModelPricingDto(
    val prompt: String? = null,
    val completion: String? = null
)

@Serializable
data class OpenAIModelArchDto(
    val modality: String? = null
)

@Serializable
data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIMessageDto>,
    val stream: Boolean = true,
    val temperature: Float = 0.7f,
    val max_tokens: Int = 2048
)

@Serializable
data class OpenAIMessageDto(
    val role: String,
    val content: JsonElement
)
