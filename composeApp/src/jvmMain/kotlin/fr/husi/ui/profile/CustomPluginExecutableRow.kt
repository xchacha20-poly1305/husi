package fr.husi.ui.profile

import androidx.compose.runtime.Composable

@Composable
internal expect fun CustomPluginExecutableRow(
    pluginId: String,
    executablePath: String,
    onPluginIdChange: (String) -> Unit,
    onExecutablePathChange: (String) -> Unit,
)
