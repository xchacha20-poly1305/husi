@file:OptIn(ExperimentalComposeUiApi::class)

package fr.husi.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

fun DragAndDropEvent.hasFileList(): Boolean = runCatching {
    awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
}.getOrDefault(false)

fun DragAndDropEvent.droppedFiles(): List<File> = runCatching {
    @Suppress("UNCHECKED_CAST")
    awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
}.getOrNull().orEmpty()

@Composable
fun rememberFileDropTarget(
    onDragStateChange: (Boolean) -> Unit = {},
    onDrop: (List<File>) -> Unit,
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
