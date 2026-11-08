package fr.husi.ui.tailscale

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.PreferenceCategory
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
import fr.husi.resources.tailscale_exit_node
import fr.husi.resources.tailscale_expired
import fr.husi.resources.tailscale_open_auth_url_qr_code
import fr.husi.resources.tailscale_peers
import fr.husi.resources.tailscale_this_device
import fr.husi.resources.tailscale_unread_count
import fr.husi.tailscale.TAILSCALE_STATE_NEEDS_LOGIN
import fr.husi.tailscale.TAILSCALE_STATE_RUNNING
import fr.husi.tailscale.TAILSCALE_STATE_STOPPED
import fr.husi.tailscale.TailscaleEndpointState
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.tailscaleEndpointContent(
    current: TailscaleEndpointState,
    onOpenPeer: (String) -> Unit,
    onOpenExitNode: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenAuthUrl: () -> Unit,
    onShowQr: () -> Unit,
) {
    preferenceGroup(key = "${current.endpointTag}:status") {
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
                        current.exitNode?.displayName ?: stringResource(Res.string.disabled),
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
                val count =
                    pluralStringResource(
                        Res.plurals.taildrop_file_count,
                        current.taildropFileCount,
                        current.taildropFileCount,
                    )
                val unread =
                    if (current.unreadFileCount > 0) {
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
                onClick = onOpenAuthUrl,
            )
            Preference(
                title = { Text(stringResource(Res.string.tailscale_open_auth_url_qr_code)) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.qr_code,
                        color = IconMaskColors.IconLavender,
                    )
                },
                onClick = onShowQr,
            )
        }
    }
    if (current.userGroups.isNotEmpty()) {
        item(key = "${current.endpointTag}:peers-header") {
            PreferenceCategory {
                Text(stringResource(Res.string.tailscale_peers))
            }
        }
    }
    for (group in current.userGroups) {
        val groupName = group.displayName.ifEmpty { group.loginName }
        item(key = "${current.endpointTag}:user-${group.userId}") {
            PreferenceCategory {
                Text(groupName)
            }
        }
        preferenceGroup(key = "${current.endpointTag}:peers-${group.userId}") {
            for (peer in group.peers) {
                Preference(
                    title = { Text(peer.displayName) },
                    summary = { Text(peerStatusSummary(peer.online, peer.exitNode, peer.expired)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.person,
                            color =
                                if (peer.online) {
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

@Composable
private fun peerStatusSummary(online: Boolean, exitNode: Boolean, expired: Boolean): String {
    val parts =
        mutableListOf(
            stringResource(
                if (online) Res.string.connected else Res.string.not_connected,
            ),
        )
    if (exitNode) parts += stringResource(Res.string.tailscale_exit_node)
    if (expired) parts += stringResource(Res.string.tailscale_expired)
    return parts.joinToString(separator = " · ")
}

private fun stateIconColor(state: String) =
    when (state) {
        TAILSCALE_STATE_RUNNING -> IconMaskColors.IconLightGreen
        TAILSCALE_STATE_NEEDS_LOGIN -> IconMaskColors.IconLightOrange
        TAILSCALE_STATE_STOPPED -> IconMaskColors.IconWarmGray
        else -> IconMaskColors.IconCoral
    }
