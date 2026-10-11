package com.davizig.blazergateway.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davizig.blazergateway.ui.theme.BlazeOrange
import com.davizig.blazergateway.ui.theme.CyanAccent
import com.davizig.blazergateway.ui.theme.EmeraldHealthy

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val themeChoice by viewModel.themeChoice.collectAsState()
    val proactiveThreshold by viewModel.proactiveThreshold.collectAsState()
    val cloudflareAccountId by viewModel.cloudflareAccountId.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val modelRefreshHours by viewModel.modelRefreshHours.collectAsState()

    var cloudflareInput by remember(cloudflareAccountId) { mutableStateOf(cloudflareAccountId) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Configurações",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Personalize o roteamento, tema e parâmetros de segurança",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Tema da Interface
            SettingsCard(
                icon = Icons.Default.DarkMode,
                title = "Tema da Interface",
                subtitle = "Escolha a aparência visual do aplicativo"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = themeChoice == "SYSTEM",
                        onClick = { viewModel.setThemeChoice("SYSTEM") },
                        label = { Text("Sistema") }
                    )
                    FilterChip(
                        selected = themeChoice == "DARK",
                        onClick = { viewModel.setThemeChoice("DARK") },
                        label = { Text("Escuro") }
                    )
                    FilterChip(
                        selected = themeChoice == "LIGHT",
                        onClick = { viewModel.setThemeChoice("LIGHT") },
                        label = { Text("Claro") }
                    )
                }
            }

            // 2. Limiar de Troca Proativa de Provedor
            SettingsCard(
                icon = Icons.Default.Speed,
                title = "Troca Proativa de Provedor",
                subtitle = "Pula o provedor quando o consumo atingir $proactiveThreshold% do limite da janela para evitar erros 429."
            ) {
                Text(
                    text = "Limiar atual: $proactiveThreshold%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Slider(
                    value = proactiveThreshold.toFloat(),
                    onValueChange = { viewModel.setProactiveThreshold(it.toInt()) },
                    valueRange = 50f..95f,
                    steps = 8,
                    modifier = Modifier.testTag("proactive_threshold_slider")
                )
            }

            // 3. Cloudflare Account ID
            SettingsCard(
                icon = Icons.Default.Security,
                title = "ID da Conta Cloudflare",
                subtitle = "Necessário para rotas do Cloudflare AI Workers (/accounts/{ACCOUNT_ID}/ai/v1)"
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = cloudflareInput,
                        onValueChange = { cloudflareInput = it },
                        placeholder = { Text("ACCOUNT_ID") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cloudflare_account_id_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.setCloudflareAccountId(cloudflareInput) },
                        modifier = Modifier.testTag("save_cloudflare_button")
                    ) {
                        Text("Salvar")
                    }
                }
            }

            // 4. Bloqueio Biométrico
            SettingsCard(
                icon = Icons.Default.Fingerprint,
                title = "Bloqueio Biométrico",
                subtitle = "Exigir autenticação com biometria ou PIN ao acessar a aba de Chaves e Modelos"
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (biometricEnabled) "Proteção biométrica ATIVADA" else "Proteção biométrica desativada",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (biometricEnabled) EmeraldHealthy else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) },
                        modifier = Modifier.testTag("biometric_switch")
                    )
                }
            }

            // 5. Intervalo de Atualização de Modelos
            SettingsCard(
                icon = Icons.Default.Refresh,
                title = "Cache de Modelos Dinâmicos",
                subtitle = "Intervalo para re-consultar o endpoint /models dos provedores ativos"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(12, 24, 48, 72).forEach { hours ->
                        FilterChip(
                            selected = modelRefreshHours == hours,
                            onClick = { viewModel.setModelRefreshHours(hours) },
                            label = { Text("${hours}h") }
                        )
                    }
                }
            }

            // 6. Sobre e Segurança
            SettingsCard(
                icon = Icons.Default.Info,
                title = "Segurança e Privacidade Inegociáveis",
                subtitle = "BlazerGateway opera 100% de ponta a ponta no próprio dispositivo."
            ) {
                Text(
                    text = "• Todas as chaves são cifradas com MasterKey AES-256-GCM via Android Keystore.\n" +
                            "• Nenhum intermediário: requisições saem diretamente do seu Android para a API oficial do provedor.\n" +
                            "• Backup exportável com AES-256-GCM e senha derivada via PBKDF2.\n" +
                            "• Criado por Davizig10jojo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            content()
        }
    }
}
