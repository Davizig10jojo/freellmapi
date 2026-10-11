package com.davizig.blazergateway.ui.models

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davizig.blazergateway.domain.model.ModelInfo
import com.davizig.blazergateway.ui.keys.KeysViewModel
import com.davizig.blazergateway.ui.theme.BlazeOrange
import com.davizig.blazergateway.ui.theme.CyanAccent
import com.davizig.blazergateway.ui.theme.EmeraldHealthy
import com.davizig.blazergateway.ui.theme.VioletFree

@Composable
fun ModelsCatalogScreen(
    keysViewModel: KeysViewModel,
    onSelectModelForChat: (providerId: String, modelId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val modelsByProvider by keysViewModel.modelsByProvider.collectAsState()
    val isRefreshingAll by keysViewModel.isRefreshingAll.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("Todos") } // "Todos", "Prontos", "Ferramentas", "Visão"

    // Agrega todos os modelos de todos os provedores
    val allModels = remember(modelsByProvider) {
        val list = mutableListOf<ModelInfo>()
        for ((_, provModels) in modelsByProvider) {
            list.addAll(provModels)
        }
        list
    }

    val filteredModels = remember(allModels, searchQuery, selectedFilter) {
        allModels.filter { model ->
            val matchesQuery = searchQuery.isBlank() ||
                    model.name.contains(searchQuery, ignoreCase = true) ||
                    model.id.contains(searchQuery, ignoreCase = true) ||
                    model.providerId.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                "Prontos" -> model.isFree
                "Visão" -> model.hasVision
                "Ferramentas" -> model.contextLength >= 32768
                else -> true
            }

            matchesQuery && matchesFilter
        }.sortedBy { it.name.lowercase() }
    }

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Text(
                        text = "Modelos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${allModels.size} modelos gratuitos sincronizados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { keysViewModel.refreshAllModels() },
                    enabled = !isRefreshingAll,
                    modifier = Modifier.testTag("catalog_refresh_button")
                ) {
                    if (isRefreshingAll) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar modelos")
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(14.dp)) {
            // Barra de busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar modelos…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_search_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Abas de Filtros como no FreeLLMAPI: "Todos", "Prontos", "Ferramentas", "Visão"
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("Todos", "Prontos", "Ferramentas", "Visão").forEach { tab ->
                    FilterChip(
                        selected = selectedFilter == tab,
                        onClick = { selectedFilter = tab },
                        label = { Text(tab) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de Modelos do Catálogo
            if (filteredModels.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = if (allModels.isEmpty())
                            "Carregando catálogo dinâmico de modelos..."
                        else
                            "Nenhum modelo encontrado com estes filtros.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredModels, key = { "${it.providerId}:${it.id}" }) { model ->
                        ModelCatalogCard(
                            model = model,
                            onClick = { onSelectModelForChat(model.providerId, model.id) }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
fun ModelCatalogCard(
    model: ModelInfo,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${model.providerId} · Contexto: ${model.contextLength / 1024}k tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (model.isFree) {
                    Text(
                        text = "PRONTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldHealthy,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(EmeraldHealthy.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (model.hasVision) {
                    Text(
                        text = "VISÃO",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanAccent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
