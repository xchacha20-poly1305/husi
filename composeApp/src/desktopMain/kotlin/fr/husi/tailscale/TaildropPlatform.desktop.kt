package fr.husi.tailscale

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.husi.compose.hasFileList
import fr.husi.compose.rememberFileDropTarget
import fr.husi.ktx.Logs
import fr.husi.ktx.openUri
import fr.husi.repository.resolveDesktopRepository
import io.github.vinceglb.filekit.PlatformFile
import java.io.File

actual object TaildropPlatform {
    actual fun receiveDirectory(): File? {
        val repository = resolveDesktopRepository()
        if (repository.coreHostState.value.isDaemon) return null
        return repository.coreRunDir.resolve(TAILDROP_DIRECTORY_NAME)
    }

    actual fun downloadDirectory(): File =
        resolveDesktopRepository().dataDir.resolve(TAILDROP_DOWNLOAD_DIRECTORY_NAME)

    actual fun canShareReceivedFile(): Boolean = false

    actual fun openDirectory(directory: File): Boolean {
        if (!directory.isDirectory && !directory.mkdirs()) return false
        return openPath(directory)
    }

    actual fun openReceivedFile(file: File): Boolean {
        if (!file.isFile) return false
        return openPath(file)
    }

    actual fun shareReceivedFile(file: File): Boolean = false
}

@Composable
actual fun Modifier.taildropDropTarget(
    enabled: Boolean,
    onDragStateChange: (Boolean) -> Unit,
    onDrop: (List<TaildropSource>) -> Unit,
): Modifier {
    val target = rememberFileDropTarget(
        onDragStateChange = onDragStateChange,
        onDrop = { files ->
            onDrop(files.map { file -> TaildropSource(PlatformFile(file)) })
        },
    )
    if (!enabled) return this
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.hasFileList() },
        target = target,
    )
}

private fun openPath(file: File): Boolean {
    val error = openUri(file.toURI().toString()) ?: return true
    Logs.w("open taildrop path: $error")
    return false
}
