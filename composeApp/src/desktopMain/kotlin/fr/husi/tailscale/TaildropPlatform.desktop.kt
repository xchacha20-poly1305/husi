package fr.husi.tailscale

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.husi.compose.hasFileList
import fr.husi.compose.rememberFileDropTarget
import fr.husi.ktx.Logs
import fr.husi.ktx.openUri
import fr.husi.ktx.toJavaUri
import fr.husi.repository.resolveDesktopRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.resolve

actual object TaildropPlatform {
    actual fun receiveDirectory(): PlatformFile? {
        val repository = resolveDesktopRepository()
        if (repository.coreHostState.value.isDaemon) return null
        return repository.coreRunDir.resolve(TAILDROP_DIRECTORY_NAME)
    }

    actual fun downloadDirectory(): PlatformFile =
        resolveDesktopRepository().dataDir.resolve(TAILDROP_DOWNLOAD_DIRECTORY_NAME)

    actual fun canShareReceivedFile(): Boolean = false

    actual fun openDirectory(directory: PlatformFile): Boolean {
        if (runCatching { directory.createDirectories() }.isFailure) return false
        return openPath(directory)
    }

    actual fun openReceivedFile(file: PlatformFile): Boolean {
        if (!file.isRegularFile()) return false
        return openPath(file)
    }

    actual fun shareReceivedFile(file: PlatformFile): Boolean = false
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
            onDrop(files.map { file -> TaildropSource(file) })
        },
    )
    if (!enabled) return this
    return dragAndDropTarget(
        shouldStartDragAndDrop = { event -> event.hasFileList() },
        target = target,
    )
}

private fun openPath(file: PlatformFile): Boolean {
    val error = openUri(file.toJavaUri().toString()) ?: return true
    Logs.w("open taildrop path: $error")
    return false
}
