package com.davizig.blazergateway.util

/**
 * Parser de arquivos .env para importação de chaves de API.
 * Lê linhas no formato CHAVE=VALOR, ignora comentários (#) e linhas vazias,
 * remove aspas simples e duplas, e mapeia para os provedores suportados.
 */
object EnvParser {

    data class ParsedEnv(
        val providerKeys: Map<String, String>,
        val cloudflareAccountId: String?,
        val unmappedVariables: Map<String, String>
    )

    // Mapeamento das chaves de ambiente para IDs internos dos provedores
    private val ENV_KEY_TO_PROVIDER_ID = mapOf(
        "OPENROUTER_KEY" to "openrouter",
        "OPENROUTER_API_KEY" to "openrouter",
        "GROQ_KEY" to "groq",
        "GROQ_API_KEY" to "groq",
        "CEREBRAS_KEY" to "cerebras",
        "CEREBRAS_API_KEY" to "cerebras",
        "GOOGLE_KEY" to "google_gemini",
        "GEMINI_API_KEY" to "google_gemini",
        "GEMINI_KEY" to "google_gemini",
        "NVIDIA_KEY" to "nvidia",
        "NVIDIA_API_KEY" to "nvidia",
        "SILICONFLOW_KEY" to "siliconflow",
        "SILICONFLOW_API_KEY" to "siliconflow",
        "HF_KEY" to "huggingface",
        "HUGGINGFACE_KEY" to "huggingface",
        "HUGGINGFACE_API_KEY" to "huggingface",
        "CLOUDFLARE_KEY" to "cloudflare",
        "CLOUDFLARE_API_KEY" to "cloudflare",
        "AGNES_KEY" to "agnes_ai",
        "AGNES_API_KEY" to "agnes_ai",
        "ANYAPI_KEY" to "anyapi",
        "ANYAPI_API_KEY" to "anyapi"
    )

    fun parse(content: String): ParsedEnv {
        val providerKeys = mutableMapOf<String, String>()
        val unmapped = mutableMapOf<String, String>()
        var cloudflareAccountId: String? = null

        val lines = content.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                continue
            }

            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0) {
                continue
            }

            val rawKey = line.substring(0, separatorIndex).trim()
            var rawValue = line.substring(separatorIndex + 1).trim()

            // Remove aspas simples ou duplas envolventes
            if ((rawValue.startsWith("\"") && rawValue.endsWith("\"") && rawValue.length >= 2) ||
                (rawValue.startsWith("'") && rawValue.endsWith("'") && rawValue.length >= 2)
            ) {
                rawValue = rawValue.substring(1, rawValue.length - 1).trim()
            }

            if (rawKey.equals("CLOUDFLARE_ACCOUNT_ID", ignoreCase = true)) {
                cloudflareAccountId = rawValue
                continue
            }

            val upperKey = rawKey.uppercase()
            val mappedProvider = ENV_KEY_TO_PROVIDER_ID[upperKey]
            if (mappedProvider != null && rawValue.isNotEmpty()) {
                providerKeys[mappedProvider] = rawValue
            } else if (rawValue.isNotEmpty()) {
                unmapped[rawKey] = rawValue
            }
        }

        return ParsedEnv(
            providerKeys = providerKeys,
            cloudflareAccountId = cloudflareAccountId,
            unmappedVariables = unmapped
        )
    }
}
