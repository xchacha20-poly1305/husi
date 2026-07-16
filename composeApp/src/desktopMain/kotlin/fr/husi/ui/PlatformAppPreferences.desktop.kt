@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package fr.husi.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import fr.husi.compose.IconMaskColors
import fr.husi.compose.MaskedIcon
import fr.husi.compose.SimpleIconButton
import fr.husi.compose.hasFileList
import fr.husi.compose.material3.Text
import fr.husi.compose.rememberFileDropTarget
import fr.husi.ktx.contentOrUnset
import fr.husi.ktx.listByLineOrComma
import fr.husi.resources.Res
import fr.husi.resources.folder_open
import fr.husi.resources.legend_toggle
import fr.husi.resources.process
import fr.husi.resources.select_file
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import me.zhanghai.compose.preference.TextFieldPreference
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import java.io.File

@Composable
internal actual fun AppSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
) {
    val content = packages.joinToString("\n")
    TextFieldPreference(
        value = content,
        onValueChange = { text ->
            onSelectApps(text.listByLineOrComma().toSet())
        },
        title = { Text(stringResource(Res.string.process)) },
        icon = {
            MaskedIcon(
                resource = Res.drawable.legend_toggle,
                color = IconMaskColors.IconLavender,
            )
        },
        summary = { Text(contentOrUnset(content)) },
        textToValue = { it },
        valueToText = { it },
        textField = { value, onValueChange, onOk ->
            // Workaround stale TextFieldPreference value snapshots in asynchronous file picker and drop callbacks.
            var text by remember { mutableStateOf(value) }
            fun updateText(newValue: TextFieldValue) {
                text = newValue
                onValueChange(newValue)
            }

            fun appendPaths(paths: List<String>) {
                val selectedPaths = paths.joinToString("\n")
                val content = listOf(text.text, selectedPaths)
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
                updateText(text.copy(text = content, selection = TextRange(content.length)))
            }

            fun appendFiles(files: List<File>) {
                appendPaths(files.map { it.absolutePath })
            }
            val filePicker = rememberFilePickerLauncher(
                mode = FileKitMode.Multiple(),
            ) { files ->
                if (files == null) return@rememberFilePickerLauncher
                appendPaths(files.map { it.absolutePath() })
            }
            var isDragOver by remember { mutableStateOf(false) }
            val dropTarget = rememberFileDropTarget(
                onDragStateChange = { isDragOver = it },
                onDrop = ::appendFiles,
            )
            val highlightColor by animateColorAsState(
                targetValue = if (isDragOver) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    Color.Transparent
                },
            )
            Row(
                modifier = Modifier
                    .background(highlightColor)
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { it.hasFileList() },
                        target = dropTarget,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = ::updateText,
                    modifier = Modifier.weight(1f),
                    keyboardActions = KeyboardActions { onOk() },
                    singleLine = false,
                )
                SimpleIconButton(
                    imageVector = vectorResource(Res.drawable.folder_open),
                    contentDescription = stringResource(Res.string.select_file),
                    onClick = filePicker::launch,
                )
            }
        },
    )
}
