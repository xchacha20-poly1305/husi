package fr.husi.ui.tailscale

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.husi.resources.Res
import fr.husi.resources.details
import fr.husi.tailscale.TailscaleEndpointState
import fr.husi.tailscale.TailscalePeerState
import fr.husi.tailscale.TailscalePingState
import org.jetbrains.compose.resources.stringResource

@Composable
fun TailscalePeerContent(
    endpoint: TailscaleEndpointState?,
    peer: TailscalePeerState?,
    ping: TailscalePingState,
    onBackPress: () -> Unit,
    onStartPing: (String) -> Unit,
    onStopPing: () -> Unit,
    onSsh: () -> Unit,
    onSendFiles: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    dropModifier: Modifier = Modifier,
    sendSessions: LazyListScope.() -> Unit = {},
) {
    val canSend =
        endpoint != null &&
            peer != null &&
            peer.online &&
            peer.canReceiveFiles &&
            endpoint.canShareFiles &&
            endpoint.selfPeer?.id != peer.id
    val canSSH =
        endpoint != null &&
            peer != null &&
            peer.online &&
            peer.sshHostKeys.isNotEmpty() &&
            peer.tailscaleIps.isNotEmpty() &&
            endpoint.selfPeer?.id != peer.id
    TailscaleScreenScaffold(
        title = peer?.displayName ?: stringResource(Res.string.details),
        onBackPress = onBackPress,
        modifier = modifier,
    ) {
        if (endpoint == null || peer == null) return@TailscaleScreenScaffold
        tailscalePeerContent(
            endpoint = endpoint,
            peer = peer,
            ping = ping,
            canSSH = canSSH,
            canSend = canSend,
            dragging = dragging,
            dropModifier = dropModifier,
            onStartPing = { onStartPing(peer.tailscaleIps.first()) },
            onStopPing = onStopPing,
            onSsh = onSsh,
            onSendFiles = onSendFiles,
            onLogout = onLogout,
            sendSessions = sendSessions,
        )
    }
}
