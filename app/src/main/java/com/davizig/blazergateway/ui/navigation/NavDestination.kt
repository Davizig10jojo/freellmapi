package com.davizig.blazergateway.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CHAT(
        route = "chat",
        title = "Bate-papo",
        selectedIcon = Icons.Filled.ChatBubble,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline
    ),
    MODELS(
        route = "models",
        title = "Modelos",
        selectedIcon = Icons.Filled.Layers,
        unselectedIcon = Icons.Outlined.Layers
    ),
    SERVER(
        route = "server",
        title = "Servidor",
        selectedIcon = Icons.Filled.Wifi,
        unselectedIcon = Icons.Outlined.Wifi
    ),
    KEYS(
        route = "keys",
        title = "Chaves",
        selectedIcon = Icons.Filled.Key,
        unselectedIcon = Icons.Outlined.Key
    ),
    SETTINGS(
        route = "settings",
        title = "Mais",
        selectedIcon = Icons.Filled.MoreHoriz,
        unselectedIcon = Icons.Outlined.MoreHoriz
    )
}
