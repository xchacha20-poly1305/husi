package fr.husi.ui.tailscale

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.QRCodeDialog
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.resources.Res
import fr.husi.resources.auth_open_url
import fr.husi.resources.connected
import fr.husi.resources.disabled
import fr.husi.resources.fiber_smart_record
import fr.husi.resources.folder_open
import fr.husi.resources.link
import fr.husi.resources.not_connected
import fr.husi.resources.person
import fr.husi.resources.public_icon
import fr.husi.resources.qr_code
import fr.husi.resources.router
import fr.husi.resources.state
import fr.husi.resources.taildrop
import fr.husi.resources.taildrop_file_count
import fr.husi.resources.tailscale
import fr.husi.resources.tailscale_exit_node
import fr.husi.resources.tailscale_expired
import fr.husi.resources.tailscale_open_auth_url_qr_code
import fr.husi.resources.tailscale_peers
import fr.husi.resources.tailscale_this_device
import fr.husi.resources.tailscale_unread_count
import fr.husi.tailscale.TAILSCALE_STATE_NEEDS_LOGIN
import fr.husi.tailscale.TAILSCALE_STATE_RUNNING
import fr.husi.tailscale.TAILSCALE_STATE_STOPPED
import fr.husi.tailscale.TaildropSendManager
import fr.husi.tailscale.TaildropSource
import fr.husi.tailscale.TailscaleEndpointViewModel
import fr.husi.tailscale.TailscaleStatusRepository
import fr.husi.tailscale.taildropDropTarget
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun TailscaleEndpointScreen(
    endpointTag: String,
    onBackPress: () -> Unit,
    onOpenPeer: (String) -> Unit,
    onOpenExitNode: () -> Unit,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
    repository: TailscaleStatusRepository = koinInject(),
    sendManager: TaildropSendManager = koinInject(),
    viewModel: TailscaleEndpointViewModel = viewModel(key = endpointTag) {
        TailscaleEndpointViewModel(
            endpointTag = endpointTag,
            repository = repository,
        )
    },
) {
    val endpoint by viewModel.endpoint.collectAsStateWithLifecycle()
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    val endpoints = snapshot.endpoints
    val sessions by sendManager.sessions.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val snackbar = LocalSnackbarEmitter.current
    var showQr by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dropped by remember { mutableStateOf<List<TaildropSource>?>(null) }

    CollectTailscaleEvents(
        events = viewModel.uiEvent,
        closed = viewModel.closed,
        onBackPress = onBackPress,
    )

    val dropModifier = Modifier
        .taildropDropTarget(
            enabled = true,
            onDragStateChange = { dragging = it },
            onDrop = { files -> dropped = files },
        )
        .background(
            if (dragging) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            } else {
                Color.Transparent
            },
        )

    TailscaleScreenScaffold(
        title = stringResource(Res.string.tailscale),
        onBackPress = onBackPress,
        modifier = modifier,
        contentModifier = dropModifier,
    ) {
        val current = endpoint ?: return@TailscaleScreenScaffold
        preferenceGroup(key = "status") {
            Preference(
                title = { Text(stringResource(Res.string.state)) },
                summary = {
                    Text(
                        text = tailscaleStateLabel(current),
                        color = tailscaleStateColor(current.backendState),
                    )
                },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.fiber_smart_record,
                        color = stateIconColor(current.backendState),
                    )
                },
            )
            current.selfPeer?.let { self ->
                Preference(
                    title = { Text(stringResource(Res.string.tailscale_this_device)) },
                    summary = { Text(self.displayName) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.router,
                            color = IconMaskColors.IconLightBlue,
                        )
                    },
                    onClick = { onOpenPeer(self.id) },
                )
            }
            if (current.hasExitNodeCandidates) {
                Preference(
                    title = { Text(stringResource(Res.string.tailscale_exit_node)) },
                    summary = {
                        Text(
                            current.exitNode?.displayName
                                ?: stringResource(Res.string.disabled),
                        )
                    },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.public_icon,
                            color = IconMaskColors.IconCyan,
                        )
                    },
                    onClick = onOpenExitNode,
                )
            }
            Preference(
                title = { Text(stringResource(Res.string.taildrop)) },
                summary = {
                    val count = pluralStringResource(
                        Res.plurals.taildrop_file_count,
                        current.taildropFileCount,
                        current.taildropFileCount,
                    )
                    val unread = if (current.unreadFileCount > 0) {
                        pluralStringResource(
                            Res.plurals.tailscale_unread_count,
                            current.unreadFileCount,
                            current.unreadFileCount,
                        )
                    } else {
                        null
                    }
                    Text(listOfNotNull(count, unread).joinToString(separator = " · "))
                },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.folder_open,
                        color = IconMaskColors.IconLightYellow,
                    )
                },
                onClick = onOpenInbox,
            )
            if (current.authUrl.isNotEmpty()) {
                Preference(
                    title = { Text(stringResource(Res.string.auth_open_url)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.link,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    onClick = { uriHandler.openUri(current.authUrl) },
                )
                Preference(
                    title = { Text(stringResource(Res.string.tailscale_open_auth_url_qr_code)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.qr_code,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    onClick = { showQr = true },
                )
            }
        }
        if (current.userGroups.isNotEmpty()) {
            item(key = "peers-header") {
                PreferenceCategory {
                    Text(stringResource(Res.string.tailscale_peers))
                }
            }
        }
        for (group in current.userGroups) {
            val groupName = group.displayName.ifEmpty { group.loginName }
            item(key = "user-${group.userId}") {
                PreferenceCategory {
                    Text(groupName)
                }
            }
            preferenceGroup(key = "peers-${group.userId}") {
                for (peer in group.peers) {
                    Preference(
                        title = { Text(peer.displayName) },
                        summary = { Text(peerStatusSummary(peer.online, peer.exitNode, peer.expired)) },
                        icon = {
                            MaskedIcon(
                                resource = Res.drawable.person,
                                color = if (peer.online) {
                                    IconMaskColors.IconLightGreen
                                } else {
                                    IconMaskColors.IconWarmGray
                                },
                            )
                        },
                        onClick = { onOpenPeer(peer.id) },
                    )
                }
            }
        }
    }

    if (showQr) {
        val authUrl = endpoint?.authUrl.orEmpty()
        if (authUrl.isNotEmpty()) {
            QRCodeDialog(
                url = authUrl,
                name = endpointTag,
                onDismiss = { showQr = false },
                showSnackbar = { message -> snackbar.show(StringOrRes.Direct(message)) },
            )
        }
    }

    TaildropTargetPicker(
        visible = dropped != null,
        endpoints = endpoints,
        sessions = sessions,
        onDismiss = { dropped = null },
        onSelect = { selected, peer ->
            val files = dropped.orEmpty()
            if (files.isNotEmpty()) {
                sendManager.send(
                    tag = selected.endpointTag,
                    peerStableId = peer.stableId,
                    peerName = peer.displayName,
                    files = files,
                )
            }
        },
        onCancelSession = sendManager::cancel,
        onDismissSession = sendManager::dismiss,
    )
}

@Composable
private fun peerStatusSummary(online: Boolean, exitNode: Boolean, expired: Boolean): String {
    val parts = mutableListOf(
        stringResource(
            if (online) Res.string.connected else Res.string.not_connected,
        ),
    )
    if (exitNode) parts += stringResource(Res.string.tailscale_exit_node)
    if (expired) parts += stringResource(Res.string.tailscale_expired)
    return parts.joinToString(separator = " · ")
}

private fun stateIconColor(state: String) = when (state) {
    TAILSCALE_STATE_RUNNING -> IconMaskColors.IconLightGreen
    TAILSCALE_STATE_NEEDS_LOGIN -> IconMaskColors.IconLightOrange
    TAILSCALE_STATE_STOPPED -> IconMaskColors.IconWarmGray
    else -> IconMaskColors.IconCoral
}
