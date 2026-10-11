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
 * Registra o consumo por minuto e por dia, calcula índices de sucesso e saturação,
 * e elege o melhor provedor disponível.
 */
class SmartRouter(
    private val usageTrackerDao: UsageTrackerDao
) {

    // Limites de referência para níveis gratuitos (Free Tier)
    private val DEFAULT_RPM_LIMIT = 20
    private val DEFAULT_DAILY_LIMIT = 800

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun getTodayString(): String = dateFormat.format(Date())

    /**
     * Candidato elegível para roteamento
     */
    data class RouteCandidate(
        val provider: Provider,
        val modelId: String,
        val apiKey: String?,
        val score: Double,
        val cooldownUntil: Long = 0L
    )

    /**
     * Avalia e ordena candidatos viáveis com base em:
     * Score = 0.6 * taxa_sucesso + 0.4 * cota_restante + random(0.05)
     */
    suspend fun selectCandidate(
        availableCandidates: List<Pair<Provider, String>>,
        apiKeys: Map<String, String>,
        proactiveThresholdPercent: Int
    ): RouteCandidate? {
        val now = System.currentTimeMillis()
        val today = getTodayString()

        val evaluated = mutableListOf<RouteCandidate>()

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

            // Verifica se está em cooldown
            if (tracker.cooldownUntil > now) {
                continue
            }

            // Calcula janela de 1 minuto
            val minuteRequests = if (now - tracker.minuteWindowStart < 60_000) {
                tracker.requestsLastMinute
            } else {
                0
            }

            // Calcula contagem do dia
            val dailyRequests = if (tracker.dailyDateString == today) {
                tracker.dailyRequestsCount
            } else {
                0
            }

            // Troca proativa: se atingir ou ultrapassar o limiar (ex: 85%), pula este provedor
            val rpmSaturation = minuteRequests.toDouble() / DEFAULT_RPM_LIMIT.toDouble()
            val dailySaturation = dailyRequests.toDouble() / DEFAULT_DAILY_LIMIT.toDouble()
            val highestSaturation = maxOf(rpmSaturation, dailySaturation) * 100.0

            if (highestSaturation >= proactiveThresholdPercent) {
                // Muito próximo do limite de cota, prioriza alternativas
                continue
            }

            // Taxa de sucesso baseada em falhas consecutivas
            val successRate = when (tracker.consecutiveFailures) {
                0 -> 1.0
                1 -> 0.7
                2 -> 0.4
                else -> 0.1
            }

            // Cota restante (0.0 a 1.0)
            val remainingQuota = (1.0 - (highestSaturation / 100.0)).coerceIn(0.0, 1.0)

            // Fórmula: 0.6 * taxa_sucesso + 0.4 * cota_restante + random(0.05)
            val jitter = Random.nextDouble(0.0, 0.05)
            val score = (0.6 * successRate) + (0.4 * remainingQuota) + jitter

            val apiKey = if (provider.requiresKey) apiKeys[provider.id] else null

            evaluated.add(
                RouteCandidate(
                    provider = provider,
                    modelId = modelId,
                    apiKey = apiKey,
                    score = score,
                    cooldownUntil = tracker.cooldownUntil
                )
            )
        }

        // Ordena pelo maior score
        return evaluated.maxByOrNull { it.score }
    }

    /**
     * Registra o sucesso de uma requisição e reseta falhas consecutivas.
     */
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

    /**
     * Aplica regras reativas de Cooldown:
     * HTTP 429 -> cooldown = Retry-After * 2 (máx 15 min); padrão 4 min
     * HTTP 401/403 -> 1 hora
     * HTTP 5xx -> 2 minutos
     */
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

        val cooldownMs: Long = when {
            statusCode == 429 -> {
                val seconds = retryAfterSeconds?.let { it * 2 } ?: 240L // Padrão 4 min se ausente
                val cappedSeconds = minOf(seconds, 900L) // Máximo 15 min
                cappedSeconds * 1000L
            }
            statusCode == 401 || statusCode == 403 -> {
                3600_000L // 1 hora
            }
            statusCode in 500..599 -> {
                120_000L // 2 minutos
            }
            else -> {
                30_000L // 30 segundos para outros erros de rede
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

    /**
     * Retorna o menor tempo restante de cooldown (em segundos) se todos os candidatos estiverem travados.
     */
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
}
