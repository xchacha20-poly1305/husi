package fr.husi.ui.tailscale

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.compose.setPlainText
import fr.husi.ktx.formatBytes
import fr.husi.resources.Res
import fr.husi.resources.action_copy
import fr.husi.resources.active
import fr.husi.resources.computer_cancel
import fr.husi.resources.content_copy
import fr.husi.resources.copy_success
import fr.husi.resources.details
import fr.husi.resources.dns
import fr.husi.resources.ipv4
import fr.husi.resources.ipv6
import fr.husi.resources.person
import fr.husi.resources.send
import fr.husi.resources.tailscale_exit_node
import fr.husi.resources.tailscale_expired
import fr.husi.resources.tailscale_hostname
import fr.husi.resources.tailscale_key_expiry
import fr.husi.resources.tailscale_last_seen
import fr.husi.resources.tailscale_logout
import fr.husi.resources.tailscale_magic_dns
import fr.husi.resources.tailscale_os
import fr.husi.resources.tailscale_received
import fr.husi.resources.tailscale_shared_in
import fr.husi.resources.tailscale_ssh
import fr.husi.resources.tailscale_ssh_summary
import fr.husi.resources.tailscale_transmitted
import fr.husi.resources.terminal
import fr.husi.resources.tools_network
import fr.husi.tailscale.TAILSCALE_STATE_RUNNING
import fr.husi.tailscale.TaildropSendManager
import fr.husi.tailscale.TaildropSource
import fr.husi.tailscale.TailscalePeerState
import fr.husi.tailscale.TailscalePeerViewModel
import fr.husi.tailscale.TailscaleSSHLogins
import fr.husi.tailscale.TailscaleSSHSessionManager
import fr.husi.tailscale.TailscaleSSHTarget
import fr.husi.tailscale.TailscaleStatusRepository
import fr.husi.tailscale.taildropDropTarget
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
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

    TailscaleScreenScaffold(
        title = peer?.displayName ?: stringResource(Res.string.details),
        onBackPress = onBackPress,
        modifier = modifier,
    ) {
        if (endpoint == null || peer == null) return@TailscaleScreenScaffold
        preferenceGroup(key = "identity") {
            if (endpoint.selfPeer?.id == peer.id && endpoint.networkName.isNotEmpty()) {
                Preference(
                    title = { Text(stringResource(Res.string.tools_network)) },
                    summary = { Text(endpoint.networkName) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.dns,
                            color = IconMaskColors.IconLightBlue,
                        )
                    },
                )
            }
            if (peer.dnsName.isNotEmpty()) {
                Preference(
                    title = { Text(stringResource(Res.string.tailscale_magic_dns)) },
                    summary = { Text(peer.dnsName) },
                    widgetContainer = { CopyButton(peer.dnsName) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.dns,
                            color = IconMaskColors.IconCyan,
                        )
                    },
                )
            }
            if (peer.hostName.isNotEmpty()) {
                Preference(
                    title = { Text(stringResource(Res.string.tailscale_hostname)) },
                    summary = { Text(peer.hostName) },
                    widgetContainer = { CopyButton(peer.hostName) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.person,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                )
            }
        }
        if (peer.tailscaleIps.isNotEmpty()) {
            preferenceGroup(key = "addresses") {
                for (address in peer.tailscaleIps) {
                    val label = stringResource(
                        if (address.contains(':')) {
                            Res.string.ipv6
                        } else {
                            Res.string.ipv4
                        },
                    )
                    Preference(
                        title = { Text(label) },
                        summary = { Text(address) },
                        icon = {
                            MaskedIcon(
                                resource = Res.drawable.dns,
                                color = IconMaskColors.IconLightBlue,
                            )
                        },
                        widgetContainer = { CopyButton(address) },
                    )
                }
            }
        }
        item(key = "ping") {
            PingCard(
                ping = uiState.ping,
                enabled = peer.tailscaleIps.isNotEmpty(),
                onStart = { viewModel.startPing(peer.tailscaleIps.first()) },
                onStop = viewModel::stopPing,
            )
        }
        if (canSSH) {
            preferenceGroup(key = "ssh") {
                Preference(
                    title = { Text(stringResource(Res.string.tailscale_ssh)) },
                    summary = { Text(stringResource(Res.string.tailscale_ssh_summary)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.terminal,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    onClick = { showSSHLogin = true },
                )
            }
        }
        if (canSend) {
            item(key = "send") {
                SendFilesCard(
                    peerName = peer.displayName,
                    dragging = dragging,
                    onClick = { filePicker.launch() },
                    modifier = dropModifier,
                )
            }
            val peerSessions = sessions.filter { session ->
                session.endpointTag == endpointTag && session.peerName == peer.displayName
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
        }
        preferenceGroup(key = "details") {
            if (peer.operatingSystem.isNotEmpty()) {
                Preference(
                    title = { Text(stringResource(Res.string.tailscale_os)) },
                    summary = { Text(peer.operatingSystem) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.person,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                )
            }
            Preference(
                title = { Text(stringResource(Res.string.tailscale_key_expiry)) },
                summary = {
                    Text(
                        text = tailscaleKeyExpiryText(peer),
                        color = if (peer.expired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Unspecified
                        },
                    )
                },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.computer_cancel,
                        color = if (peer.expired) IconMaskColors.IconCoral else IconMaskColors.IconLightOrange,
                    )
                },
            )
            if (!peer.online && peer.lastSeen > 0L) {
                Preference(
                    title = { Text(stringResource(Res.string.tailscale_last_seen)) },
                    summary = { Text(tailscaleSeenText(peer.lastSeen)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.person,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                )
            }
            Preference(
                title = { Text(stringResource(Res.string.tailscale_received)) },
                summary = { Text(peer.receivedBytes.formatBytes()) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.send,
                        color = IconMaskColors.IconLightGreen,
                    )
                },
            )
            Preference(
                title = { Text(stringResource(Res.string.tailscale_transmitted)) },
                summary = { Text(peer.transmittedBytes.formatBytes()) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.send,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
            )
            peerBadgeSummary(peer)?.let { summary ->
                Preference(
                    title = { Text(stringResource(Res.string.details)) },
                    summary = { Text(summary) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.person,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                )
            }
            val canLogout = endpoint.selfPeer?.id == peer.id
                && !endpoint.keyAuth
                && endpoint.backendState == TAILSCALE_STATE_RUNNING
            if (canLogout) {
                Preference(
                    title = {
                        Text(
                            text = stringResource(Res.string.tailscale_logout),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.computer_cancel,
                            color = IconMaskColors.IconCoral,
                        )
                    },
                    onClick = viewModel::logout,
                )
            }
        }
    }
}

@Composable
private fun peerBadgeSummary(peer: TailscalePeerState): String? {
    val parts = buildList {
        if (peer.exitNode) add(stringResource(Res.string.tailscale_exit_node))
        if (peer.active) add(stringResource(Res.string.active))
        if (peer.expired) add(stringResource(Res.string.tailscale_expired))
        if (peer.shareeNode) add(stringResource(Res.string.tailscale_shared_in))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

@Composable
private fun CopyButton(text: String) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val snackbar = LocalSnackbarEmitter.current
    SimpleIconButton(
        imageVector = vectorResource(Res.drawable.content_copy),
        contentDescription = stringResource(Res.string.action_copy),
        onClick = {
            scope.launch { clipboard.setPlainText(text) }
            snackbar.show(StringOrRes.Res(Res.string.copy_success))
        },
    )
}
