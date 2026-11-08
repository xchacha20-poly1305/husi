package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.husi.compose.preferenceGroup
import fr.husi.remote.RemoteState
import fr.husi.remote.request
import fr.husi.tailscale.RemoteInboxModel
import fr.husi.tailscale.RemotePeerModel
import fr.husi.tailscale.RemoteSshSessions
import fr.husi.tailscale.RemoteUploads
import fr.husi.tailscale.TailscaleSSHLogin
import fr.husi.tailscale.TailscaleSSHTarget
import fr.husi.tailscale.toTailscaleEndpoints
import fr.husi.ui.LocalNavigator
import fr.husi.ui.RemoteRoute
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun RemoteEndpointBinding(tag: String) {
    val remote = koinInject<RemoteState>()
    val navigator = LocalNavigator.current
    TailscaleEndpointContent(
        endpointTag = tag,
        endpoint = remote.tailscale.toTailscaleEndpoints().firstOrNull { it.endpointTag == tag },
        onBackPress = { navigator.popBackStack() },
        onOpenPeer = { navigator.navigateTo(RemoteRoute.Peer(tag, it)) },
        onOpenExitNode = { navigator.navigateTo(RemoteRoute.ExitNode(tag)) },
        onOpenInbox = { navigator.navigateTo(RemoteRoute.Inbox(tag)) },
    )
}

@Composable
internal fun RemotePeerBinding(tag: String, id: String) {
    val remote = koinInject<RemoteState>()
    val model = koinViewModel<RemotePeerModel>(parameters = { parametersOf(tag) })
    val ssh = koinInject<RemoteSshSessions>()
    val uploads = koinInject<RemoteUploads>()
    val navigator = LocalNavigator.current
    val endpoint = remote.tailscale.toTailscaleEndpoints().firstOrNull { it.endpointTag == tag }
    val peer = endpoint?.findPeer(id)
    var showLogin by remember { mutableStateOf(false) }
    TailscalePeerContent(
        endpoint = endpoint,
        peer = peer,
        ping = model.ping,
        onBackPress = { navigator.popBackStack() },
        onStartPing = model::startPing,
        onStopPing = model::stopPing,
        onSsh = { if (remote.terminalReady) showLogin = true },
        onSendFiles = { peer?.let { uploads.send(tag, it) } },
        onLogout = { remote.call("TailscaleLogout", request("endpointTag" to tag)) },
        sendSessions = {
            val sessions =
                uploads.sessions.filter {
                    it.endpointTag == tag && it.peerName == peer?.displayName
                }
            if (sessions.isNotEmpty())
                preferenceGroup(key = "send-sessions") {
                    sessions.forEach {
                        TaildropSendSessionRow(
                            it,
                            { uploads.cancel(it.id) },
                            { uploads.dismiss(it.id) },
                        )
                    }
                }
        },
    )
    if (showLogin && peer != null) {
        TailscaleSSHLoginContent(
            initialLogin = TailscaleSSHLogin(),
            peerName = peer.displayName,
            onDismiss = { showLogin = false },
            onConnect = { login ->
                showLogin = false
                ssh.open(
                    TailscaleSSHTarget(
                        tag,
                        peer.stableId,
                        peer.displayName,
                        peer.tailscaleIps.first(),
                        peer.sshHostKeys,
                        login,
                    ),
                )
                navigator.navigateTo(RemoteRoute.Ssh)
            },
        )
    }
}

@Composable
internal fun RemoteExitNodeBinding(tag: String) {
    val remote = koinInject<RemoteState>()
    val navigator = LocalNavigator.current
    TailscaleExitNodeContent(
        endpoint = remote.tailscale.toTailscaleEndpoints().firstOrNull { it.endpointTag == tag },
        onBackPress = { navigator.popBackStack() },
        onSetExitNode = {
            remote.call("SetTailscaleExitNode", request("endpointTag" to tag, "stableID" to it))
        },
    )
}

@Composable
internal fun RemoteInboxBinding(tag: String) {
    val model = koinViewModel<RemoteInboxModel>(parameters = { parametersOf(tag) })
    val uploads = koinInject<RemoteUploads>()
    val navigator = LocalNavigator.current
    TaildropInboxContent(
        files = model.files,
        receiving = model.receiving,
        endpointSessions = uploads.sessions.filter { it.endpointTag == tag },
        onBackPress = { navigator.popBackStack() },
        onCancelReceiving = model::cancelReceiving,
        onDelete = model::delete,
        onDeleteAll = model::deleteAll,
        onOpenFile = model::download,
        onSaveFile = model::download,
        onShareFile = {},
        onCancelSend = uploads::cancel,
        onDismissSend = uploads::dismiss,
    )
}

@Composable
internal fun RemoteSshBinding() {
    val ssh = koinInject<RemoteSshSessions>()
    val navigator = LocalNavigator.current
    DisposableEffect(ssh) { onDispose { ssh.closeAll() } }
    TailscaleSSHTerminalContent(
        ssh.sessions,
        ssh.activeSessionId,
        { navigator.popBackStack() },
        ssh::close,
        ssh::select,
        ssh::duplicate,
    )
}
