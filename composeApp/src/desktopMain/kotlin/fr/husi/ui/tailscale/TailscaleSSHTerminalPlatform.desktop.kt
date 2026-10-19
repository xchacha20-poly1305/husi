package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberOpenTailscaleSSHTerminal(): () -> Unit = TailscaleSSHWindowState::show
