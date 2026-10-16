package fr.husi.tailscale

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.husi.repository.resolveAndroidRepository
import java.io.File

actual object TaildropPlatform {
    actual fun receiveDirectory(): File? =
        TaildropDocumentRoot.Receive.directory(resolveAndroidRepository().context)

    actual fun downloadDirectory(): File =
        TaildropDocumentRoot.Downloads.directory(resolveAndroidRepository().context)

    actual fun canShareReceivedFile(): Boolean = true

    actual fun openDirectory(directory: File): Boolean {
        val context = resolveAndroidRepository().context
        val root = TaildropDocumentRoot.ofDirectory(context, directory) ?: return false
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val authority = taildropDocumentsAuthority(context)
        // The provider omits a root while its directory is missing; DocumentsUI caches roots.
        context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(authority), null)
        val rootIntent = Intent(Intent.ACTION_VIEW).setDataAndType(
            DocumentsContract.buildRootUri(authority, root.rootId),
            "vnd.android.document/root",
        )
        val directoryIntent = Intent(Intent.ACTION_VIEW).setDataAndType(
            DocumentsContract.buildDocumentUri(authority, root.rootId),
            DocumentsContract.Document.MIME_TYPE_DIR,
        )
        return context.startViewer(rootIntent) || context.startViewer(directoryIntent)
    }

    actual fun openReceivedFile(file: File): Boolean {
        val context = resolveAndroidRepository().context
        val uri = taildropDocumentUri(context, file) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, taildropMimeType(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return context.startViewer(intent)
    }

    actual fun shareReceivedFile(file: File): Boolean {
        val context = resolveAndroidRepository().context
        val uri = taildropDocumentUri(context, file) ?: return false
        val intent = Intent(Intent.ACTION_SEND)
            .setType(taildropMimeType(file.name))
            .putExtra(Intent.EXTRA_STREAM, uri)
            // The chooser forwards the grant only through ClipData.
            .also { it.clipData = ClipData.newRawUri(file.name, uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return context.startViewer(Intent.createChooser(intent, file.name))
    }
}

private fun Context.startViewer(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Composable
actual fun Modifier.taildropDropTarget(
    enabled: Boolean,
    onDragStateChange: (Boolean) -> Unit,
    onDrop: (List<TaildropSource>) -> Unit,
): Modifier = this
