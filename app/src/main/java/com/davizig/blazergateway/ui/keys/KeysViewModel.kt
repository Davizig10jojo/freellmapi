package com.davizig.blazergateway.ui.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.davizig.blazergateway.data.local.SecurePreferences
import com.davizig.blazergateway.data.local.dao.UsageTrackerDao
import com.davizig.blazergateway.data.repository.ProviderRepository
import com.davizig.blazergateway.domain.model.HealthStatus
import com.davizig.blazergateway.domain.model.ModelInfo
import com.davizig.blazergateway.domain.model.Provider
import com.davizig.blazergateway.domain.model.ProviderHealth
import com.davizig.blazergateway.util.BackupCrypto
import com.davizig.blazergateway.util.EnvParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeysViewModel(
    private val providerRepository: ProviderRepository,
    private val securePreferences: SecurePreferences,
    private val usageTrackerDao: UsageTrackerDao
) : ViewModel() {

    private val _providers = MutableStateFlow<List<Provider>>(emptyMap<String, Provider>().values.toList())
    val providers = _providers.asStateFlow()

    val healthMap: StateFlow<Map<String, ProviderHealth>> = providerRepository.healthMap

    private val _modelsByProvider = MutableStateFlow<Map<String, List<ModelInfo>>>(emptyMap())
    val modelsByProvider = _modelsByProvider.asStateFlow()

    private val _lastUpdatedByProvider = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastUpdatedByProvider = _lastUpdatedByProvider.asStateFlow()

    private val _dailyUsageByProvider = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dailyUsageByProvider = _dailyUsageByProvider.asStateFlow()

    private val _isRefreshingAll = MutableStateFlow(false)
    val isRefreshingAll = _isRefreshingAll.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    init {
        loadData()
    }

    fun loadData() {
        _providers.value = providerRepository.getAllProviders()
        viewModelScope.launch {
            val modelsMap = mutableMapOf<String, List<ModelInfo>>()
            val updateMap = mutableMapOf<String, Long>()
            val usageMap = mutableMapOf<String, Int>()
            val today = dateFormat.format(Date())

            for (p in _providers.value) {
                val cached = providerRepository.getCachedModels(p.id)
                modelsMap[p.id] = cached
                val lastUpdated = providerRepository.getLastUpdatedTimestamp(p.id)
                if (lastUpdated != null) {
                    updateMap[p.id] = lastUpdated
                }

                val trackers = usageTrackerDao.getTrackersForProvider(p.id)
                val sumDaily = trackers.filter { it.dailyDateString == today }.sumOf { it.dailyRequestsCount }
                usageMap[p.id] = sumDaily
            }

            _modelsByProvider.value = modelsMap
            _lastUpdatedByProvider.value = updateMap
            _dailyUsageByProvider.value = usageMap
        }
    }

    fun getApiKey(providerId: String): String {
        return securePreferences.getApiKey(providerId) ?: ""
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        securePreferences.setApiKey(providerId, apiKey)
        loadData()
        // Auto-busca modelos para o provedor quando a chave é adicionada
        if (apiKey.isNotBlank()) {
            refreshModels(providerId, force = true)
        }
    }

    fun toggleProviderEnabled(providerId: String, enabled: Boolean) {
        securePreferences.setProviderEnabled(providerId, enabled)
        loadData()
    }

    fun selectModelForProvider(providerId: String, modelId: String) {
        securePreferences.setSelectedModel(providerId, modelId)
        loadData()
    }

    fun testProvider(providerId: String) {
        viewModelScope.launch {
            providerRepository.testProvider(providerId)
        }
    }

    fun testAllProviders() {
        viewModelScope.launch {
            _statusMessage.value = "Testando todos os provedores…"
            providerRepository.testAllProviders()
            _statusMessage.value = "Testes concluídos!"
        }
    }

    fun refreshModels(providerId: String, force: Boolean = true) {
        viewModelScope.launch {
            val models = providerRepository.refreshModelsForProvider(providerId, force = force)
            val updated = _modelsByProvider.value.toMutableMap()
            updated[providerId] = models
            _modelsByProvider.value = updated

            val lastUpdate = providerRepository.getLastUpdatedTimestamp(providerId)
            if (lastUpdate != null) {
                val updatedTimestamps = _lastUpdatedByProvider.value.toMutableMap()
                updatedTimestamps[providerId] = lastUpdate
                _lastUpdatedByProvider.value = updatedTimestamps
            }
        }
    }

    fun refreshAllModels() {
        viewModelScope.launch {
            _isRefreshingAll.value = true
            _statusMessage.value = "Atualizando modelos de todos os provedores…"
            try {
                providerRepository.refreshAllActiveModels(force = true)
                loadData()
                _statusMessage.value = "Modelos atualizados com sucesso!"
            } finally {
                _isRefreshingAll.value = false
            }
        }
    }

    fun clearAllCooldowns() {
        viewModelScope.launch {
            usageTrackerDao.clearAllCooldowns()
            loadData()
            _statusMessage.value = "Limites de espera e contadores redefinidos com sucesso!"
        }
    }

    fun addCustomProvider(name: String, baseUrl: String, apiKey: String, chatEndpoint: String) {
        val id = "custom_" + System.currentTimeMillis()
        val custom = Provider(
            id = id,
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            chatEndpoint = if (chatEndpoint.isBlank()) "/chat/completions" else chatEndpoint.trim(),
            requiresKey = apiKey.isNotBlank(),
            isCustom = true,
            enabled = true
        )
        providerRepository.addCustomProvider(custom)
        if (apiKey.isNotBlank()) {
            securePreferences.setApiKey(id, apiKey)
        }
        loadData()
        refreshModels(id, force = true)
    }

    fun removeCustomProvider(providerId: String) {
        providerRepository.removeCustomProvider(providerId)
        loadData()
    }

    // Prévia de importação .env
    fun parseEnvPreview(content: String): EnvParser.ParsedEnv {
        return EnvParser.parse(content)
    }

    fun applyImportedEnv(parsed: EnvParser.ParsedEnv) {
        for ((providerId, key) in parsed.providerKeys) {
            securePreferences.setApiKey(providerId, key)
        }
        if (!parsed.cloudflareAccountId.isNullOrBlank()) {
            securePreferences.setCloudflareAccountId(parsed.cloudflareAccountId)
        }
        loadData()
        refreshAllModels()
        _statusMessage.value = "${parsed.providerKeys.size} chaves importadas com sucesso!"
    }

    // Exportação Criptografada AES-256-GCM
    fun exportEncrypted(password: String): String {
        val keysMap = securePreferences.getAllApiKeys()
        val payloadObj = buildJsonObject {
            for ((k, v) in keysMap) {
                put(k, v)
            }
            val cf = securePreferences.getCloudflareAccountId()
            if (cf.isNotBlank()) {
                put("CLOUDFLARE_ACCOUNT_ID", cf)
            }
        }
        val rawJson = payloadObj.toString()
        return BackupCrypto.encrypt(rawJson, password.toCharArray())
    }

    // Importação a partir de backup criptografado
    fun importEncrypted(encryptedJson: String, password: String): Boolean {
        return try {
            val decryptedJson = BackupCrypto.decrypt(encryptedJson, password.toCharArray())
            val parsed = Json.decodeFromString<Map<String, String>>(decryptedJson)
            for ((key, value) in parsed) {
                if (key == "CLOUDFLARE_ACCOUNT_ID") {
                    securePreferences.setCloudflareAccountId(value)
                } else {
                    securePreferences.setApiKey(key, value)
                }
            }
            loadData()
            refreshAllModels()
            _statusMessage.value = "Backup descriptografado e aplicado com sucesso!"
            true
        } catch (e: Exception) {
            _statusMessage.value = "Erro na senha ou arquivo de backup inválido: ${e.message}"
            false
        }
    }

    // Exportação em JSON puro
    fun exportPlainJson(): String {
        val keysMap = securePreferences.getAllApiKeys()
        val payloadObj = buildJsonObject {
            for ((k, v) in keysMap) {
                put(k, v)
            }
            val cf = securePreferences.getCloudflareAccountId()
            if (cf.isNotBlank()) {
                put("CLOUDFLARE_ACCOUNT_ID", cf)
            }
        }
        return payloadObj.toString()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val providerRepository: ProviderRepository,
        private val securePreferences: SecurePreferences,
        private val usageTrackerDao: UsageTrackerDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KeysViewModel(providerRepository, securePreferences, usageTrackerDao) as T
        }
    }
}
