package com.davizig.blazergateway.data.repository

import com.davizig.blazergateway.data.local.dao.UsageTrackerDao
import com.davizig.blazergateway.data.local.entity.UsageTrackerEntity
import com.davizig.blazergateway.domain.model.Provider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Roteador Inteligente com failover reativo e troca proativa de provedores.
 * Suporta limites específicos por provedor (como NVIDIA NIM com 40 RPM e sem créditos pagos,
 * Pollinations ilimitado, Groq e Cerebras de alta velocidade).
 */
class SmartRouter(
    private val usageTrackerDao: UsageTrackerDao
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun getTodayString(): String = dateFormat.format(Date())

    /**
     * Limites de taxa específicos por provedor em requisições por minuto (RPM)
     */
    fun getProviderRpmLimit(providerId: String): Int {
        return when (providerId.lowercase()) {
            "nvidia" -> 40 // NVIDIA NIM: 40 requisições por minuto sem créditos pagos
            "groq" -> 30
            "cerebras" -> 30
            "pollinations" -> 120 // Gratuito e anônimo de alta capacidade
            "kilo" -> 60
            "ovh" -> 60
            "aihorde" -> 30
            "google_gemini" -> 15
            "openrouter" -> 20
            else -> 60
        }
    }

    fun isRpmBasedProvider(providerId: String): Boolean {
        return when (providerId.lowercase()) {
            "nvidia", "groq", "cerebras", "google_gemini", "pollinations", "kilo", "ovh", "aihorde" -> true
            else -> false
        }
    }

    fun getProviderDailyLimit(providerId: String): Int {
        return when (providerId.lowercase()) {
            "nvidia" -> 100000 // NVIDIA NIM não é baseado em créditos pagos
            "groq" -> 14400
            "cerebras" -> 14400
            "pollinations" -> 50000
            "kilo" -> 20000
            "ovh" -> 20000
            "aihorde" -> 5000
            "google_gemini" -> 1500
            "openrouter" -> 1000
            else -> 10000
        }
    }

    fun getProviderQuotaDescription(providerId: String): String {
        return when (providerId.lowercase()) {
            "nvidia" -> "Taxa: 40 req/min (Sem créditos / Grátis)"
            "groq" -> "Taxa: 30 req/min (Ultra veloz / Grátis)"
            "cerebras" -> "Taxa: 30 req/min (Ultra veloz / Grátis)"
            "pollinations" -> "Ilimitado & Anônimo (Sem chave)"
            "kilo" -> "Modelos gratuitos (Sem chave)"
            "ovh" -> "Modelos gratuitos Kepler (Sem chave)"
            "aihorde" -> "Cluster Comunitário (Sem chave / Grátis)"
            "google_gemini" -> "Taxa: 15 req/min (Plano gratuito Google)"
            "openrouter" -> "Taxa: 20 req/min (Modelos :free)"
            else -> "Compatível com OpenAI"
        }
    }

    data class RouteCandidate(
        val provider: Provider,
        val modelId: String,
        val apiKey: String?,
        val score: Double,
        val cooldownUntil: Long = 0L,
        val saturationPercent: Double = 0.0
    )

    /**
     * Avalia e elege o melhor provedor disponível.
     * NUNCA rejeita todos os provedores se houver candidatos configurados!
     */
    suspend fun selectCandidate(
        availableCandidates: List<Pair<Provider, String>>,
        apiKeys: Map<String, String>,
        proactiveThresholdPercent: Int
    ): RouteCandidate? {
        if (availableCandidates.isEmpty()) return null

        val now = System.currentTimeMillis()
        val today = getTodayString()

        val allEvaluated = mutableListOf<RouteCandidate>()
        val readyEvaluated = mutableListOf<RouteCandidate>()

        for ((provider, modelId) in availableCandidates) {
            val tracker = usageTrackerDao.getTracker(provider.id, modelId)
                ?: UsageTrackerEntity(
                    providerId = provider.id,
                    modelId = modelId,
                    requestsLastMinute = 0,
                    minuteWindowStart = now,
                    dailyRequestsCount = 0,
                    dailyDateString = today,
                    consecutiveFailures = 0,
                    cooldownUntil = 0L
                )

            // Calcula taxa na janela de 1 minuto
            val minuteRequests = if (now - tracker.minuteWindowStart < 60_000) {
                tracker.requestsLastMinute
            } else {
                0
            }

            // Calcula contagem diária
            val dailyRequests = if (tracker.dailyDateString == today) {
                tracker.dailyRequestsCount
            } else {
                0
            }

            val rpmLimit = getProviderRpmLimit(provider.id)
            val dailyLimit = getProviderDailyLimit(provider.id)

            val rpmSaturation = (minuteRequests.toDouble() / rpmLimit.toDouble()).coerceAtLeast(0.0)
            val dailySaturation = (dailyRequests.toDouble() / dailyLimit.toDouble()).coerceAtLeast(0.0)
            val highestSaturation = maxOf(rpmSaturation, dailySaturation) * 100.0

            val successRate = when (tracker.consecutiveFailures) {
                0 -> 1.0
                1 -> 0.8
                2 -> 0.6
                else -> 0.4
            }

            val remainingQuota = (1.0 - (highestSaturation / 100.0)).coerceIn(0.0, 1.0)
            val jitter = Random.nextDouble(0.0, 0.05)
            val score = (0.6 * successRate) + (0.4 * remainingQuota) + jitter

            val apiKey = if (provider.requiresKey) apiKeys[provider.id] else null

            val candidate = RouteCandidate(
                provider = provider,
                modelId = modelId,
                apiKey = apiKey,
                score = score,
                cooldownUntil = tracker.cooldownUntil,
                saturationPercent = highestSaturation
            )

            allEvaluated.add(candidate)

            // Se o cooldown já expirou, está pronto para uso imediato
            if (tracker.cooldownUntil <= now) {
                readyEvaluated.add(candidate)
            }
        }

        // Se houver candidatos fora de cooldown:
        if (readyEvaluated.isNotEmpty()) {
            val belowThreshold = readyEvaluated.filter { it.saturationPercent < proactiveThresholdPercent }
            if (belowThreshold.isNotEmpty()) {
                return belowThreshold.maxByOrNull { it.score }
            }
            return readyEvaluated.maxByOrNull { it.score }
        }

        // Se todos estiverem com cooldown registrado:
        // NUNCA bloqueia completamente o usuário! Elege o candidato cujo cooldown expira mais rápido!
        val closestCandidate = allEvaluated.minByOrNull { it.cooldownUntil }
        if (closestCandidate != null) {
            // Se o tempo restante for menor que 10 segundos, permite a tentativa imediata
            if (closestCandidate.cooldownUntil - now <= 10_000L) {
                return closestCandidate
            }
        }

        return closestCandidate
    }

    suspend fun recordSuccess(providerId: String, modelId: String) {
        val now = System.currentTimeMillis()
        val today = getTodayString()
        val tracker = usageTrackerDao.getTracker(providerId, modelId)

        val newMinuteRequests: Int
        val newMinuteStart: Long
        val newDailyRequests: Int

        if (tracker == null) {
            newMinuteRequests = 1
            newMinuteStart = now
            newDailyRequests = 1
        } else {
            if (now - tracker.minuteWindowStart < 60_000) {
                newMinuteRequests = tracker.requestsLastMinute + 1
                newMinuteStart = tracker.minuteWindowStart
            } else {
                newMinuteRequests = 1
                newMinuteStart = now
            }

            newDailyRequests = if (tracker.dailyDateString == today) {
                tracker.dailyRequestsCount + 1
            } else {
                1
            }
        }

        usageTrackerDao.insertOrUpdate(
            UsageTrackerEntity(
                providerId = providerId,
                modelId = modelId,
                requestsLastMinute = newMinuteRequests,
                minuteWindowStart = newMinuteStart,
                dailyRequestsCount = newDailyRequests,
                dailyDateString = today,
                consecutiveFailures = 0,
                cooldownUntil = 0L
            )
        )
    }

    suspend fun recordFailure(
        providerId: String,
        modelId: String,
        statusCode: Int,
        retryAfterSeconds: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        val today = getTodayString()
        val tracker = usageTrackerDao.getTracker(providerId, modelId)

        val consecutive = (tracker?.consecutiveFailures ?: 0) + 1

        val isRpm = isRpmBasedProvider(providerId)

        // Limites de espera (cooldown) realistas:
        // Para NVIDIA NIM (40 RPM) ou provedores por RPM, 429 deve pausar no máximo 5 a 10s!
        val cooldownMs: Long = when {
            statusCode == 429 -> {
                if (isRpm) {
                    val sec = retryAfterSeconds?.coerceIn(2L, 10L) ?: 5L
                    sec * 1000L
                } else {
                    val sec = retryAfterSeconds?.coerceIn(3L, 20L) ?: 10L
                    sec * 1000L
                }
            }
            statusCode == 401 || statusCode == 403 -> {
                60_000L // 1 minuto (em vez de 30 minutos, permitindo corrigir a chave e testar)
            }
            statusCode in 500..599 -> {
                10_000L // 10 segundos
            }
            else -> {
                5_000L // 5 segundos para erro transitório de conexão
            }
        }

        val cooldownUntil = now + cooldownMs

        usageTrackerDao.insertOrUpdate(
            UsageTrackerEntity(
                providerId = providerId,
                modelId = modelId,
                requestsLastMinute = tracker?.requestsLastMinute ?: 0,
                minuteWindowStart = tracker?.minuteWindowStart ?: now,
                dailyRequestsCount = tracker?.dailyRequestsCount ?: 0,
                dailyDateString = tracker?.dailyDateString ?: today,
                consecutiveFailures = consecutive,
                cooldownUntil = cooldownUntil
            )
        )

        return cooldownMs / 1000L
    }

    suspend fun getMinCooldownRemainingSeconds(candidates: List<Pair<Provider, String>>): Long {
        val now = System.currentTimeMillis()
        var minWait = Long.MAX_VALUE

        for ((provider, modelId) in candidates) {
            val tracker = usageTrackerDao.getTracker(provider.id, modelId)
            if (tracker != null && tracker.cooldownUntil > now) {
                val remaining = (tracker.cooldownUntil - now) / 1000L
                if (remaining < minWait) {
                    minWait = remaining
                }
            }
        }

        return if (minWait == Long.MAX_VALUE) 0L else maxOf(minWait, 1L)
    }

    suspend fun clearAllCooldowns() {
        usageTrackerDao.clearAllCooldowns()
    }
}
