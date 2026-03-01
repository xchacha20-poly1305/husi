package fr.husi.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

internal expect fun platformPluginsFlow(): Flow<List<PluginDisplay>>

internal expect fun openPluginCard(plugin: PluginDisplay)

@Composable
internal expect fun rememberShouldRequestBatteryOptimizations(): Boolean

internal expect fun requestIgnoreBatteryOptimizations()
