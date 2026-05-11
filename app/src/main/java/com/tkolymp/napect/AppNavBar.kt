package com.tkolymp.napect

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource

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
                        Icon(painterResource(dest.icon), contentDescription = dest.label)
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
