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

        val res1 = parser.processChunk(chunk1)
        emittedTokens.addAll(res1.deltas)
        assertEquals(false, res1.isDone)

        val res2 = parser.processChunk(chunk2)
        emittedTokens.addAll(res2.deltas)
        assertEquals(false, res2.isDone)

        val res3 = parser.processChunk(chunk3)
        emittedTokens.addAll(res3.deltas)
        assertEquals(true, res3.isDone)

        assertEquals("Olá", emittedTokens[0])
        assertEquals(" mundo", emittedTokens[1])
        assertEquals("!", emittedTokens[2])
        assertEquals(3, emittedTokens.size)
    }

    @Test
    fun `test sse parser handles empty data or comments gracefully`() {
        val parser = SseParser()

        val raw = """: ping
data: 

data: {"choices":[{"delta":{"content":"Token"}}]}

data: [DONE]

"""
        val result = parser.processChunk(raw)

        assertTrue(result.isDone)
        assertEquals(1, result.deltas.size)
        assertEquals("Token", result.deltas[0])
    }
}
