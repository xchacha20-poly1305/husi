package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.husi.tailscale.TailscaleSSHLogin
import fr.husi.tailscale.TailscaleSSHLogins

@Composable
internal fun TailscaleSSHLoginDialog(
    peerStableId: String,
    peerName: String,
    onDismiss: () -> Unit,
    onConnect: (TailscaleSSHLogin) -> Unit,
) {
    var login by remember(peerStableId) { mutableStateOf<TailscaleSSHLogin?>(null) }
    LaunchedEffect(peerStableId) { login = TailscaleSSHLogins.get(peerStableId) }
    login?.let { TailscaleSSHLoginContent(it, peerName, onDismiss, onConnect) }
}
