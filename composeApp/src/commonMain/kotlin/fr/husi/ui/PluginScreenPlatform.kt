package fr.husi.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

internal expect fun platformPluginsFlow(): Flow<List<PluginDisplay>>

@Composable
internal expect fun rememberOpenPluginCard(): (PluginDisplay) -> Unit
