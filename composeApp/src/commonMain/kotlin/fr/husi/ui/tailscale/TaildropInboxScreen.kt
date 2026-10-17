package fr.husi.ui.tailscale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.Preference
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.ScrollableDropdownMenuPopup
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.ktx.formatBytes
import fr.husi.ktx.readableMessage
import fr.husi.resources.Res
import fr.husi.resources.action_open
import fr.husi.resources.cancel
import fr.husi.resources.close
import fr.husi.resources.delete
import fr.husi.resources.delete_sweep
import fr.husi.resources.description
import fr.husi.resources.download
import fr.husi.resources.folder_open
import fr.husi.resources.more
import fr.husi.resources.more_vert
import fr.husi.resources.share
import fr.husi.resources.taildrop
import fr.husi.resources.taildrop_delete_all
import fr.husi.resources.taildrop_delete_all_confirm
import fr.husi.resources.taildrop_empty
import fr.husi.resources.taildrop_files
import fr.husi.resources.taildrop_from
import fr.husi.resources.taildrop_open_directory
import fr.husi.resources.taildrop_open_directory_failed
import fr.husi.resources.taildrop_open_download_directory
import fr.husi.resources.taildrop_receive
import fr.husi.resources.taildrop_save
import fr.husi.resources.taildrop_saved
import fr.husi.resources.taildrop_send
import fr.husi.tailscale.TaildropFileState
import fr.husi.tailscale.TaildropInboxViewModel
import fr.husi.tailscale.TaildropPlatform
import fr.husi.tailscale.TaildropReceivingFileState
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject

private data class InboxMenuAction(
    val label: StringResource,
    val icon: DrawableResource,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    var confirmDeleteAll by remember { mutableStateOf(false) }
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
    val overflowActions = buildList {
        if (uiState.files.isNotEmpty()) {
            add(
                InboxMenuAction(
                    label = Res.string.taildrop_delete_all,
                    icon = Res.drawable.delete_sweep,
                    onClick = { confirmDeleteAll = true },
                ),
            )
        }
        uiState.filesDirectory?.let { filesDirectory ->
            add(
                InboxMenuAction(
                    label = if (filesDirectory.isDownloads) {
                        Res.string.taildrop_open_download_directory
                    } else {
                        Res.string.taildrop_open_directory
                    },
                    icon = Res.drawable.folder_open,
                    onClick = {
                        if (!TaildropPlatform.openDirectory(filesDirectory.directory)) {
                            scope.launch {
                                snackbar.show(StringOrRes.Res(Res.string.taildrop_open_directory_failed))
                            }
                        }
                    },
                ),
            )
        }
    }
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
        title = stringResource(Res.string.taildrop),
        onBackPress = onBackPress,
        modifier = modifier,
        actions = {
            if (overflowActions.isNotEmpty()) {
                CapsuleActionButton {
                    InboxOverflowMenu(actions = overflowActions)
                }
            }
        },
        contentModifier = dropModifier,
    ) {
        if (endpointSessions.isEmpty() && uiState.receiving.isEmpty() && uiState.files.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(Res.string.taildrop_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (endpointSessions.isNotEmpty()) {
            item(key = "send-header") {
                PreferenceCategory { Text(stringResource(Res.string.taildrop_send)) }
            }
            preferenceGroup(key = "send") {
                for (session in endpointSessions) {
                    TaildropSendSessionRow(
                        session = session,
                        onCancel = { sendManager.cancel(session.id) },
                        onDismiss = { sendManager.dismiss(session.id) },
                    )
                }
            }
        }
        if (uiState.receiving.isNotEmpty()) {
            item(key = "receive-header") {
                PreferenceCategory { Text(stringResource(Res.string.taildrop_receive)) }
            }
            preferenceGroup(key = "receiving") {
                for (file in uiState.receiving) {
                    ReceivingRow(
                        file = file,
                        onCancel = { viewModel.cancelReceiving(file.senderId, file.name) },
                    )
                }
            }
        }
        if (uiState.files.isNotEmpty()) {
            item(key = "files-header") {
                PreferenceCategory { Text(stringResource(Res.string.taildrop_files)) }
            }
            preferenceGroup(key = "files") {
                for (file in uiState.files) {
                    FileRow(
                        file = file,
                        canShare = TaildropPlatform.canShareReceivedFile(),
                        onOpen = { openFile(file.name) },
                        onSave = {
                            pendingSave = file.name
                            val (suggestedName, extension) = taildropSaveName(file.name)
                            saver.launch(
                                suggestedName = suggestedName,
                                defaultExtension = extension,
                            )
                        },
                        onShare = { shareFile(file.name) },
                        onDelete = { viewModel.delete(file.name) },
                    )
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            confirmButton = {
                TextButton(stringResource(Res.string.taildrop_delete_all)) {
                    confirmDeleteAll = false
                    viewModel.deleteAll()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    confirmDeleteAll = false
                }
            },
            title = { Text(stringResource(Res.string.taildrop_delete_all)) },
            text = {
                Text(
                    pluralStringResource(
                        Res.plurals.taildrop_delete_all_confirm,
                        uiState.files.size,
                        uiState.files.size,
                    ),
                )
            },
        )
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
private fun ReceivingRow(
    file: TaildropReceivingFileState,
    onCancel: () -> Unit,
) {
    val fraction = if (file.size > 0L) {
        (file.receivedBytes.toFloat() / file.size.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    Preference(
        title = { Text(file.name) },
        summary = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (file.senderName.isNotEmpty()) {
                    Text(stringResource(Res.string.taildrop_from, file.senderName))
                }
                if (fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        icon = {
            MaskedIcon(
                resource = Res.drawable.download,
                color = IconMaskColors.IconLightBlue,
            )
        },
        widgetContainer = {
            SimpleIconButton(
                imageVector = vectorResource(Res.drawable.close),
                contentDescription = stringResource(Res.string.cancel),
                onClick = onCancel,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileRow(
    file: TaildropFileState,
    canShare: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = buildList {
        if (file.senderName.isNotEmpty()) {
            add(stringResource(Res.string.taildrop_from, file.senderName))
        }
        if (file.size >= 0L) add(file.size.formatBytes())
        if (file.modifiedAt > 0L) add(tailscaleSeenText(file.modifiedAt))
    }.joinToString(separator = " · ")
    Preference(
        title = { Text(file.name) },
        summary = if (summary.isEmpty()) null else ({ Text(summary) }),
        icon = {
            MaskedIcon(
                resource = Res.drawable.description,
                color = IconMaskColors.IconLightYellow,
            )
        },
        widgetContainer = {
            FileOverflowMenu(
                canShare = canShare,
                onOpen = onOpen,
                onSave = onSave,
                onShare = onShare,
                onDelete = onDelete,
            )
        },
        onClick = onOpen,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FileOverflowMenu(
    canShare: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val actions = buildList {
        add(InboxMenuAction(Res.string.action_open, Res.drawable.folder_open, onOpen))
        add(InboxMenuAction(Res.string.taildrop_save, Res.drawable.download, onSave))
        if (canShare) add(InboxMenuAction(Res.string.share, Res.drawable.share, onShare))
        add(InboxMenuAction(Res.string.delete, Res.drawable.delete, onDelete))
    }
    InboxOverflowMenu(actions = actions)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InboxOverflowMenu(actions: List<InboxMenuAction>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SimpleIconButton(
            imageVector = vectorResource(Res.drawable.more_vert),
            contentDescription = stringResource(Res.string.more),
            onClick = { expanded = true },
        )
        ScrollableDropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                for ((index, action) in actions.withIndex()) {
                    DropdownMenuItem(
                        selected = false,
                        text = { Text(stringResource(action.label)) },
                        onClick = {
                            expanded = false
                            action.onClick()
                        },
                        leadingIcon = {
                            Icon(vectorResource(action.icon), contentDescription = null)
                        },
                        shapes = MenuDefaults.itemShape(index, actions.size),
                    )
                }
            }
        }
    }
}

/** Suggested file name and extension for the save dialog. */
internal fun taildropSaveName(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return name to "bin"
    return name.substring(0, dot) to name.substring(dot + 1)
}
