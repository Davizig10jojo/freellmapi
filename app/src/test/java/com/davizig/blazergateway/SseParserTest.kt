package com.davizig.blazergateway

import com.davizig.blazergateway.util.SseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    @Test
    fun `test sse parser accumulates chunks and emits tokens`() {
        val parser = SseParser()
        val emittedTokens = mutableListOf<String>()

        // Simula chunks fragmentados divididos no meio de linhas SSE
        val chunk1 = "data: {\"choices\":[{\"delta\":{\"content\":\"Olá\"}}]}\n\n"
        val chunk2 = "data: {\"choices\":[{\"delta\":{\"content\":\" mundo\"}}]}"
        val chunk3 = "\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"!\"}}]}\n\ndata: [DONE]\n\n"

        val done1 = parser.processChunk(chunk1) { emittedTokens.add(it) }
        assertEquals(false, done1)

        val done2 = parser.processChunk(chunk2) { emittedTokens.add(it) }
        assertEquals(false, done2)

        val done3 = parser.processChunk(chunk3) { emittedTokens.add(it) }
        assertEquals(true, done3)

        assertEquals("Olá", emittedTokens[0])
        assertEquals(" mundo", emittedTokens[1])
        assertEquals("!", emittedTokens[2])
        assertEquals(3, emittedTokens.size)
    }

    @Test
    fun `test sse parser handles empty data or comments gracefully`() {
        val parser = SseParser()
        val tokens = mutableListOf<String>()

        val raw = """: ping
data: 

data: {"choices":[{"delta":{"content":"Token"}}]}

data: [DONE]

"""
        val isDone = parser.processChunk(raw) { tokens.add(it) }

        assertTrue(isDone)
        assertEquals(1, tokens.size)
        assertEquals("Token", tokens[0])
    }
}
