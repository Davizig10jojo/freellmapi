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
    }

    @Test
    fun `test proactive switching skips provider at 85 percent threshold`() = runBlocking {
        val dao = FakeUsageTrackerDao()
        val router = SmartRouter(dao)

        val prov1 = Provider(id = "p1", name = "P1", baseUrl = "https://p1.ai", requiresKey = false)
        val prov2 = Provider(id = "p2", name = "P2", baseUrl = "https://p2.ai", requiresKey = false)

        val now = System.currentTimeMillis()
        // P1 tem 18 requisições na janela de 1 min (90% do limite de 20 RPM -> excede 85%)
        dao.insertOrUpdate(
            UsageTrackerEntity(
                providerId = "p1",
                modelId = "m1",
                requestsLastMinute = 18,
                minuteWindowStart = now,
                dailyRequestsCount = 50,
                dailyDateString = "2026-10-11",
                consecutiveFailures = 0,
                cooldownUntil = 0L
            )
        )

        // P2 está limpo
        dao.insertOrUpdate(
            UsageTrackerEntity(
                providerId = "p2",
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
        assertEquals("p2", chosen?.provider?.id)
    }

    @Test
    fun `test reactive cooldown calculations`() = runBlocking {
        val dao = FakeUsageTrackerDao()
        val router = SmartRouter(dao)

        // 429 com Retry-After 30s -> cooldown = 30 * 2 = 60s
        val cd429 = router.recordFailure("groq", "llama", statusCode = 429, retryAfterSeconds = 30L)
        assertEquals(60L, cd429)

        // 429 com Retry-After enorme 600s -> cooldown capado em 900s (15 min)
        val cd429Max = router.recordFailure("groq", "llama", statusCode = 429, retryAfterSeconds = 600L)
        assertEquals(900L, cd429Max)

        // 401 -> 1 hora (3600s)
        val cd401 = router.recordFailure("openrouter", "gpt-4", statusCode = 401)
        assertEquals(3600L, cd401)

        // 500 -> 2 minutos (120s)
        val cd500 = router.recordFailure("cerebras", "llama", statusCode = 500)
        assertEquals(120L, cd500)
    }
}
