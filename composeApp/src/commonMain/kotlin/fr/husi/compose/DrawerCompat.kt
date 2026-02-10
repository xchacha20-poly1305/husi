package fr.husi.compose

import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable

@Composable
internal expect fun DrawerCompat(
    drawerState: DrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
)

internal expect fun drawerIsCollapsible(): Boolean
