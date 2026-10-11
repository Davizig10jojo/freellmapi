package com.davizig.blazergateway.data.repository

import com.davizig.blazergateway.data.local.SecurePreferences
import com.davizig.blazergateway.data.local.dao.CachedModelDao
import com.davizig.blazergateway.data.local.entity.CachedModelEntity
import com.davizig.blazergateway.data.remote.OpenAIApiClient
import com.davizig.blazergateway.domain.model.HealthStatus
import com.davizig.blazergateway.domain.model.ModelInfo
import com.davizig.blazergateway.domain.model.Provider
import com.davizig.blazergateway.domain.model.ProviderHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProviderRepository(
    private val securePreferences: SecurePreferences,
    private val cachedModelDao: CachedModelDao,
    private val apiClient: OpenAIApiClient
) {

    private val _healthMap = MutableStateFlow<Map<String, ProviderHealth>>(emptyMap())
    val healthMap = _healthMap.asStateFlow()

    fun getAllProviders(): List<Provider> {
        val defaultList = Provider.DEFAULT_PROVIDERS.map { base ->
            base.copy(
                enabled = securePreferences.isProviderEnabled(base.id),
                selectedModelId = securePreferences.getSelectedModel(base.id)
            )
        }
        val customList = securePreferences.getCustomProviders().map { base ->
            base.copy(
                enabled = securePreferences.isProviderEnabled(base.id),
                selectedModelId = securePreferences.getSelectedModel(base.id)
            )
        }
        return defaultList + customList
    }

    fun getProvider(providerId: String): Provider? {
        return getAllProviders().firstOrNull { it.id == providerId }
    }

    fun saveApiKey(providerId: String, apiKey: String?) {
        securePreferences.setApiKey(providerId, apiKey)
    }

    fun getApiKey(providerId: String): String? {
        return securePreferences.getApiKey(providerId)
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        securePreferences.setProviderEnabled(providerId, enabled)
    }

    fun setSelectedModel(providerId: String, modelId: String?) {
        securePreferences.setSelectedModel(providerId, modelId)
    }

    fun addCustomProvider(provider: Provider) {
        val current = securePreferences.getCustomProviders().toMutableList()
        current.removeAll { it.id == provider.id }
        current.add(provider)
        securePreferences.saveCustomProviders(current)
    }

    fun removeCustomProvider(providerId: String) {
        val current = securePreferences.getCustomProviders().toMutableList()
        current.removeAll { it.id == providerId }
        securePreferences.saveCustomProviders(current)
        securePreferences.setApiKey(providerId, null)
    }

    fun getCachedModelsFlow(providerId: String): Flow<List<ModelInfo>> {
        return cachedModelDao.getModelsForProviderFlow(providerId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getCachedModels(providerId: String): List<ModelInfo> {
        return cachedModelDao.getModelsForProvider(providerId).map { it.toDomain() }
    }

    suspend fun getLastUpdatedTimestamp(providerId: String): Long? {
        return cachedModelDao.getLastUpdatedForProvider(providerId)
    }

    /**
     * Sistema de descoberta dinâmica de modelos:
     * Consulta /models do provedor se o cache tiver expirado (> intervalo configurado) ou se for forçado.
     */
    suspend fun refreshModelsForProvider(providerId: String, force: Boolean = false): List<ModelInfo> =
        withContext(Dispatchers.IO) {
            val provider = getProvider(providerId) ?: return@withContext emptyList()
            val apiKey = getApiKey(providerId)

            // Se exige chave e a chave não foi configurada, não consulta
            if (provider.requiresKey && apiKey.isNullOrBlank()) {
                return@withContext getCachedModels(providerId)
            }

            val lastUpdated = cachedModelDao.getLastUpdatedForProvider(providerId) ?: 0L
            val intervalMs = securePreferences.getModelRefreshIntervalHours() * 3600_000L
            val now = System.currentTimeMillis()

            if (!force && (now - lastUpdated) < intervalMs) {
                val cached = getCachedModels(providerId)
                if (cached.isNotEmpty()) {
                    return@withContext cached
                }
            }

            val cloudflareAccountId = securePreferences.getCloudflareAccountId()
            return@withContext try {
                val remoteModels = apiClient.fetchModels(provider, apiKey, cloudflareAccountId)
                if (remoteModels.isNotEmpty()) {
                    val entities = remoteModels.map { it.toEntity() }
                    cachedModelDao.clearForProvider(providerId)
                    cachedModelDao.insertModels(entities)
                    remoteModels
                } else {
                    getCachedModels(providerId)
                }
            } catch (_: Exception) {
                getCachedModels(providerId)
            }
        }

    /**
     * Atualiza modelos para todos os provedores ativos e configurados.
     */
    suspend fun refreshAllActiveModels(force: Boolean = false) {
        for (provider in getAllProviders()) {
            if (provider.enabled && (!provider.requiresKey || !getApiKey(provider.id).isNullOrBlank())) {
                refreshModelsForProvider(provider.id, force)
            }
        }
    }

    /**
     * Testa a saúde de um provedor e atualiza seu status no mapa reativo.
     */
    suspend fun testProvider(providerId: String): ProviderHealth = withContext(Dispatchers.IO) {
        val provider = getProvider(providerId) ?: return@withContext ProviderHealth(
            providerId = providerId,
            status = HealthStatus.ERROR,
            errorMessage = "Provedor desconhecido"
        )

        val apiKey = getApiKey(providerId)
        if (provider.requiresKey && apiKey.isNullOrBlank()) {
            val health = ProviderHealth(
                providerId = providerId,
                status = HealthStatus.ERROR,
                errorMessage = "Chave de API não configurada"
            )
            updateHealth(health)
            return@withContext health
        }

        updateHealth(ProviderHealth(providerId, HealthStatus.TESTING))

        val cloudflareAccountId = securePreferences.getCloudflareAccountId()
        return@withContext try {
            val latency = apiClient.testProviderHealth(provider, apiKey, cloudflareAccountId)
            val health = ProviderHealth(
                providerId = providerId,
                status = HealthStatus.HEALTHY,
                latencyMs = latency
            )
            updateHealth(health)
            health
        } catch (e: Exception) {
            val health = ProviderHealth(
                providerId = providerId,
                status = HealthStatus.ERROR,
                errorMessage = e.message ?: "Falha na conexão"
            )
            updateHealth(health)
            health
        }
    }

    /**
     * Testa todos os provedores habilitados
     */
    suspend fun testAllProviders() {
        for (provider in getAllProviders()) {
            if (provider.enabled) {
                testProvider(provider.id)
            }
        }
    }

    private fun updateHealth(health: ProviderHealth) {
        val updated = _healthMap.value.toMutableMap()
        updated[health.providerId] = health
        _healthMap.value = updated
    }

    private fun CachedModelEntity.toDomain() = ModelInfo(
        id = id,
        providerId = providerId,
        name = name,
        isFree = isFree,
        hasVision = hasVision,
        contextLength = contextLength,
        promptPrice = promptPrice,
        completionPrice = completionPrice,
        lastUpdated = lastUpdated
    )

    private fun ModelInfo.toEntity() = CachedModelEntity(
        id = id,
        providerId = providerId,
        name = name,
        isFree = isFree,
        hasVision = hasVision,
        contextLength = contextLength,
        promptPrice = promptPrice,
        completionPrice = completionPrice,
        lastUpdated = lastUpdated
    )
}
