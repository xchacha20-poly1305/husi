package fr.husi.ui.tailscale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import fr.husi.resources.taildrop_receive
import fr.husi.resources.taildrop_save
import fr.husi.resources.taildrop_send
import fr.husi.tailscale.TaildropFileState
import fr.husi.tailscale.TaildropReceivingFileState
import fr.husi.tailscale.TaildropSendSession
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

internal data class InboxMenuAction(
    val label: StringResource,
    val icon: DrawableResource,
    val onClick: () -> Unit,
)

@Composable
internal fun TaildropInboxContent(
    files: List<TaildropFileState>,
    receiving: List<TaildropReceivingFileState>,
    endpointSessions: List<TaildropSendSession>,
    onBackPress: () -> Unit,
    onCancelReceiving: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onOpenFile: (String) -> Unit,
    onSaveFile: (String) -> Unit,
    onShareFile: (String) -> Unit,
    onCancelSend: (Long) -> Unit,
    onDismissSend: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    canShare: Boolean = false,
    directoryAction: InboxMenuAction? = null,
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val overflowActions = buildList {
        if (files.isNotEmpty())
            add(
                InboxMenuAction(Res.string.taildrop_delete_all, Res.drawable.delete_sweep) {
                    confirmDeleteAll = true
                },
            )
        directoryAction?.let { add(it) }
    }
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
        contentModifier = contentModifier,
    ) {
        if (endpointSessions.isEmpty() && receiving.isEmpty() && files.isEmpty()) {
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
                        onCancel = { onCancelSend(session.id) },
                        onDismiss = { onDismissSend(session.id) },
                    )
                }
            }
        }
        if (receiving.isNotEmpty()) {
            item(key = "receive-header") {
                PreferenceCategory { Text(stringResource(Res.string.taildrop_receive)) }
            }
            preferenceGroup(key = "receiving") {
                for (file in receiving) {
                    ReceivingRow(
                        file = file,
                        onCancel = { onCancelReceiving(file.senderId, file.name) },
                    )
                }
            }
        }
        if (files.isNotEmpty()) {
            item(key = "files-header") {
                PreferenceCategory { Text(stringResource(Res.string.taildrop_files)) }
            }
            preferenceGroup(key = "files") {
                for (file in files) {
                    FileRow(
                        file = file,
                        canShare = canShare,
                        onOpen = { onOpenFile(file.name) },
                        onSave = { onSaveFile(file.name) },
                        onShare = { onShareFile(file.name) },
                        onDelete = { onDelete(file.name) },
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
                    onDeleteAll()
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
                        files.size,
                        files.size,
                    ),
                )
            },
        )
    }
}

@Composable
private fun ReceivingRow(
    file: TaildropReceivingFileState,
    onCancel: () -> Unit,
) {
    val fraction =
        if (file.size > 0L) {
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
    }
        .joinToString(separator = " · ")
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
