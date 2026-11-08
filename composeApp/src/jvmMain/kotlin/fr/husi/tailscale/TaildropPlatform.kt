package fr.husi.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

expect object TaildropPlatform {
    fun receiveDirectory(): File?

    fun downloadDirectory(): File

    fun canShareReceivedFile(): Boolean

    fun openDirectory(directory: File): Boolean

    fun openReceivedFile(file: File): Boolean

    fun shareReceivedFile(file: File): Boolean
}

@Composable
expect fun Modifier.taildropDropTarget(
    enabled: Boolean,
    onDragStateChange: (Boolean) -> Unit = {},
    onDrop: (List<TaildropSource>) -> Unit,
): Modifier
