package com.davizig.blazergateway.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SseChunkResult(
    val deltas: List<String>,
    val isDone: Boolean
)

/**
 * Parser de Server-Sent Events (SSE) para respostas de streaming compatíveis com OpenAI.
 * Acumula buffers de texto parciais, divide por linha em branco (\n\n ou \r\n\r\n),
 * extrai linhas "data:", trata "[DONE]" e decodifica apenas os deltas de texto token a token.
 */
class SseParser {

    private val buffer = StringBuilder()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Processa um chunk recebido do stream e retorna os deltas extraídos e se terminou.
     */
    fun processChunk(chunk: String): SseChunkResult {
        buffer.append(chunk)

        val deltas = mutableListOf<String>()
        var streamDone = false

        while (true) {
            val content = buffer.toString()
            val unixIndex = content.indexOf("\n\n")
            val windowsIndex = content.indexOf("\r\n\r\n")

            val eventEnd: Int
            val delimiterLength: Int

            if (unixIndex != -1 && (windowsIndex == -1 || unixIndex < windowsIndex)) {
                eventEnd = unixIndex
                delimiterLength = 2
            } else if (windowsIndex != -1) {
                eventEnd = windowsIndex
                delimiterLength = 4
            } else {
                break
            }

            val rawEvent = content.substring(0, eventEnd)
            buffer.delete(0, eventEnd + delimiterLength)

            val eventResult = processEvent(rawEvent)
            deltas.addAll(eventResult.deltas)
            if (eventResult.isDone) {
                streamDone = true
            }
        }

        return SseChunkResult(deltas, streamDone)
    }

    /**
     * Processa um bloco de evento SSE individual.
     */
    fun processEvent(eventBlock: String): SseChunkResult {
        val deltas = mutableListOf<String>()
        var isDone = false
        val lines = eventBlock.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("data:")) {
                val dataContent = trimmed.substring(5).trim()
                if (dataContent == "[DONE]" || dataContent == "\"[DONE]\"" || dataContent.startsWith("[DONE]")) {
                    isDone = true
                    continue
                }
                if (dataContent.isEmpty()) {
                    continue
                }

                try {
                    val deltaText = extractDeltaContent(dataContent)
                    if (!deltaText.isNullOrEmpty()) {
                        deltas.add(deltaText)
                    }
                    if (isStopChunk(dataContent)) {
                        isDone = true
                    }
                } catch (_: Exception) {
                    // Ignora chunks com formato inesperado sem quebrar o stream
                }
            }
        }
        return SseChunkResult(deltas, isDone)
    }

    /**
     * Extrai delta de conteúdo ou reasoning de choices[0] do JSON da OpenAI.
     */
    fun extractDeltaContent(jsonString: String): String? {
        val root = try {
            json.parseToJsonElement(jsonString).jsonObject
        } catch (_: Exception) {
            return null
        }
        val choices = root["choices"]?.jsonArray ?: return null
        if (choices.isEmpty()) return null

        val firstChoice = choices[0].jsonObject

        // 1. OpenAI padrão delta.content
        val delta = firstChoice["delta"]?.jsonObject
        if (delta != null) {
            val content = delta["content"]?.jsonPrimitive?.content
            if (!content.isNullOrEmpty()) return content

            // 2. Modelos com reasoning (DeepSeek, NVIDIA NIM, Qwen, etc.)
            val reasoning = delta["reasoning_content"]?.jsonPrimitive?.content
                ?: delta["reasoning"]?.jsonPrimitive?.content
            if (!reasoning.isNullOrEmpty()) return reasoning
        }

        // 3. Formato alternativo choices[0].text
        val text = firstChoice["text"]?.jsonPrimitive?.content
        if (!text.isNullOrEmpty()) return text

        // 4. Formato choices[0].message.content
        val message = firstChoice["message"]?.jsonObject
        if (message != null) {
            val content = message["content"]?.jsonPrimitive?.content
            if (!content.isNullOrEmpty()) return content
        }

        return null
    }

    private fun isStopChunk(jsonString: String): Boolean {
        return try {
            val root = json.parseToJsonElement(jsonString).jsonObject
            val choices = root["choices"]?.jsonArray ?: return false
            if (choices.isEmpty()) return false
            val firstChoice = choices[0].jsonObject
            val finishReason = firstChoice["finish_reason"]?.jsonPrimitive?.content
            finishReason == "stop" || finishReason == "length" || finishReason == "end_turn"
        } catch (_: Exception) {
            false
        }
    }
}
