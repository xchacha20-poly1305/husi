@file:OptIn(ExperimentalComposeUiApi::class)

package fr.husi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import fr.husi.ktx.platformFilesFromAwtFileList
import io.github.vinceglb.filekit.PlatformFile
import java.awt.datatransfer.DataFlavor

fun DragAndDropEvent.hasFileList(): Boolean = runCatching {
    awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
}.getOrDefault(false)

fun DragAndDropEvent.droppedFiles(): List<PlatformFile> = runCatching {
    platformFilesFromAwtFileList(awtTransferable.getTransferData(DataFlavor.javaFileListFlavor))
}.getOrNull().orEmpty()

@Composable
fun rememberFileDropTarget(
    onDragStateChange: (Boolean) -> Unit = {},
    onDrop: (List<PlatformFile>) -> Unit,
): DragAndDropTarget {
    val onDragStateChangeState = rememberUpdatedState(onDragStateChange)
    val onDropState = rememberUpdatedState(onDrop)
    return remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                onDragStateChangeState.value(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                onDragStateChangeState.value(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                onDragStateChangeState.value(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                onDragStateChangeState.value(false)
                val files = event.droppedFiles()
                if (files.isEmpty()) return false
                onDropState.value(files)
                return true
            }
        }
    }
}
