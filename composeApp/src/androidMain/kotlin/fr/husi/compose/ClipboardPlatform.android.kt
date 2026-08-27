package fr.husi.compose

import android.content.ClipData
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import fr.husi.ktx.Logs
import fr.husi.ktx.deleteRecursively
import fr.husi.ktx.shareUri
import fr.husi.repository.resolveAndroidRepository
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.write
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CLIPBOARD_IMAGE_CACHE_DIRECTORY = "clipboard"

actual suspend fun Clipboard.setPlainText(text: String) {
    val clipData = ClipData.newPlainText(null, text)
    setClipEntry(clipData.toClipEntry())
}

actual suspend fun Clipboard.getPlainText(): String? {
    return getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}

actual suspend fun Clipboard.getFirstContent(): ClipboardContent? {
    val item = getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
    item.text?.toString()?.let { return ClipboardContent.Text(it) }

    val context = resolveAndroidRepository().context
    val uri = item.uri ?: return null
    return withContext(Dispatchers.IO) {
        try {
            if (context.contentResolver.getType(uri)?.startsWith("image/") == true) {
                context.contentResolver.openInputStream(uri)
                    .use { BitmapFactory.decodeStream(it) }
                    ?.let { ClipboardContent.Image(it.asImageBitmap()) }
            } else {
                item.coerceToText(context)?.toString()?.let(ClipboardContent::Text)
            }
        } catch (e: Exception) {
            Logs.e(e)
            null
        }
    }
}

actual suspend fun Clipboard.setImage(bitmap: ImageBitmap) {
    val context = resolveAndroidRepository().context
    val imageFile = withContext(Dispatchers.IO) {
        val dir = resolveRepository().cacheDir / CLIPBOARD_IMAGE_CACHE_DIRECTORY
        dir.createDirectories()
        val file = dir / "${UUID.randomUUID()}.png"
        file.write(encodeImageBitmapToPng(bitmap))
        file
    }
    val uri = shareUri(context, imageFile)
    setClipEntry(ClipData.newUri(context.contentResolver, null, uri).toClipEntry())
}

internal suspend fun clearClipboardImageCache(cacheDir: PlatformFile) {
    (cacheDir / CLIPBOARD_IMAGE_CACHE_DIRECTORY).deleteRecursively()
}
