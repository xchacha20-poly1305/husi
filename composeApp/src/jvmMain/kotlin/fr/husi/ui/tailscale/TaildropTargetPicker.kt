package fr.husi.ui.tailscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import fr.husi.compose.PreferenceGroupDefaults
import fr.husi.compose.ProvidePreferenceLocals
import fr.husi.compose.material3.Text
import fr.husi.resources.Res
import fr.husi.resources.connected
import fr.husi.resources.not_connected
import fr.husi.resources.taildrop_choose_peer
import fr.husi.resources.taildrop_no_targets
import fr.husi.tailscale.TaildropSendSession
import fr.husi.tailscale.TailscaleEndpointState
import fr.husi.tailscale.TailscalePeerState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Picks a Tailscale peer that can receive files.
 * [emptyMessage] explains why no peer is listed.
 * Dismissing the sheet leaves an in-flight send running.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaildropTargetPicker(
    visible: Boolean,
    endpoints: List<TailscaleEndpointState>,
    sessions: List<TaildropSendSession>,
    onDismiss: () -> Unit,
    onSelect: (TailscaleEndpointState, TailscalePeerState) -> Unit,
    onCancelSession: (Long) -> Unit,
    onDismissSession: (Long) -> Unit,
    emptyMessage: StringResource = Res.string.taildrop_no_targets,
) {
    if (!visible) return
    val selectable = remember(endpoints) {
        endpoints.filter { endpoint -> endpoint.canShareFiles && endpoint.tailDropTargets().isNotEmpty() }
    }
    var selectedTag by remember(selectable.map { endpoint -> endpoint.endpointTag }) {
        mutableStateOf(selectable.firstOrNull()?.endpointTag)
    }
    val selected = selectable.firstOrNull { endpoint -> endpoint.endpointTag == selectedTag }
    val visibleSessions = if (selectedTag == null) {
        sessions
    } else {
        sessions.filter { session -> session.endpointTag == selectedTag }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.taildrop_choose_peer),
                style = MaterialTheme.typography.titleLarge,
            )
            if (selectable.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (endpoint in selectable) {
                        FilterChip(
                            selected = endpoint.endpointTag == selectedTag,
                            onClick = { selectedTag = endpoint.endpointTag },
                            label = { Text(endpoint.endpointTag) },
                        )
                    }
                }
            }
            val peers = selected?.tailDropTargets().orEmpty()
            if (peers.isEmpty()) {
                Text(stringResource(emptyMessage))
            } else {
                for (peer in peers) {
                    Surface(
                        onClick = { selected?.let { endpoint -> onSelect(endpoint, peer) } },
                        enabled = peer.online,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(peer.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(
                                    if (peer.online) {
                                        Res.string.connected
                                    } else {
                                        Res.string.not_connected
                                    },
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            if (visibleSessions.isNotEmpty()) {
                ProvidePreferenceLocals {
                    Column(
                        modifier = Modifier.clip(PreferenceGroupDefaults.groupShape),
                        verticalArrangement = PreferenceGroupDefaults.itemArrangement,
                    ) {
                        for (session in visibleSessions) {
                            TaildropSendSessionRow(
                                session = session,
                                onCancel = { onCancelSession(session.id) },
                                onDismiss = { onDismissSession(session.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
