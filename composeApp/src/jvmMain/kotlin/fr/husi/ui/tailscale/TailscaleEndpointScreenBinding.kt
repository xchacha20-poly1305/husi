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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.tailscale.TaildropSendManager
import fr.husi.tailscale.TaildropSource
import fr.husi.tailscale.TailscaleEndpointViewModel
import fr.husi.tailscale.TailscaleStatusRepository
import fr.husi.tailscale.taildropDropTarget
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

    TailscaleEndpointContent(
        endpointTag, endpoint, onBackPress, onOpenPeer, onOpenExitNode, onOpenInbox,
        modifier, dropModifier,
    )

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

