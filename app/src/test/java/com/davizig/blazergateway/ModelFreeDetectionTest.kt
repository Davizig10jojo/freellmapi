package com.davizig.blazergateway

import com.davizig.blazergateway.data.remote.OpenAIApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFreeDetectionTest {

    @Test
    fun `test parse models detects free models and vision tags correctly`() {
        val client = OpenAIApiClient()

        val jsonResponse = """
            {
              "data": [
                {
                  "id": "qwen/qwen-2.5-coder-32b:free",
                  "name": "Qwen 2.5 Coder Free",
                  "pricing": {
                    "prompt": "0",
                    "completion": "0"
                  },
                  "context_length": 32768
                },
                {
                  "id": "meta-llama/llama-3.2-11b-vision-instruct:free",
                  "name": "Llama 3.2 11B Vision Instruct",
                  "description": "Multimodal model for images and text",
                  "free": true,
                  "context_length": 131072
                },
                {
                  "id": "openai/gpt-4o",
                  "name": "GPT-4o Paid",
                  "pricing": {
                    "prompt": "0.005",
                    "completion": "0.015"
                  },
                  "context_length": 128000
                },
                {
                  "id": "google/gemini-flash-1.5",
                  "name": "Gemini Flash 1.5",
                  "context_length": 1000000
                }
              ]
            }
        """.trimIndent()

        val models = client.parseModelsResponse("openrouter", jsonResponse)
        assertEquals(4, models.size)

        // Modelo 1: prompt e completion "0" -> isFree = true, hasVision = false
        val m1 = models.first { it.id == "qwen/qwen-2.5-coder-32b:free" }
        assertTrue("M1 deve ser gratuito", m1.isFree)
        assertFalse("M1 não tem visão", m1.hasVision)
        assertEquals(32768, m1.contextLength)

        // Modelo 2: free: true e nome com "vision" -> isFree = true, hasVision = true
        val m2 = models.first { it.id == "meta-llama/llama-3.2-11b-vision-instruct:free" }
        assertTrue("M2 deve ser gratuito", m2.isFree)
        assertTrue("M2 deve ter visão", m2.hasVision)
        assertEquals(131072, m2.contextLength)

        // Modelo 3: pago (preços > 0) -> isFree = false
        val m3 = models.first { it.id == "openai/gpt-4o" }
        assertFalse("M3 deve ser pago", m3.isFree)

        // Modelo 4: sem campo pricing -> assume gratuito
        val m4 = models.first { it.id == "google/gemini-flash-1.5" }
        assertTrue("M4 sem pricing deve ser assumido gratuito", m4.isFree)
    }
}
