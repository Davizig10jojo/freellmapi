package com.davizig.blazergateway.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.davizig.blazergateway.BlazerGatewayApp
import com.davizig.blazergateway.ui.chat.ChatScreen
import com.davizig.blazergateway.ui.chat.ChatViewModel
import com.davizig.blazergateway.ui.keys.KeysAndModelsScreen
import com.davizig.blazergateway.ui.keys.KeysViewModel
import com.davizig.blazergateway.ui.models.ModelsCatalogScreen
import com.davizig.blazergateway.ui.navigation.NavDestination
import com.davizig.blazergateway.ui.server.ServerScreen
import com.davizig.blazergateway.ui.server.ServerViewModel
import com.davizig.blazergateway.ui.settings.SettingsScreen
import com.davizig.blazergateway.ui.settings.SettingsViewModel
import com.davizig.blazergateway.ui.theme.BlazerGatewayTheme

class MainActivity : FragmentActivity() {

    private val chatViewModel: ChatViewModel by viewModels {
        val app = application as BlazerGatewayApp
        ChatViewModel.Factory(app.chatRepository, app.providerRepository)
    }

    private val keysViewModel: KeysViewModel by viewModels {
        val app = application as BlazerGatewayApp
        KeysViewModel.Factory(
            app.providerRepository,
            app.securePreferences,
            app.database.usageTrackerDao()
        )
    }

    private val serverViewModel: ServerViewModel by viewModels {
        val app = application as BlazerGatewayApp
        ServerViewModel.Factory(app.localProxyServer)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as BlazerGatewayApp
        SettingsViewModel.Factory(app.securePreferences)
    }

    private var isBiometricAuthenticatedThisSession by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeChoice by settingsViewModel.themeChoice.collectAsState()
            val isDarkTheme = when (themeChoice) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            BlazerGatewayTheme(darkTheme = isDarkTheme) {
                MainContent()
            }
        }
    }

    @Composable
    private fun MainContent() {
        var currentTab by remember { mutableStateOf(NavDestination.CHAT) }
        val biometricEnabled by settingsViewModel.biometricEnabled.collectAsState()
        val providers by keysViewModel.providers.collectAsState()

        Scaffold(
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    NavDestination.entries.forEach { destination ->
                        val selected = currentTab == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (destination == NavDestination.KEYS && biometricEnabled && !isBiometricAuthenticatedThisSession) {
                                    triggerBiometricPrompt {
                                        isBiometricAuthenticatedThisSession = true
                                        currentTab = NavDestination.KEYS
                                    }
                                } else {
                                    currentTab = destination
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title
                                )
                            },
                            label = { Text(destination.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_${destination.route}")
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    NavDestination.CHAT -> {
                        ChatScreen(
                            viewModel = chatViewModel,
                            providers = providers,
                            onNavigateToModels = { currentTab = NavDestination.MODELS },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    NavDestination.MODELS -> {
                        ModelsCatalogScreen(
                            keysViewModel = keysViewModel,
                            onSelectModelForChat = { providerId, modelId ->
                                chatViewModel.currentConversation.value?.let { conv ->
                                    chatViewModel.updateConversationSettings(
                                        systemPrompt = conv.systemPrompt,
                                        temperature = conv.temperature,
                                        maxTokens = conv.maxTokens,
                                        providerId = providerId,
                                        modelId = modelId
                                    )
                                }
                                currentTab = NavDestination.CHAT
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    NavDestination.SERVER -> {
                        ServerScreen(
                            viewModel = serverViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    NavDestination.KEYS -> {
                        if (biometricEnabled && !isBiometricAuthenticatedThisSession) {
                            BiometricLockPlaceholder(
                                onUnlock = {
                                    triggerBiometricPrompt {
                                        isBiometricAuthenticatedThisSession = true
                                    }
                                }
                            )
                        } else {
                            KeysAndModelsScreen(
                                viewModel = keysViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    NavDestination.SETTINGS -> {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    private fun triggerBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, "Autenticação necessária: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Biometria não reconhecida", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticação Biométrica")
            .setSubtitle("Confirme sua identidade para acessar as chaves de API")
            .setNegativeButtonText("Cancelar")
            .build()

        prompt.authenticate(promptInfo)
    }

    @Composable
    private fun BiometricLockPlaceholder(onUnlock: () -> Unit) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Bloqueio Biométrico",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Acesso Protegido",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A tela de Chaves está protegida por biometria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onUnlock) {
                    Text("Desbloquear com Biometria")
                }
            }
        }
    }
}
