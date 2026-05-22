package com.tkolymp.napect

import androidx.annotation.StringRes
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
    @StringRes val labelRes: Int,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
) {
    HOME("Domů", R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    FAVORITES("Oblíbené", R.string.nav_favorites, Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    SETTINGS("Nastavení", R.string.nav_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}
