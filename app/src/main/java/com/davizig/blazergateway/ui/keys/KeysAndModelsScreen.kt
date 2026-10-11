package com.davizig.blazergateway.ui.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davizig.blazergateway.domain.model.HealthStatus
import com.davizig.blazergateway.domain.model.ModelInfo
import com.davizig.blazergateway.domain.model.Provider
import com.davizig.blazergateway.ui.theme.BlazeOrange
import com.davizig.blazergateway.ui.theme.CrimsonError
import com.davizig.blazergateway.ui.theme.CyanAccent
import com.davizig.blazergateway.ui.theme.EmeraldHealthy
import com.davizig.blazergateway.ui.theme.VioletFree
import com.davizig.blazergateway.util.EnvParser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeysAndModelsScreen(
    viewModel: KeysViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsState()
    val healthMap by viewModel.healthMap.collectAsState()
    val modelsByProvider by viewModel.modelsByProvider.collectAsState()
    val lastUpdatedByProvider by viewModel.lastUpdatedByProvider.collectAsState()
    val dailyUsageByProvider by viewModel.dailyUsageByProvider.collectAsState()
    val isRefreshingAll by viewModel.isRefreshingAll.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    var editingKeyProvider by remember { mutableStateOf<Provider?>(null) }
    var selectingModelProvider by remember { mutableStateOf<Provider?>(null) }

    val anonymousProviders = remember(providers) { providers.filter { !it.requiresKey } }
    val keyedProviders = remember(providers) { providers.filter { it.requiresKey } }
    val hasConfiguredKey = remember(providers) {
        keyedProviders.any { viewModel.getApiKey(it.id).isNotBlank() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Cabeçalho da Tela
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Chaves",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.testTag("import_keys_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Importar")
                        }

                        Button(
                            onClick = { showAddCustomDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("add_custom_provider_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Adicionar chave")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Ações auxiliares
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("export_keys_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exportar")
                    }

                    OutlinedButton(
                        onClick = { viewModel.testAllProviders() },
                        modifier = Modifier.testTag("test_all_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Testar Todos")
                    }

                    IconButton(
                        onClick = { viewModel.refreshAllModels() },
                        enabled = !isRefreshingAll,
                        modifier = Modifier.testTag("refresh_models_button")
                    ) {
                        if (isRefreshingAll) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Atualizar modelos")
                        }
                    }
                }
            }
        }

        // Aviso de Status
        AnimatedVisibility(visible = statusMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    IconButton(
                        onClick = { viewModel.clearStatusMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
            }
        }

        // Conteúdo da Tela
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Se nenhuma chave estiver configurada, mostra card de dica do FreeLLMAPI
            if (!hasConfiguredKey) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Nenhuma chave de provedor ainda",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Você já está roteando pelos planos gratuitos anônimos abaixo (Kilo, Pollinations, OVH, AI Horde). Adicione chaves grátis para liberar muito mais modelos e cota. Nenhum cartão de crédito em lugar nenhum.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Seção 1: SEMPRE DISPONÍVEL (SEM CHAVE)
            item {
                Text(
                    text = "SEMPRE DISPONÍVEL (SEM CHAVE)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            items(anonymousProviders, key = { it.id }) { provider ->
                AnonymousProviderCard(
                    provider = provider,
                    health = healthMap[provider.id],
                    modelsCount = (modelsByProvider[provider.id] ?: emptyList()).size,
                    selectedModelId = provider.selectedModelId,
                    onSelectModel = { selectingModelProvider = provider },
                    onTest = { viewModel.testProvider(provider.id) },
                    onRefreshModels = { viewModel.refreshModels(provider.id, force = true) }
                )
            }

            // Seção 2: PROVEDORES COM CHAVE
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PROVEDORES CONFIGURÁVEIS COM CHAVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            items(keyedProviders, key = { it.id }) { provider ->
                val health = healthMap[provider.id]
                val models = modelsByProvider[provider.id] ?: emptyList()
                val lastUpdated = lastUpdatedByProvider[provider.id]
                val dailyUsage = dailyUsageByProvider[provider.id] ?: 0
                val key = viewModel.getApiKey(provider.id)

                ProviderCard(
                    provider = provider,
                    apiKey = key,
                    health = health,
                    modelsCount = models.size,
                    selectedModelId = provider.selectedModelId,
                    lastUpdated = lastUpdated,
                    dailyUsage = dailyUsage,
                    onToggleEnabled = { enabled -> viewModel.toggleProviderEnabled(provider.id, enabled) },
                    onEditKey = { editingKeyProvider = provider },
                    onSelectModel = { selectingModelProvider = provider },
                    onTestProvider = { viewModel.testProvider(provider.id) },
                    onRefreshModels = { viewModel.refreshModels(provider.id, force = true) },
                    onDeleteCustom = if (provider.isCustom) {
                        { viewModel.removeCustomProvider(provider.id) }
                    } else null
                )
            }

            item { Spacer(modifier = Modifier.height(30.dp)) }
        }
    }

    // Diálogo de Edição de Chave de API
    editingKeyProvider?.let { prov ->
        var keyInput by remember { mutableStateOf(viewModel.getApiKey(prov.id)) }
        var showPlainKey by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingKeyProvider = null },
            title = { Text("Chave de API: ${prov.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A chave será armazenada de forma segura com AES-256-GCM no Keystore do Android.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Chave de API") },
                        visualTransformation = if (showPlainKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPlainKey = !showPlainKey }) {
                                Icon(
                                    imageVector = if (showPlainKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Mostrar chave"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveApiKey(prov.id, keyInput)
                        editingKeyProvider = null
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingKeyProvider = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de Seleção de Modelo
    selectingModelProvider?.let { prov ->
        val models = modelsByProvider[prov.id] ?: emptyList()
        ModelSelectionDialog(
            provider = prov,
            models = models,
            selectedModelId = prov.selectedModelId,
            onSelect = { chosenModelId ->
                viewModel.selectModelForProvider(prov.id, chosenModelId)
                selectingModelProvider = null
            },
            onRefresh = { viewModel.refreshModels(prov.id, force = true) },
            onDismiss = { selectingModelProvider = null }
        )
    }

    // Diálogo de Importação
    if (showImportDialog) {
        ImportDialog(
            onImportEnv = { envContent ->
                val preview = viewModel.parseEnvPreview(envContent)
                viewModel.applyImportedEnv(preview)
                showImportDialog = false
            },
            onImportEncrypted = { encryptedJson, password ->
                val ok = viewModel.importEncrypted(encryptedJson, password)
                if (ok) showImportDialog = false
            },
            onDismiss = { showImportDialog = false }
        )
    }

    // Diálogo de Exportação
    if (showExportDialog) {
        ExportDialog(
            onExportEncrypted = { password ->
                val encrypted = viewModel.exportEncrypted(password)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Backup BlazerGateway", encrypted))
                Toast.makeText(context, "Backup criptografado copiado para a área de transferência!", Toast.LENGTH_LONG).show()
                showExportDialog = false
            },
            onExportPlain = {
                val plain = viewModel.exportPlainJson()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Backup BlazerGateway (JSON)", plain))
                Toast.makeText(context, "Backup JSON copiado (Atenção às chaves em texto puro)!", Toast.LENGTH_LONG).show()
                showExportDialog = false
            },
            onDismiss = { showExportDialog = false }
        )
    }

    // Diálogo de Adicionar Provedor Customizado
    if (showAddCustomDialog) {
        AddCustomProviderDialog(
            onAdd = { name, baseUrl, apiKey, endpoint ->
                viewModel.addCustomProvider(name, baseUrl, apiKey, endpoint)
                showAddCustomDialog = false
            },
            onDismiss = { showAddCustomDialog = false }
        )
    }
}

/**
 * Card para Provedores Anônimos Gratuitos (Kilo, Pollinations, OVH, AI Horde)
 */
@Composable
fun AnonymousProviderCard(
    provider: Provider,
    health: com.davizig.blazergateway.domain.model.ProviderHealth?,
    modelsCount: Int,
    selectedModelId: String?,
    onSelectModel: () -> Unit,
    onTest: () -> Unit,
    onRefreshModels: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (health?.status) {
                        HealthStatus.HEALTHY -> EmeraldHealthy
                        HealthStatus.ERROR -> CrimsonError
                        HealthStatus.TESTING -> BlazeOrange
                        else -> EmeraldHealthy
                    }
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Modelo: ${selectedModelId ?: "Automático ($modelsCount disponíveis)"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "PLANO GRATUITO ANÔNIMO",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldHealthy,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onSelectModel, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Trocar modelo")
                }
            }
        }
    }
}

@Composable
fun ProviderCard(
    provider: Provider,
    apiKey: String,
    health: com.davizig.blazergateway.domain.model.ProviderHealth?,
    modelsCount: Int,
    selectedModelId: String?,
    lastUpdated: Long?,
    dailyUsage: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onEditKey: () -> Unit,
    onSelectModel: () -> Unit,
    onTestProvider: () -> Unit,
    onRefreshModels: () -> Unit,
    onDeleteCustom: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (statusColor, _) = when (health?.status) {
                        HealthStatus.HEALTHY -> EmeraldHealthy to "${health.latencyMs ?: 0}ms"
                        HealthStatus.ERROR -> CrimsonError to "Erro"
                        HealthStatus.TESTING -> BlazeOrange to "Testando…"
                        else -> Color.Gray to "Não testado"
                    }

                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (provider.isCustom) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CUSTOM",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyanAccent.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDeleteCustom != null) {
                        IconButton(onClick = onDeleteCustom, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    Switch(
                        checked = provider.enabled,
                        onCheckedChange = onToggleEnabled
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onEditKey)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (apiKey.isNotBlank()) "Chave (••••••••)" else "Configurar chave",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (apiKey.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }

                Text(
                    text = "Uso hoje: $dailyUsage/800",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSelectModel)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modelo Selecionado:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedModelId ?: "Toque para escolher um modelo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Selecionar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$modelsCount modelos disponíveis",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRefreshModels, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar modelos", modifier = Modifier.size(16.dp))
                    }
                    TextButton(onClick = onTestProvider) {
                        Text("Testar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelSelectionDialog(
    provider: Provider,
    models: List<ModelInfo>,
    selectedModelId: String?,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterFreeOnly by remember { mutableStateOf(false) }
    var filterVisionOnly by remember { mutableStateOf(false) }

    val filtered = remember(models, searchQuery, filterFreeOnly, filterVisionOnly) {
        models.filter { model ->
            val matchesQuery = searchQuery.isBlank() ||
                    model.name.contains(searchQuery, ignoreCase = true) ||
                    model.id.contains(searchQuery, ignoreCase = true)
            val matchesFree = !filterFreeOnly || model.isFree
            val matchesVision = !filterVisionOnly || model.hasVision
            matchesQuery && matchesFree && matchesVision
        }.sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Modelos de ${provider.name}")
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar /models")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(420.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar modelo…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = filterFreeOnly,
                        onClick = { filterFreeOnly = !filterFreeOnly },
                        label = { Text("Apenas Gratuitos") }
                    )
                    FilterChip(
                        selected = filterVisionOnly,
                        onClick = { filterVisionOnly = !filterVisionOnly },
                        label = { Text("Apenas com Visão") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Text(
                            text = if (models.isEmpty()) "Nenhum modelo em cache. Toque em atualizar." else "Nenhum modelo encontrado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.id }) { model ->
                            val isChosen = model.id == selectedModelId

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(model.id) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = model.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (model.isFree) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "GRÁTIS",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = VioletFree,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Text(
                                            text = "ID: ${model.id} · Contexto: ${model.contextLength / 1024}k",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isChosen) {
                                        Icon(Icons.Default.Check, contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
fun ImportDialog(
    onImportEnv: (String) -> Unit,
    onImportEncrypted: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isEncryptedMode by remember { mutableStateOf(false) }
    var previewEnv by remember { mutableStateOf<EnvParser.ParsedEnv?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (previewEnv != null) "Prévia de Importação (.env)" else "Importar Chaves") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (previewEnv != null) {
                    val p = previewEnv!!
                    Text(
                        text = "Aviso: Arquivos .env possuem chaves em texto puro. Elas serão salvas no Keystore criptografado do dispositivo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    p.providerKeys.forEach { (prov, _) ->
                        Text("• $prov: [CHAVE DETECTADA]", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isEncryptedMode,
                            onClick = { isEncryptedMode = false },
                            label = { Text("Arquivo .env") }
                        )
                        FilterChip(
                            selected = isEncryptedMode,
                            onClick = { isEncryptedMode = true },
                            label = { Text("Backup Criptografado") }
                        )
                    }

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text(if (isEncryptedMode) "JSON criptografado" else "Cole o conteúdo do .env") },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isEncryptedMode) {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Senha do Backup") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (previewEnv != null) {
                Button(onClick = { onImportEnv(textInput) }) { Text("Confirmar e Aplicar") }
            } else {
                Button(
                    onClick = {
                        if (isEncryptedMode) {
                            onImportEncrypted(textInput, passwordInput)
                        } else {
                            previewEnv = EnvParser.parse(textInput)
                        }
                    },
                    enabled = textInput.isNotBlank() && (!isEncryptedMode || passwordInput.isNotBlank())
                ) {
                    Text(if (isEncryptedMode) "Descriptografar" else "Ver Prévia")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ExportDialog(
    onExportEncrypted: (String) -> Unit,
    onExportPlain: () -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar Chaves Seguras") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "O backup é protegido com AES-256-GCM derivado de senha com PBKDF2.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha para criptografia") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = passwordConfirm,
                    onValueChange = { passwordConfirm = it },
                    label = { Text("Confirmar senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                TextButton(onClick = onExportPlain) {
                    Text("Exportar JSON em texto puro (Aviso)", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onExportEncrypted(password) },
                enabled = password.isNotBlank() && password == passwordConfirm
            ) {
                Text("Copiar Criptografado")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun AddCustomProviderDialog(
    onAdd: (name: String, baseUrl: String, apiKey: String, chatEndpoint: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("/chat/completions") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Provedor Personalizado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome (ex: Meu Servidor vLLM / LM Studio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (ex: http://192.168.1.50:1234/v1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Chave de API (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { endpoint = it },
                    label = { Text("Endpoint de Chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, baseUrl, apiKey, endpoint) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank()
            ) {
                Text("Salvar Provedor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
