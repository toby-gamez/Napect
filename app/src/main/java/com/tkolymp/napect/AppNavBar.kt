package com.tkolymp.napect

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun AppNavBar(
    currentDestination: AppDestinations,
    onDestinationChange: (AppDestinations) -> Unit,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.values().forEach { dest ->
                item(
                    icon = {
                        val image: ImageVector = if (dest == currentDestination) dest.filledIcon else dest.outlinedIcon
                        Icon(imageVector = image, modifier = Modifier.size(30.dp), contentDescription = dest.label)
                    },
                    label = { Text(dest.label) },
                    selected = dest == currentDestination,
                    onClick = { onDestinationChange(dest) }
                )
            }
        }
    ) {
        content()
    }
}
