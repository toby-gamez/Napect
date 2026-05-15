package com.tkolymp.napect

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestinations(
    val label: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
) {
    HOME("Domů", Icons.Outlined.Home, Icons.Filled.Home),
    FAVORITES("Oblíbené", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    SETTINGS("Nastavení", Icons.Outlined.Settings, Icons.Filled.Settings),
}
