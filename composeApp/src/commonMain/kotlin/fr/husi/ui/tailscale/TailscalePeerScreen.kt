package fr.husi.ui.tailscale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.material3.Button
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.compose.setPlainText
import fr.husi.ktx.formatBytes
import fr.husi.resources.Res
import fr.husi.resources.action_copy
import fr.husi.resources.computer_cancel
import fr.husi.resources.connecting
import fr.husi.resources.content_copy
import fr.husi.resources.copy_success
import fr.husi.resources.dns
import fr.husi.resources.ipv4
import fr.husi.resources.ipv6
import fr.husi.resources.person
import fr.husi.resources.send
import fr.husi.resources.start
import fr.husi.resources.stop
import fr.husi.resources.taildrop_drop_zone
import fr.husi.resources.tailscale_active
import fr.husi.resources.tailscale_details
import fr.husi.resources.tailscale_exit_node
import fr.husi.resources.tailscale_expired
import fr.husi.resources.tailscale_hostname
import fr.husi.resources.tailscale_key_expiry
import fr.husi.resources.tailscale_last_seen
import fr.husi.resources.tailscale_logout
import fr.husi.resources.tailscale_magic_dns
import fr.husi.resources.tailscale_os
import fr.husi.resources.tailscale_ping
import fr.husi.resources.tailscale_ping_derp
import fr.husi.resources.tailscale_ping_derp_region
import fr.husi.resources.tailscale_ping_direct
import fr.husi.resources.tailscale_ping_endpoint
import fr.husi.resources.tailscale_ping_latency
import fr.husi.resources.tailscale_ping_no_data
import fr.husi.resources.tailscale_ping_peer_relay
import fr.husi.resources.tailscale_ping_peer_relay_address
import fr.husi.resources.tailscale_received
import fr.husi.resources.tailscale_shared_in
import fr.husi.resources.tailscale_transmitted
import fr.husi.resources.tools_network
import fr.husi.tailscale.TAILSCALE_STATE_RUNNING
import fr.husi.tailscale.TaildropSendManager
import fr.husi.tailscale.TaildropSource
import fr.husi.tailscale.TailscalePeerState
import fr.husi.tailscale.TailscalePeerViewModel
import fr.husi.tailscale.TailscalePingState
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
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val snackbar = LocalSnackbarEmitter.current
    var dragging by remember { mutableStateOf(false) }

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

    TailscaleScreenScaffold(
        title = peer?.displayName ?: stringResource(Res.string.tailscale_details),
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
                        widgetContainer = {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.content_copy),
                                contentDescription = stringResource(Res.string.action_copy),
                                onClick = {
                                    scope.launch { clipboard.setPlainText(address) }
                                    snackbar.show(StringOrRes.Res(Res.string.copy_success))
                                },
                            )
                        },
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
        if (canSend) {
            item(key = "send") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SendZone(
                        dragging = dragging,
                        modifier = dropModifier,
                        onClick = { filePicker.launch() },
                    )
                    val peerSessions = sessions.filter { session ->
                        session.endpointTag == endpointTag && session.peerName == peer.displayName
                    }
                    for (session in peerSessions) {
                        TaildropSendSessionRow(
                            session = session,
                            onCancel = { sendManager.cancel(session.id) },
                            onDismiss = { sendManager.dismiss(session.id) },
                            modifier = Modifier.padding(horizontal = 16.dp),
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
                    title = { Text(stringResource(Res.string.tailscale_details)) },
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
        if (peer.active) add(stringResource(Res.string.tailscale_active))
        if (peer.expired) add(stringResource(Res.string.tailscale_expired))
        if (peer.shareeNode) add(stringResource(Res.string.tailscale_shared_in))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

@Composable
private fun PingCard(
    ping: TailscalePingState,
    enabled: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.tailscale_ping),
                style = MaterialTheme.typography.titleMedium,
            )
            when {
                ping.running && !ping.hasResult -> Text(stringResource(Res.string.connecting))
                ping.hasResult -> {
                    Text(stringResource(Res.string.tailscale_ping_latency, ping.latencyMilliseconds.toInt()))
                    Text(pingPathLabel(ping))
                    if (ping.endpoint.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.tailscale_ping_endpoint) + ": " + ping.endpoint,
                        )
                    }
                    if (ping.derpRegionCode.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.tailscale_ping_derp_region) + ": " + ping.derpRegionCode,
                        )
                    }
                    if (ping.peerRelay.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.tailscale_ping_peer_relay_address) + ": " + ping.peerRelay,
                        )
                    }
                    PingSparkline(samples = ping.latencyHistory)
                }

                else -> Text(stringResource(Res.string.tailscale_ping_no_data))
            }
            if (ping.error.isNotEmpty()) {
                Text(ping.error, color = MaterialTheme.colorScheme.error)
            }
            if (ping.running) {
                Button(onClick = onStop) {
                    Text(stringResource(Res.string.stop))
                }
            } else {
                Button(onClick = onStart, enabled = enabled) {
                    Text(stringResource(Res.string.start))
                }
            }
        }
    }
}

@Composable
private fun pingPathLabel(ping: TailscalePingState): String = when {
    ping.peerRelay.isNotEmpty() -> stringResource(Res.string.tailscale_ping_peer_relay)
    ping.direct -> stringResource(Res.string.tailscale_ping_direct)
    else -> stringResource(Res.string.tailscale_ping_derp)
}

@Composable
private fun PingSparkline(samples: List<Float>, modifier: Modifier = Modifier) {
    if (samples.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        val min = samples.min()
        val max = samples.max()
        val span = (max - min).takeIf { value -> value > 0f } ?: 1f
        val path = Path()
        samples.forEachIndexed { index, sample ->
            val x = index.toFloat() / (samples.lastIndex).toFloat() * size.width
            val y = size.height - (sample - min) / span * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun SendZone(
    dragging: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .background(
                if (dragging) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.taildrop_drop_zone),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
