package fr.husi.ui.tailscale

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.ktx.readableMessage
import fr.husi.resources.Res
import fr.husi.resources.folder_open
import fr.husi.resources.taildrop_open_directory
import fr.husi.resources.taildrop_open_directory_failed
import fr.husi.resources.taildrop_open_download_directory
import fr.husi.resources.taildrop_saved
import fr.husi.tailscale.TaildropInboxViewModel
import fr.husi.tailscale.TaildropPlatform
import fr.husi.tailscale.TaildropSendManager
import fr.husi.tailscale.TaildropSource
import fr.husi.tailscale.TailscaleStatusRepository
import fr.husi.tailscale.taildropDropTarget
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun TaildropInboxScreen(
    endpointTag: String,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    repository: TailscaleStatusRepository = koinInject(),
    sendManager: TaildropSendManager = koinInject(),
    viewModel: TaildropInboxViewModel = viewModel(key = endpointTag) {
        TaildropInboxViewModel(
            endpointTag = endpointTag,
            repository = repository,
            localReceiveDirectory = TaildropPlatform.receiveDirectory(),
            downloadDirectory = TaildropPlatform.downloadDirectory(),
        )
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    val endpoints = snapshot.endpoints
    val sessions by sendManager.sessions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarEmitter.current
    var dragging by remember { mutableStateOf(false) }
    var dropped by remember { mutableStateOf<List<TaildropSource>?>(null) }
    var pendingSave by remember { mutableStateOf<String?>(null) }
    val saver = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { file ->
        val name = pendingSave
        if (file == null || name == null) return@rememberFileSaverLauncher
        scope.launch {
            try {
                file.write(viewModel.materialize(name).readBytes())
                snackbar.show(StringOrRes.Res(Res.string.taildrop_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.show(StringOrRes.Direct(e.readableMessage))
            }
        }
    }

    CollectTailscaleEvents(
        events = viewModel.uiEvent,
        closed = viewModel.closed,
        onBackPress = onBackPress,
    )

    fun openFile(name: String) {
        scope.launch {
            try {
                val file = viewModel.materialize(name)
                TaildropPlatform.openReceivedFile(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.show(StringOrRes.Direct(e.readableMessage))
            }
        }
    }

    fun shareFile(name: String) {
        scope.launch {
            try {
                val file = viewModel.materialize(name)
                TaildropPlatform.shareReceivedFile(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.show(StringOrRes.Direct(e.readableMessage))
            }
        }
    }

    val endpointSessions = sessions.filter { session -> session.endpointTag == endpointTag }
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

    TaildropInboxContent(
        files = uiState.files,
        receiving = uiState.receiving,
        endpointSessions = endpointSessions,
        onBackPress = onBackPress,
        onCancelReceiving = { sender, name -> viewModel.cancelReceiving(sender, name) },
        onDelete = { viewModel.delete(it) },
        onDeleteAll = { viewModel.deleteAll() },
        onOpenFile = ::openFile,
        onSaveFile = { name ->
            pendingSave = name
            val (suggestedName, extension) = taildropSaveName(name)
            saver.launch(suggestedName = suggestedName, defaultExtension = extension)
        },
        onShareFile = ::shareFile,
        onCancelSend = sendManager::cancel,
        onDismissSend = sendManager::dismiss,
        modifier = modifier,
        contentModifier = dropModifier,
        canShare = TaildropPlatform.canShareReceivedFile(),
        directoryAction = uiState.filesDirectory?.let { directory ->
            InboxMenuAction(
                if (directory.isDownloads) Res.string.taildrop_open_download_directory else Res.string.taildrop_open_directory,
                Res.drawable.folder_open,
            ) {
                if (!TaildropPlatform.openDirectory(directory.directory)) {
                    scope.launch { snackbar.show(StringOrRes.Res(Res.string.taildrop_open_directory_failed)) }
                }
            }
        },
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

