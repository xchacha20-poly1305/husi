package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.preferenceGroup
import fr.husi.tailscale.TaildropSendManager
import fr.husi.tailscale.TaildropSource
import fr.husi.tailscale.TailscalePeerViewModel
import fr.husi.tailscale.TailscaleSSHLogins
import fr.husi.tailscale.TailscaleSSHSessionManager
import fr.husi.tailscale.TailscaleSSHTarget
import fr.husi.tailscale.TailscaleStatusRepository
import fr.husi.tailscale.taildropDropTarget
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TailscalePeerScreen(
    endpointTag: String,
    peerId: String,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    repository: TailscaleStatusRepository = koinInject(),
    sendManager: TaildropSendManager = koinInject(),
    sshManager: TailscaleSSHSessionManager = koinInject(),
    viewModel: TailscalePeerViewModel = viewModel(key = "$endpointTag/$peerId") {
        TailscalePeerViewModel(
            endpointTag = endpointTag,
            peerId = peerId,
            repository = repository,
        )
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessions by sendManager.sessions.collectAsStateWithLifecycle()
    val endpoint = uiState.endpoint
    val peer = uiState.peer
    var dragging by remember { mutableStateOf(false) }
    var showSSHLogin by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val openSSHTerminal = rememberOpenTailscaleSSHTerminal()

    CollectTailscaleEvents(
        events = viewModel.uiEvent,
        closed = viewModel.closed,
        onBackPress = onBackPress,
    )

    val canSend = endpoint != null
        && peer != null
        && peer.online
        && peer.canReceiveFiles
        && endpoint.canShareFiles
        && endpoint.selfPeer?.id != peer.id
    val filePicker = rememberFilePickerLauncher(mode = FileKitMode.Multiple()) { files ->
        val currentPeer = viewModel.uiState.value.peer ?: return@rememberFilePickerLauncher
        if (files.isNullOrEmpty()) return@rememberFilePickerLauncher
        sendManager.send(
            tag = endpointTag,
            peerStableId = currentPeer.stableId,
            peerName = currentPeer.displayName,
            files = files.map { file -> TaildropSource(file) },
        )
    }
    val dropModifier = Modifier.taildropDropTarget(
        enabled = canSend,
        onDragStateChange = { dragging = it },
        onDrop = { sources ->
            val currentPeer = viewModel.uiState.value.peer ?: return@taildropDropTarget
            sendManager.send(
                tag = endpointTag,
                peerStableId = currentPeer.stableId,
                peerName = currentPeer.displayName,
                files = sources,
            )
        },
    )

    val canSSH = endpoint != null
        && peer != null
        && peer.online
        && peer.sshHostKeys.isNotEmpty()
        && peer.tailscaleIps.isNotEmpty()
        && endpoint.selfPeer?.id != peer.id
    if (showSSHLogin && canSSH) {
        TailscaleSSHLoginDialog(
            peerStableId = peer.stableId,
            peerName = peer.displayName,
            onDismiss = { showSSHLogin = false },
            onConnect = { login ->
                showSSHLogin = false
                scope.launch { TailscaleSSHLogins.put(peer.stableId, login) }
                sshManager.open(
                    TailscaleSSHTarget(
                        endpointTag = endpointTag,
                        peerStableId = peer.stableId,
                        peerHostName = peer.displayName,
                        peerAddress = peer.tailscaleIps.first(),
                        hostKeys = peer.sshHostKeys,
                        login = login,
                    ),
                )
                openSSHTerminal()
            },
        )
    }

    TailscalePeerContent(
        endpoint = endpoint,
        peer = peer,
        ping = uiState.ping,
        onBackPress = onBackPress,
        onStartPing = viewModel::startPing,
        onStopPing = viewModel::stopPing,
        onSsh = { showSSHLogin = true },
        onSendFiles = { filePicker.launch() },
        onLogout = viewModel::logout,
        modifier = modifier,
        dragging = dragging,
        dropModifier = dropModifier,
            sendSessions = {
            val peerSessions = sessions.filter { session ->
                session.endpointTag == endpointTag && session.peerName == peer?.displayName
            }
            if (peerSessions.isNotEmpty()) {
                preferenceGroup(key = "send-sessions") {
                    for (session in peerSessions) {
                        TaildropSendSessionRow(
                            session = session,
                            onCancel = { sendManager.cancel(session.id) },
                            onDismiss = { sendManager.dismiss(session.id) },
                        )
                    }
                }
            }
            },
    )
}
