package fr.husi.ui.tailscale

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.resources.Res
import fr.husi.resources.block
import fr.husi.resources.check
import fr.husi.resources.connected
import fr.husi.resources.disabled
import fr.husi.resources.not_connected
import fr.husi.resources.public_icon
import fr.husi.resources.search
import fr.husi.resources.tailscale_exit_node
import fr.husi.tailscale.TailscaleEndpointViewModel
import fr.husi.tailscale.TailscalePeerState
import fr.husi.tailscale.TailscaleStatusRepository
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

@Composable
fun TailscaleExitNodeScreen(
    endpointTag: String,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    repository: TailscaleStatusRepository = koinInject(),
    viewModel: TailscaleEndpointViewModel = viewModel(key = endpointTag) {
        TailscaleEndpointViewModel(
            endpointTag = endpointTag,
            repository = repository,
        )
    },
) {
    val endpoint by viewModel.endpoint.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    CollectTailscaleEvents(
        events = viewModel.uiEvent,
        closed = viewModel.closed,
        onBackPress = onBackPress,
    )

    TailscaleScreenScaffold(
        title = stringResource(Res.string.tailscale_exit_node),
        onBackPress = onBackPress,
        modifier = modifier,
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.search),
                        contentDescription = null,
                    )
                },
                placeholder = { Text(stringResource(Res.string.search)) },
            )
        }
        val current = endpoint ?: return@TailscaleScreenScaffold
        val selectedId = current.exitNode?.stableId
        preferenceGroup(key = "exit-nodes") {
            Preference(
                title = { Text(stringResource(Res.string.disabled)) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.block,
                        color = IconMaskColors.IconWarmGray,
                    )
                },
                widgetContainer = checkMark(selectedId == null),
                onClick = { viewModel.setExitNode("") },
            )
            for (peer in current.exitNodeCandidates()) {
                if (!peer.matchesExitNodeQuery(query)) continue
                Preference(
                    title = { Text(peer.displayName) },
                    summary = {
                        Text(
                            exitNodeSummary(peer),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.public_icon,
                            color = if (peer.online) {
                                IconMaskColors.IconLightGreen
                            } else {
                                IconMaskColors.IconWarmGray
                            },
                        )
                    },
                    widgetContainer = checkMark(peer.stableId == selectedId),
                    onClick = { viewModel.setExitNode(peer.stableId) },
                )
            }
        }
    }
}

@Composable
private fun checkMark(selected: Boolean): (@Composable () -> Unit)? {
    if (!selected) return null
    return {
        Icon(
            imageVector = vectorResource(Res.drawable.check),
            contentDescription = null,
        )
    }
}

@Composable
private fun exitNodeSummary(peer: TailscalePeerState): String {
    val status = stringResource(
        if (peer.online) {
            Res.string.connected
        } else {
            Res.string.not_connected
        },
    )
    val address = peer.tailscaleIps.firstOrNull()
    return if (address == null) status else "$status · $address"
}

private fun TailscalePeerState.matchesExitNodeQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    if (displayName.contains(needle, ignoreCase = true)) return true
    if (hostName.contains(needle, ignoreCase = true)) return true
    if (dnsName.contains(needle, ignoreCase = true)) return true
    return tailscaleIps.any { address -> address.contains(needle, ignoreCase = true) }
}
