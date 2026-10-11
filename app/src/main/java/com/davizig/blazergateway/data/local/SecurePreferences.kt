package com.davizig.blazergateway.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.davizig.blazergateway.domain.model.Provider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Gerenciador de armazenamento seguro utilizando EncryptedSharedPreferences
 * com chave mestre AES256_GCM gerenciada diretamente pelo Android Keystore.
 */
class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "blazergateway_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json { ignoreUnknownKeys = true }

    // Chaves de API por provedor
    fun getApiKey(providerId: String): String? {
        return prefs.getString("key_$providerId", null)?.takeIf { it.isNotBlank() }
    }

    fun setApiKey(providerId: String, apiKey: String?) {
        prefs.edit().apply {
            if (apiKey.isNullOrBlank()) {
                remove("key_$providerId")
            } else {
                putString("key_$providerId", apiKey.trim())
            }
            apply()
        }
    }

    fun getAllApiKeys(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (provider in Provider.DEFAULT_PROVIDERS) {
            val key = getApiKey(provider.id)
            if (!key.isNullOrBlank()) {
                result[provider.id] = key
            }
        }
        for (custom in getCustomProviders()) {
            val key = getApiKey(custom.id)
            if (!key.isNullOrBlank()) {
                result[custom.id] = key
            }
        }
        return result
    }

    // Status de ativação do provedor
    fun isProviderEnabled(providerId: String): Boolean {
        return prefs.getBoolean("enabled_$providerId", true)
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_$providerId", enabled).apply()
    }

    // Modelo selecionado por provedor
    fun getSelectedModel(providerId: String): String? {
        return prefs.getString("selected_model_$providerId", null)
    }

    fun setSelectedModel(providerId: String, modelId: String?) {
        prefs.edit().apply {
            if (modelId.isNullOrBlank()) {
                remove("selected_model_$providerId")
            } else {
                putString("selected_model_$providerId", modelId.trim())
            }
            apply()
        }
    }

    // Cloudflare Account ID
    fun getCloudflareAccountId(): String {
        return prefs.getString("cloudflare_account_id", "") ?: ""
    }

    fun setCloudflareAccountId(accountId: String) {
        prefs.edit().putString("cloudflare_account_id", accountId.trim()).apply()
    }

    // Limiar de troca proativa (porcentagem: 50 a 95, default 85)
    fun getProactiveThreshold(): Int {
        return prefs.getInt("proactive_threshold", 85)
    }

    fun setProactiveThreshold(threshold: Int) {
        prefs.edit().putInt("proactive_threshold", threshold.coerceIn(50, 95)).apply()
    }

    // Bloqueio biométrico
    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean("biometric_enabled", false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    // Intervalo de cache de modelos (horas, default 24)
    fun getModelRefreshIntervalHours(): Int {
        return prefs.getInt("model_refresh_hours", 24)
    }

    fun setModelRefreshIntervalHours(hours: Int) {
        prefs.edit().putInt("model_refresh_hours", hours.coerceIn(1, 168)).apply()
    }

    // Tema (SYSTEM, DARK, LIGHT)
    fun getThemeChoice(): String {
        return prefs.getString("theme_choice", "SYSTEM") ?: "SYSTEM"
    }

    fun setThemeChoice(theme: String) {
        prefs.edit().putString("theme_choice", theme).apply()
    }

    // Provedores customizados
    fun getCustomProviders(): List<Provider> {
        val raw = prefs.getString("custom_providers_json", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Provider>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomProviders(providers: List<Provider>) {
        val serialized = json.encodeToString(providers)
        prefs.edit().putString("custom_providers_json", serialized).apply()
    }
}
