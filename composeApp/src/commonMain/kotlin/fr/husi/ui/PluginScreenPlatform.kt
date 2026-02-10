package fr.husi.ui

import androidx.compose.runtime.Composable

internal expect suspend fun loadPlatformPlugins(onPlugin: suspend (PluginDisplay) -> Unit)

internal expect fun openPluginCard(plugin: PluginDisplay)

@Composable
internal expect fun rememberShouldRequestBatteryOptimizations(): Boolean

internal expect fun requestIgnoreBatteryOptimizations()
