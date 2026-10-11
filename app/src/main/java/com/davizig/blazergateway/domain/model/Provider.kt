package com.davizig.blazergateway.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Provider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val chatEndpoint: String = "/chat/completions",
    val requiresKey: Boolean = true,
    val isCloudflare: Boolean = false,
    val isCustom: Boolean = false,
    val isAiHorde: Boolean = false,
    val enabled: Boolean = true,
    val selectedModelId: String? = null,
    val customAuthHeader: String? = null,
    val defaultApiKey: String? = null
) {
    fun getResolvedBaseUrl(cloudflareAccountId: String?): String {
        return if (isCloudflare) {
            val accountId = if (!cloudflareAccountId.isNullOrBlank()) cloudflareAccountId else "{ACCOUNT_ID}"
            baseUrl.replace("{ACCOUNT_ID}", accountId)
        } else {
            baseUrl
        }
    }

    companion object {
        val DEFAULT_PROVIDERS = listOf(
            // Provedores com plano gratuito anônimo (Sem chave obrigatória)
            Provider(
                id = "kilo",
                name = "Kilo",
                baseUrl = "https://api.kilo.ai/api/gateway",
                chatEndpoint = "/chat/completions",
                requiresKey = false
            ),
            Provider(
                id = "ovh",
                name = "OVH Kepler",
                baseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1",
                chatEndpoint = "/chat/completions",
                requiresKey = false
            ),
            Provider(
                id = "pollinations",
                name = "Pollinations",
                baseUrl = "https://gen.pollinations.ai/v1",
                chatEndpoint = "/chat/completions",
                requiresKey = false
            ),
            Provider(
                id = "aihorde",
                name = "AI Horde",
                baseUrl = "https://aihorde.net/api/v2",
                chatEndpoint = "/generate/text/async",
                requiresKey = false,
                isAiHorde = true,
                defaultApiKey = "0000000000"
            ),

            // Provedores padrão compatíveis com OpenAI (Requer chave gratuita do usuário)
            Provider(
                id = "openrouter",
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                requiresKey = true
            ),
            Provider(
                id = "groq",
                name = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                requiresKey = true
            ),
            Provider(
                id = "cerebras",
                name = "Cerebras",
                baseUrl = "https://api.cerebras.ai/v1",
                requiresKey = true
            ),
            Provider(
                id = "google_gemini",
                name = "Google Gemini",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
                requiresKey = true
            ),
            Provider(
                id = "nvidia",
                name = "NVIDIA NIM",
                baseUrl = "https://integrate.api.nvidia.com/v1",
                requiresKey = true
            ),
            Provider(
                id = "siliconflow",
                name = "SiliconFlow",
                baseUrl = "https://api.siliconflow.cn/v1",
                requiresKey = true
            ),
            Provider(
                id = "huggingface",
                name = "HuggingFace",
                baseUrl = "https://api-inference.huggingface.co/v1",
                requiresKey = true
            ),
            Provider(
                id = "cloudflare",
                name = "Cloudflare AI",
                baseUrl = "https://api.cloudflare.com/client/v4/accounts/{ACCOUNT_ID}/ai/v1",
                requiresKey = true,
                isCloudflare = true
            ),
            Provider(
                id = "agnes_ai",
                name = "Agnes AI",
                baseUrl = "https://apihub.agnes-ai.com/v1",
                requiresKey = true
            ),
            Provider(
                id = "anyapi",
                name = "AnyAPI",
                baseUrl = "https://api.anyapi.ai/v1",
                requiresKey = true
            )
        )
    }
}
