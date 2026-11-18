package fr.husi.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

expect object TaildropPlatform {
    fun receiveDirectory(): PlatformFile?

    fun downloadDirectory(): PlatformFile

    fun canShareReceivedFile(): Boolean

    fun openDirectory(directory: PlatformFile): Boolean

    fun openReceivedFile(file: PlatformFile): Boolean

    fun shareReceivedFile(file: PlatformFile): Boolean
}

@Composable
expect fun Modifier.taildropDropTarget(
    enabled: Boolean,
    onDragStateChange: (Boolean) -> Unit = {},
    onDrop: (List<TaildropSource>) -> Unit,
): Modifier
