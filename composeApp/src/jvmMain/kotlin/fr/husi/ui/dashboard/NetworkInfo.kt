package fr.husi.ui.dashboard

import androidx.compose.runtime.Composable

@Composable
expect fun rememberLoadPlatformNetworkInfo(): suspend () -> Triple<List<NetworkInterfaceInfo>, String?, String?>
