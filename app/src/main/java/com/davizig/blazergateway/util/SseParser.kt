package com.davizig.blazergateway.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
     * Processa um chunk recebido do stream e emite a lista de deltas de texto extraídos.
     * Retorna se o stream foi finalizado ([DONE]).
     */
    fun processChunk(chunk: String, onDelta: (String) -> Unit): Boolean {
        buffer.append(chunk)

        var streamDone = false

        while (true) {
            val content = buffer.toString()
            // Procura o delimitador de evento SSE: linha em branco (\n\n ou \r\n\r\n)
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
                // Evento ainda incompleto no buffer
                break
            }

            val rawEvent = content.substring(0, eventEnd)
            buffer.delete(0, eventEnd + delimiterLength)

            val isDone = processEvent(rawEvent, onDelta)
            if (isDone) {
                streamDone = true
            }
        }

        return streamDone
    }

    /**
     * Processa um bloco de evento SSE individual.
     * Retorna true se for o sinal de término [DONE].
     */
    fun processEvent(eventBlock: String, onDelta: (String) -> Unit): Boolean {
        var isDone = false
        val lines = eventBlock.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("data:")) {
                val dataContent = trimmed.substring(5).trim()
                if (dataContent == "[DONE]") {
                    isDone = true
                    continue
                }
                if (dataContent.isEmpty()) {
                    continue
                }

                try {
                    val deltaText = extractDeltaContent(dataContent)
                    if (!deltaText.isNullOrEmpty()) {
                        onDelta(deltaText)
                    }
                } catch (_: Exception) {
                    // Ignora chunks com formato inesperado sem quebrar o stream
                }
            }
        }
        return isDone
    }

    /**
     * Extrai choices[0].delta.content da linha de dados JSON da OpenAI.
     */
    fun extractDeltaContent(jsonString: String): String? {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val choices = root["choices"]?.jsonArray ?: return null
        if (choices.isEmpty()) return null

        val firstChoice = choices[0].jsonObject
        val delta = firstChoice["delta"]?.jsonObject ?: return null

        return delta["content"]?.jsonPrimitive?.content
    }
}
