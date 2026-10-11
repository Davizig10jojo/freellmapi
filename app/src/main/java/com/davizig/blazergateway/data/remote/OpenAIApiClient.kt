package com.davizig.blazergateway.data.remote

import android.util.Log
import com.davizig.blazergateway.data.remote.dto.OpenAIChatRequest
import com.davizig.blazergateway.data.remote.dto.OpenAIMessageDto
import com.davizig.blazergateway.data.remote.dto.OpenAIModelsResponse
import com.davizig.blazergateway.domain.model.ChatMessage
import com.davizig.blazergateway.domain.model.ModelInfo
import com.davizig.blazergateway.domain.model.Provider
import com.davizig.blazergateway.util.SseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(
    val statusCode: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null
) : Exception(message)

class OpenAIApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun sanitizeLog(text: String?): String {
        if (text == null) return ""
        return text.replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+"), "Bearer [MASKED_KEY]")
            .replace(Regex("(?i)(api[_-]?key|key|apikey)=\\S+"), "$1=[MASKED_KEY]")
    }

    /**
     * Busca modelos disponíveis diretamente do provedor.
     * Trata OpenAI-compatible, AI Horde e Pollinations.
     */
    suspend fun fetchModels(
        provider: Provider,
        apiKey: String?,
        cloudflareAccountId: String?
    ): List<ModelInfo> = withContext(Dispatchers.IO) {
        if (provider.isAiHorde) {
            return@withContext fetchAiHordeModels()
        }

        val resolvedBase = provider.getResolvedBaseUrl(cloudflareAccountId).trimEnd('/')
        val url = "$resolvedBase/models"

        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")

        val effectiveKey = apiKey ?: provider.defaultApiKey
        if (!effectiveKey.isNullOrBlank()) {
            val authHeader = provider.customAuthHeader ?: "Authorization"
            val authValue = if (authHeader.equals("Authorization", ignoreCase = true)) "Bearer $effectiveKey" else effectiveKey
            requestBuilder.header(authHeader, authValue)
        }

        if (provider.id == "openrouter") {
            requestBuilder.header("HTTP-Referer", "https://blazergateway.app")
            requestBuilder.header("X-Title", "BlazerGateway")
        }

        val request = requestBuilder.build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext getFallbackModelsForProvider(provider.id)
                }

                val bodyString = response.body?.string() ?: ""
                val parsed = parseModelsResponse(provider.id, bodyString)
                if (parsed.isEmpty()) getFallbackModelsForProvider(provider.id) else parsed
            }
        } catch (_: Exception) {
            getFallbackModelsForProvider(provider.id)
        }
    }

    /**
     * Busca modelos ativos no cluster do AI Horde: GET https://aihorde.net/api/v2/status/models?type=text
     */
    private fun fetchAiHordeModels(): List<ModelInfo> {
        val now = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("https://aihorde.net/api/v2/status/models?type=text")
                .get()
                .header("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val element = json.parseToJsonElement(body).jsonArray
                    val list = mutableListOf<ModelInfo>()
                    for (item in element) {
                        val obj = item.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.content ?: continue
                        val count = obj["count"]?.jsonPrimitive?.intOrNull ?: 0
                        if (count > 0) {
                            list.add(
                                ModelInfo(
                                    id = name,
                                    providerId = "aihorde",
                                    name = "$name (AI Horde)",
                                    isFree = true,
                                    hasVision = false,
                                    contextLength = 4096,
                                    promptPrice = "0",
                                    completionPrice = "0",
                                    lastUpdated = now
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) return list
                }
            }
        } catch (_: Exception) {
            // Segue para os modelos em fallback do AI Horde
        }

        return getAiHordeFallbackModels()
    }

    fun parseModelsResponse(providerId: String, jsonString: String): List<ModelInfo> {
        val result = mutableListOf<ModelInfo>()

        try {
            val responseDto = json.decodeFromString<OpenAIModelsResponse>(jsonString)
            val list = responseDto.data.ifEmpty { responseDto.models ?: emptyList() }

            val now = System.currentTimeMillis()
            for (dto in list) {
                val modelId = dto.id.trim()
                if (modelId.isEmpty()) continue

                val name = dto.name ?: modelId
                val pricing = dto.pricing

                // NVIDIA NIM não tem créditos pagos: 40 RPM gratuitos para todos os modelos!
                // Pollinations, AI Horde, Kilo, OVH também são gratuitos.
                val isFree = when {
                    providerId in listOf("nvidia", "pollinations", "aihorde", "kilo", "ovh", "groq", "cerebras") -> true
                    dto.free == true -> true
                    pricing == null -> true
                    pricing.prompt == "0" && pricing.completion == "0" -> true
                    pricing.prompt == "0.0" && pricing.completion == "0.0" -> true
                    pricing.prompt == "0.0000" && pricing.completion == "0.0000" -> true
                    pricing.prompt.equals("free", ignoreCase = true) -> true
                    modelId.contains(":free", ignoreCase = true) -> true
                    else -> false
                }

                val lowerId = modelId.lowercase()
                val lowerDesc = (dto.description ?: "").lowercase()
                val tagsStr = (dto.tags ?: emptyList()).joinToString(" ").lowercase()
                val modality = (dto.architecture?.modality ?: "").lowercase()

                val hasVision = lowerId.contains("vision") ||
                        lowerId.contains("multimodal") ||
                        lowerId.contains("-vl") ||
                        lowerId.contains("_vl") ||
                        lowerId.contains("image") ||
                        lowerDesc.contains("vision") ||
                        tagsStr.contains("vision") ||
                        modality.contains("image") ||
                        modality.contains("multimodal")

                val context = dto.context_length ?: 4096

                result.add(
                    ModelInfo(
                        id = modelId,
                        providerId = providerId,
                        name = name,
                        isFree = isFree,
                        hasVision = hasVision,
                        contextLength = context,
                        promptPrice = pricing?.prompt,
                        completionPrice = pricing?.completion,
                        lastUpdated = now
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("OpenAIApiClient", "Aviso no parse de modelos: ${e.message}")
        }

        return result
    }

    private fun getFallbackModelsForProvider(providerId: String): List<ModelInfo> {
        val now = System.currentTimeMillis()
        return when (providerId) {
            "nvidia" -> listOf(
                ModelInfo("meta/llama-3.1-8b-instruct", "nvidia", "Llama 3.1 8B Instruct (NVIDIA NIM - 40 RPM)", true, false, 128000, "0", "0", now),
                ModelInfo("meta/llama-3.1-70b-instruct", "nvidia", "Llama 3.1 70B Instruct (NVIDIA NIM - 40 RPM)", true, false, 128000, "0", "0", now),
                ModelInfo("meta/llama-3.3-70b-instruct", "nvidia", "Llama 3.3 70B Instruct (NVIDIA NIM - 40 RPM)", true, false, 128000, "0", "0", now),
                ModelInfo("mistralai/mistral-7b-instruct-v0.3", "nvidia", "Mistral 7B Instruct v0.3 (NVIDIA NIM)", true, false, 32768, "0", "0", now),
                ModelInfo("nvidia/nemotron-4-340b-instruct", "nvidia", "Nemotron 4 340B (NVIDIA NIM)", true, false, 4096, "0", "0", now),
                ModelInfo("meta/llama-3.2-11b-vision-instruct", "nvidia", "Llama 3.2 11B Vision (NVIDIA NIM)", true, true, 128000, "0", "0", now),
                ModelInfo("deepseek-ai/deepseek-r1", "nvidia", "DeepSeek R1 (NVIDIA NIM - 40 RPM)", true, false, 65536, "0", "0", now)
            )
            "groq" -> listOf(
                ModelInfo("llama-3.3-70b-versatile", "groq", "Llama 3.3 70B Versatile (Groq)", true, false, 128000, "0", "0", now),
                ModelInfo("llama-3.1-8b-instant", "groq", "Llama 3.1 8B Instant (Groq)", true, false, 128000, "0", "0", now),
                ModelInfo("mixtral-8x7b-32768", "groq", "Mixtral 8x7B (Groq)", true, false, 32768, "0", "0", now),
                ModelInfo("llama-3.2-11b-vision-preview", "groq", "Llama 3.2 11B Vision (Groq)", true, true, 8192, "0", "0", now)
            )
            "cerebras" -> listOf(
                ModelInfo("llama3.1-8b", "cerebras", "Llama 3.1 8B (Cerebras Ultra-Fast)", true, false, 8192, "0", "0", now),
                ModelInfo("llama3.1-70b", "cerebras", "Llama 3.1 70B (Cerebras Ultra-Fast)", true, false, 8192, "0", "0", now),
                ModelInfo("llama-3.3-70b", "cerebras", "Llama 3.3 70B (Cerebras Ultra-Fast)", true, false, 8192, "0", "0", now)
            )
            "google_gemini" -> listOf(
                ModelInfo("gemini-1.5-flash", "google_gemini", "Gemini 1.5 Flash (Google)", true, true, 1000000, "0", "0", now),
                ModelInfo("gemini-1.5-pro", "google_gemini", "Gemini 1.5 Pro (Google)", true, true, 2000000, "0", "0", now),
                ModelInfo("gemini-2.0-flash-exp", "google_gemini", "Gemini 2.0 Flash Exp (Google)", true, true, 1000000, "0", "0", now)
            )
            "openrouter" -> listOf(
                ModelInfo("meta-llama/llama-3.3-70b-instruct:free", "openrouter", "Llama 3.3 70B (OpenRouter Free)", true, false, 128000, "0", "0", now),
                ModelInfo("google/gemini-2.0-flash-exp:free", "openrouter", "Gemini 2.0 Flash (OpenRouter Free)", true, true, 1000000, "0", "0", now),
                ModelInfo("deepseek/deepseek-r1:free", "openrouter", "DeepSeek R1 (OpenRouter Free)", true, false, 65536, "0", "0", now),
                ModelInfo("qwen/qwen-2.5-coder-32b-instruct:free", "openrouter", "Qwen 2.5 Coder 32B (OpenRouter Free)", true, false, 32768, "0", "0", now)
            )
            "pollinations" -> listOf(
                ModelInfo("openai", "pollinations", "OpenAI GPT-4o Mini (Pollinations)", true, true, 8192, "0", "0", now),
                ModelInfo("mistral", "pollinations", "Mistral Small (Pollinations)", true, false, 8192, "0", "0", now),
                ModelInfo("qwen", "pollinations", "Qwen 2.5 Coder (Pollinations)", true, false, 8192, "0", "0", now),
                ModelInfo("llama", "pollinations", "Llama 3.3 70B (Pollinations)", true, false, 8192, "0", "0", now)
            )
            "kilo" -> listOf(
                ModelInfo("kilo-auto-free", "kilo", "Kilo Auto Free (Kilo)", true, false, 256000, "0", "0", now),
                ModelInfo("poolside-laguna-s-2.1", "kilo", "Poolside Laguna S 2.1 (Kilo)", true, false, 65536, "0", "0", now),
                ModelInfo("nemotron-3-ultra-550b", "kilo", "Nemotron 3 Ultra 550B (Kilo)", true, false, 128000, "0", "0", now),
                ModelInfo("inkling-small", "kilo", "Inkling Small (Kilo)", true, false, 32768, "0", "0", now),
                ModelInfo("nemotron-3-nano-omni-reasoning", "kilo", "Nemotron 3 Nano Omni Reasoning (Kilo)", true, true, 65536, "0", "0", now)
            )
            "ovh" -> listOf(
                ModelInfo("qwen3.8-27b", "ovh", "Qwen3.8 27B (OVH)", true, false, 32768, "0", "0", now),
                ModelInfo("qwen3.5-397b", "ovh", "Qwen3.5 397B (OVH)", true, false, 65536, "0", "0", now),
                ModelInfo("llama-3.3-70b", "ovh", "Llama 3.3 70B (OVH)", true, false, 128000, "0", "0", now),
                ModelInfo("mistral-small-3.2-24b", "ovh", "Mistral Small 3.2 24B (OVH)", true, false, 32768, "0", "0", now),
                ModelInfo("gpt-oss-20b", "ovh", "GPT-OSS 20B (OVH)", true, false, 32768, "0", "0", now)
            )
            "aihorde" -> getAiHordeFallbackModels()
            else -> emptyList()
        }
    }

    private fun getAiHordeFallbackModels(): List<ModelInfo> {
        val now = System.currentTimeMillis()
        return listOf(
            ModelInfo("Gemma 4 31B Heretic", "aihorde", "Gemma 4 31B Heretic (AI Horde)", true, false, 4096, "0", "0", now),
            ModelInfo("Cydonia 24B v4.3", "aihorde", "Cydonia 24B v4.3 (AI Horde)", true, false, 4096, "0", "0", now),
            ModelInfo("Skyfall 31B v4.2", "aihorde", "Skyfall 31B v4.2 (AI Horde)", true, false, 4096, "0", "0", now),
            ModelInfo("Mini Magnum 12B v1.1", "aihorde", "Mini Magnum 12B v1.1 (AI Horde)", true, false, 4096, "0", "0", now),
            ModelInfo("Angelic Eclipse 12B", "aihorde", "Angelic Eclipse 12B (AI Horde)", true, false, 4096, "0", "0", now),
            ModelInfo("Cerebras GPT 111M Instruct", "aihorde", "Cerebras GPT 111M Instruct (AI Horde)", true, false, 2048, "0", "0", now),
            ModelInfo("Pythia 70M Deduped", "aihorde", "Pythia 70M Deduped (AI Horde)", true, false, 2048, "0", "0", now)
        )
    }

    suspend fun testProviderHealth(
        provider: Provider,
        apiKey: String?,
        cloudflareAccountId: String?
    ): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val models = fetchModels(provider, apiKey, cloudflareAccountId)
        if (models.isEmpty()) {
            throw Exception("Nenhum modelo retornado por ${provider.name}")
        }
        System.currentTimeMillis() - startTime
    }

    /**
     * Envia mensagem de chat e consome o stream SSE token a token de forma limpa e síncrona dentro da coroutine.
     */
    fun streamChat(
        provider: Provider,
        modelId: String,
        apiKey: String?,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int,
        cloudflareAccountId: String?
    ): Flow<String> {
        if (provider.isAiHorde) {
            return streamAiHordeChat(provider, modelId, apiKey, messages, systemPrompt, temperature, maxTokens)
        }

        return flow {
            val resolvedBase = provider.getResolvedBaseUrl(cloudflareAccountId).trimEnd('/')
            val endpoint = provider.chatEndpoint.let { if (it.startsWith("/")) it else "/$it" }
            val url = "$resolvedBase$endpoint"

            val openAiMessages = mutableListOf<OpenAIMessageDto>()

            if (systemPrompt.isNotBlank()) {
                openAiMessages.add(
                    OpenAIMessageDto(
                        role = "system",
                        content = JsonPrimitive(systemPrompt)
                    )
                )
            }

            for (msg in messages) {
                val role = msg.role
                val contentElement = if (msg.imageBase64 != null) {
                    buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", msg.content)
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            put("image_url", buildJsonObject {
                                val dataUrl = if (msg.imageBase64.startsWith("data:")) {
                                    msg.imageBase64
                                } else {
                                    "data:image/jpeg;base64,${msg.imageBase64}"
                                }
                                put("url", dataUrl)
                            })
                        })
                    }
                } else {
                    JsonPrimitive(msg.content)
                }

                openAiMessages.add(OpenAIMessageDto(role = role, content = contentElement))
            }

            val chatRequest = OpenAIChatRequest(
                model = modelId,
                messages = openAiMessages,
                stream = true,
                temperature = temperature,
                max_tokens = maxTokens
            )

            val requestBodyJson = json.encodeToString(OpenAIChatRequest.serializer(), chatRequest)
            val requestBody = requestBodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Accept", "text/event-stream")

            val effectiveKey = apiKey ?: provider.defaultApiKey
            if (!effectiveKey.isNullOrBlank()) {
                val authHeader = provider.customAuthHeader ?: "Authorization"
                val authValue = if (authHeader.equals("Authorization", ignoreCase = true)) "Bearer $effectiveKey" else effectiveKey
                requestBuilder.header(authHeader, authValue)
            }

            if (provider.id == "openrouter") {
                requestBuilder.header("HTTP-Referer", "https://blazergateway.app")
                requestBuilder.header("X-Title", "BlazerGateway")
            }

            val request = requestBuilder.build()
            val call = okHttpClient.newCall(request)

            val response = try {
                call.execute()
            } catch (e: IOException) {
                throw Exception(sanitizeLog(e.message ?: "Falha na conexão com ${provider.name}"))
            }

            if (!response.isSuccessful) {
                val code = response.code
                val retryAfter = response.header("Retry-After")?.toLongOrNull()
                val errorBody = try {
                    response.body?.string()?.take(500)
                } catch (_: Exception) {
                    null
                }
                response.close()
                val msg = sanitizeLog(errorBody ?: "HTTP $code em ${provider.name}")
                throw ApiException(code, msg, retryAfter)
            }

            val body = response.body
            if (body == null) {
                response.close()
                throw Exception("Corpo de resposta nulo de ${provider.name}")
            }

            try {
                val source = body.source()
                val sseParser = SseParser()
                val buffer = okio.Buffer()
                var streamFinishedCleanly = false

                while (true) {
                    val bytesRead = try {
                        source.read(buffer, 8192L)
                    } catch (_: java.io.EOFException) {
                        -1L
                    } catch (e: IOException) {
                        if (streamFinishedCleanly) -1L else throw e
                    }

                    if (bytesRead == -1L) {
                        break
                    }

                    val chunk = buffer.readUtf8()
                    if (chunk.isNotEmpty()) {
                        val result = sseParser.processChunk(chunk)
                        for (delta in result.deltas) {
                            emit(delta)
                        }
                        if (result.isDone) {
                            streamFinishedCleanly = true
                            break
                        }
                    }
                }
            } catch (_: java.io.EOFException) {
                // Fim natural do fluxo de eventos
            } finally {
                try { body.close() } catch (_: Exception) {}
                try { response.close() } catch (_: Exception) {}
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Executa chamada assíncrona para AI Horde:
     * 1. POST https://aihorde.net/api/v2/generate/text/async
     * 2. Polling GET https://aihorde.net/api/v2/generate/text/status/{id}
     */
    private fun streamAiHordeChat(
        provider: Provider,
        modelId: String,
        apiKey: String?,
        messages: List<ChatMessage>,
        systemPrompt: String,
        temperature: Float,
        maxTokens: Int
    ): Flow<String> = flow {
        val effectiveKey = apiKey?.takeIf { it.isNotBlank() } ?: provider.defaultApiKey ?: "0000000000"

        val fullPromptBuilder = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            fullPromptBuilder.append("System: ").append(systemPrompt).append("\n\n")
        }
        for (m in messages) {
            val speaker = if (m.role == "user") "User" else "Assistant"
            fullPromptBuilder.append("$speaker: ").append(m.content).append("\n")
        }
        fullPromptBuilder.append("Assistant: ")

        val requestObj = buildJsonObject {
            put("prompt", fullPromptBuilder.toString())
            putJsonObject("params") {
                put("max_context_length", 4096)
                put("max_length", maxTokens.coerceIn(64, 1024))
                put("temperature", temperature)
            }
            put("models", buildJsonArray {
                val cleanModel = modelId.replace(" (AI Horde)", "").trim()
                add(JsonPrimitive(cleanModel))
            })
        }

        val requestBody = requestObj.toString().toRequestBody("application/json".toMediaType())
        val postRequest = Request.Builder()
            .url("https://aihorde.net/api/v2/generate/text/async")
            .post(requestBody)
            .header("apikey", effectiveKey)
            .header("Client-Agent", "BlazerGateway:1.0:blazer@app")
            .build()

        val jobId: String
        okHttpClient.newCall(postRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val code = response.code
                val err = response.body?.string() ?: ""
                throw ApiException(code, "AI Horde erro de geração: HTTP $code - $err")
            }
            val respBody = response.body?.string() ?: "{}"
            val parsed = json.parseToJsonElement(respBody).jsonObject
            jobId = parsed["id"]?.jsonPrimitive?.content ?: throw Exception("ID de tarefa do AI Horde ausente")
        }

        emit("Aguardando worker no cluster do AI Horde…\n\n")

        var isDone = false
        var attempts = 0
        while (!isDone && attempts < 30) {
            delay(1500)
            attempts++

            val statusRequest = Request.Builder()
                .url("https://aihorde.net/api/v2/generate/text/status/$jobId")
                .get()
                .header("Client-Agent", "BlazerGateway:1.0:blazer@app")
                .build()

            okHttpClient.newCall(statusRequest).execute().use { statusResp ->
                if (statusResp.isSuccessful) {
                    val statusBody = statusResp.body?.string() ?: "{}"
                    val parsed = json.parseToJsonElement(statusBody).jsonObject
                    val done = parsed["done"]?.jsonPrimitive?.booleanOrNull == true
                    val faulted = parsed["faulted"]?.jsonPrimitive?.booleanOrNull == true

                    if (faulted) {
                        throw Exception("Geração no AI Horde falhou no worker")
                    }

                    if (done) {
                        val gens = parsed["generations"]?.jsonArray
                        if (gens != null && gens.isNotEmpty()) {
                            val text = gens[0].jsonObject["text"]?.jsonPrimitive?.content ?: ""
                            emit(text.trim())
                        }
                        isDone = true
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
