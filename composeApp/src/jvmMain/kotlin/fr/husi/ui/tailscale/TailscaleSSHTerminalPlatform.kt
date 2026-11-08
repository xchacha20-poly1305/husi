package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberOpenTailscaleSSHTerminal(): () -> Unit
