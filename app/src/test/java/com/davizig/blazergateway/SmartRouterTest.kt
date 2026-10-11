package com.davizig.blazergateway

import com.davizig.blazergateway.data.local.dao.UsageTrackerDao
import com.davizig.blazergateway.data.local.entity.UsageTrackerEntity
import com.davizig.blazergateway.data.repository.SmartRouter
import com.davizig.blazergateway.domain.model.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRouterTest {

    private class FakeUsageTrackerDao : UsageTrackerDao {
        private val storage = mutableMapOf<String, UsageTrackerEntity>()

        override suspend fun getTracker(providerId: String, modelId: String): UsageTrackerEntity? {
            return storage["$providerId:$modelId"]
        }

        override suspend fun getTrackersForProvider(providerId: String): List<UsageTrackerEntity> {
            return storage.values.filter { it.providerId == providerId }
        }

        override fun getAllTrackersFlow(): Flow<List<UsageTrackerEntity>> {
            return flowOf(storage.values.toList())
        }

        override suspend fun getAllTrackers(): List<UsageTrackerEntity> {
            return storage.values.toList()
        }

        override suspend fun insertOrUpdate(tracker: UsageTrackerEntity) {
            storage["${tracker.providerId}:${tracker.modelId}"] = tracker
        }

        override suspend fun clearAllCooldowns() {
            for ((key, tracker) in storage.toMap()) {
                storage[key] = tracker.copy(cooldownUntil = 0L, consecutiveFailures = 0)
            }
        }

        override suspend fun clearAllTrackers() {
            storage.clear()
        }
    }

    @Test
    fun `test proactive switching skips provider at 85 percent threshold`() = runBlocking {
        val dao = FakeUsageTrackerDao()
        val router = SmartRouter(dao)

        val prov1 = Provider(id = "openrouter", name = "OpenRouter", baseUrl = "https://openrouter.ai", requiresKey = false)
        val prov2 = Provider(id = "groq", name = "Groq", baseUrl = "https://groq.com", requiresKey = false)

        val now = System.currentTimeMillis()
        // OpenRouter tem limite de 20 RPM -> 18 requisições = 90% (ultrapassa limiar de 85%)
        dao.insertOrUpdate(
            UsageTrackerEntity(
                providerId = "openrouter",
                modelId = "m1",
                requestsLastMinute = 18,
                minuteWindowStart = now,
                dailyRequestsCount = 50,
                dailyDateString = "2026-10-11",
                consecutiveFailures = 0,
                cooldownUntil = 0L
            )
        )

        // Groq tem limite de 30 RPM -> 2 requisições = 6% (muito abaixo de 85%)
        dao.insertOrUpdate(
            UsageTrackerEntity(
                providerId = "groq",
                modelId = "m2",
                requestsLastMinute = 2,
                minuteWindowStart = now,
                dailyRequestsCount = 10,
                dailyDateString = "2026-10-11",
                consecutiveFailures = 0,
                cooldownUntil = 0L
            )
        )

        val candidates = listOf(prov1 to "m1", prov2 to "m2")
        val chosen = router.selectCandidate(candidates, emptyMap(), proactiveThresholdPercent = 85)

        assertNotNull(chosen)
        assertEquals("groq", chosen?.provider?.id)
    }

    @Test
    fun `test reactive cooldown calculations`() = runBlocking {
        val dao = FakeUsageTrackerDao()
        val router = SmartRouter(dao)

        // Groq / NVIDIA NIM (provedor RPM): 429 com Retry-After 30s -> cooldown rápido capado em 10s
        val cd429 = router.recordFailure("groq", "llama", statusCode = 429, retryAfterSeconds = 30L)
        assertEquals(10L, cd429)

        // NVIDIA NIM: 429 sem Retry-After -> cooldown padrão de 5s
        val cd429Nvidia = router.recordFailure("nvidia", "llama", statusCode = 429)
        assertEquals(5L, cd429Nvidia)

        // 401 -> 60s (1 minuto) para permitir correção rápida da chave
        val cd401 = router.recordFailure("openrouter", "gpt-4", statusCode = 401)
        assertEquals(60L, cd401)

        // 500 -> 10s
        val cd500 = router.recordFailure("cerebras", "llama", statusCode = 500)
        assertEquals(10L, cd500)
    }
}
