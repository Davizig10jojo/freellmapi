package com.davizig.blazergateway.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.davizig.blazergateway.data.local.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _themeChoice = MutableStateFlow(securePreferences.getThemeChoice())
    val themeChoice = _themeChoice.asStateFlow()

    private val _proactiveThreshold = MutableStateFlow(securePreferences.getProactiveThreshold())
    val proactiveThreshold = _proactiveThreshold.asStateFlow()

    private val _cloudflareAccountId = MutableStateFlow(securePreferences.getCloudflareAccountId())
    val cloudflareAccountId = _cloudflareAccountId.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(securePreferences.isBiometricEnabled())
    val biometricEnabled = _biometricEnabled.asStateFlow()

    private val _modelRefreshHours = MutableStateFlow(securePreferences.getModelRefreshIntervalHours())
    val modelRefreshHours = _modelRefreshHours.asStateFlow()

    fun setThemeChoice(theme: String) {
        securePreferences.setThemeChoice(theme)
        _themeChoice.value = theme
    }

    fun setProactiveThreshold(threshold: Int) {
        securePreferences.setProactiveThreshold(threshold)
        _proactiveThreshold.value = threshold
    }

    fun setCloudflareAccountId(id: String) {
        securePreferences.setCloudflareAccountId(id)
        _cloudflareAccountId.value = id
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securePreferences.setBiometricEnabled(enabled)
        _biometricEnabled.value = enabled
    }

    fun setModelRefreshHours(hours: Int) {
        securePreferences.setModelRefreshIntervalHours(hours)
        _modelRefreshHours.value = hours
    }

    class Factory(
        private val securePreferences: SecurePreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(securePreferences) as T
        }
    }
}
