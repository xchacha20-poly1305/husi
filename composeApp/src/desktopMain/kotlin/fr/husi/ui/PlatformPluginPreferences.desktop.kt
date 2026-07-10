@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package fr.husi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.husi.compose.MaskedIcon
import fr.husi.compose.PreferenceCategory
import fr.husi.compose.PreferenceMaskColors
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.TextButton
import fr.husi.compose.material3.Text
import fr.husi.compose.preferenceGroup
import fr.husi.database.PluginEntity
import fr.husi.database.SagerDatabase
import fr.husi.fmt.PluginEntry
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.contentOrUnset
import fr.husi.resources.Res
import fr.husi.resources.cancel
import fr.husi.resources.desktop_plugins
import fr.husi.resources.folder_open
import fr.husi.resources.ok
import fr.husi.resources.select_file
import fr.husi.resources.settings
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.parent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.Preference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import java.awt.datatransfer.DataFlavor
import java.io.File
import kotlin.enums.enumEntries

internal actual fun LazyListScope.platformPluginPreferences(
    needRestart: () -> Unit,
) {
    item("desktop_plugins_category") {
        PreferenceCategory(text = { Text(stringResource(Res.string.desktop_plugins)) })
    }
    preferenceGroup("desktop_plugins") {
        DesktopPluginPreferences()
    }
}

@Composable
private fun DesktopPluginPreferences() {
    val scope = rememberCoroutineScope()
    val plugins by SagerDatabase.pluginDao.getAll().collectAsStateWithLifecycle(emptyList())
    val pluginMap = remember(plugins) { plugins.associateBy { it.pluginId } }
    val knownEntries = remember { enumEntries<PluginEntry>() }

    for (entry in knownEntries) {
        val current = pluginMap[entry.pluginId]
        PluginPathPreference(
            pluginId = entry.pluginId,
            displayName = stringResource(entry.displayName),
            current = current,
            onUpdate = { pluginId, plugin ->
                scope.launch(Dispatchers.IO) {
                    upsertPlugin(pluginId, plugin)
                }
            },
        )
    }
}

@Composable
private fun PluginPathPreference(
    pluginId: String,
    displayName: String,
    current: PluginEntity?,
    onUpdate: (String, PluginEntity?) -> Unit,
) {
    val path = current?.path.orEmpty()
    val onValueChange: (String) -> Unit = { newPath ->
        val trimmed = newPath.trim()
        if (trimmed.isBlank()) {
            onUpdate(pluginId, null)
        } else {
            onUpdate(
                pluginId,
                PluginEntity(
                    pluginId = pluginId,
                    path = trimmed,
                ),
            )
        }
    }
    val onValueChangeState = rememberUpdatedState(onValueChange)
    var isRowDragOver by remember { mutableStateOf(false) }
    val rowDropTarget = remember(pluginId) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isRowDragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isRowDragOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isRowDragOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isRowDragOver = false
                val file = event.firstDroppedFile() ?: return false
                onValueChangeState.value(file.absolutePath)
                return true
            }
        }
    }
    val rowHighlightColor by animateColorAsState(
        targetValue = if (isRowDragOver) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            Color.Transparent
        },
    )
    var openDialog by rememberSaveable { mutableStateOf(false) }

    Preference(
        title = { Text(displayName) },
        modifier = Modifier
            .fillMaxWidth()
            .background(rowHighlightColor)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { it.hasFileList() },
                target = rowDropTarget,
            ),
        icon = {
            MaskedIcon(Res.drawable.settings, color = PreferenceMaskColors.IconWarmGray)
        },
        summary = { Text(contentOrUnset(path)) },
        onClick = { openDialog = true },
    )

    if (openDialog) {
        PluginPathDialog(
            title = displayName,
            initialPath = path,
            onDismiss = { openDialog = false },
            onConfirm = { newPath ->
                onValueChange(newPath)
                openDialog = false
            },
        )
    }
}

@Composable
private fun PluginPathDialog(
    title: String,
    initialPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var dialogText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialPath, TextRange(initialPath.length)))
    }
    var isDialogDragOver by remember { mutableStateOf(false) }
    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDialogDragOver = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDialogDragOver = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDialogDragOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDialogDragOver = false
                val file = event.firstDroppedFile() ?: return false
                val absolute = file.absolutePath
                dialogText = TextFieldValue(absolute, TextRange(absolute.length))
                return true
            }
        }
    }
    val containerColor by animateColorAsState(
        targetValue = if (isDialogDragOver) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            AlertDialogDefaults.containerColor
        },
    )
    val filePicker = rememberFilePickerLauncher(
        directory = initialPath.blankAsNull()?.let(::PlatformFile)?.parent(),
    ) { file ->
        if (file == null) return@rememberFilePickerLauncher
        val absolute = file.absolutePath()
        dialogText = TextFieldValue(absolute, TextRange(absolute.length))
    }
    val onOk = { onConfirm(dialogText.text) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(stringResource(Res.string.ok), onClick = onOk) },
        modifier = Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { it.hasFileList() },
            target = dropTarget,
        ),
        dismissButton = { TextButton(stringResource(Res.string.cancel), onClick = onDismiss) },
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = dialogText,
                    onValueChange = { dialogText = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    keyboardActions = KeyboardActions { onOk() },
                    singleLine = true,
                )
                SimpleIconButton(
                    imageVector = vectorResource(Res.drawable.folder_open),
                    contentDescription = stringResource(Res.string.select_file),
                    onClick = filePicker::launch,
                )
            }
        },
        containerColor = containerColor,
    )

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
}

private suspend fun upsertPlugin(pluginId: String, plugin: PluginEntity?) {
    if (plugin == null) {
        SagerDatabase.pluginDao.delete(pluginId)
    } else {
        SagerDatabase.pluginDao.upsert(plugin)
    }
}

private fun DragAndDropEvent.hasFileList(): Boolean = runCatching {
    awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
}.getOrDefault(false)

private fun DragAndDropEvent.firstDroppedFile(): File? = runCatching {
    @Suppress("UNCHECKED_CAST")
    val fileList = awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
    fileList?.firstOrNull()
}.getOrNull()
