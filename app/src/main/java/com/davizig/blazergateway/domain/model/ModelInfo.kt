package com.davizig.blazergateway.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelInfo(
    val id: String,
    val providerId: String,
    val name: String,
    val isFree: Boolean,
    val hasVision: Boolean,
    val contextLength: Int = 4096,
    val promptPrice: String? = null,
    val completionPrice: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
