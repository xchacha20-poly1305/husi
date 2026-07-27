package fr.husi.compose

import android.content.ClipData
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.core.content.FileProvider
import fr.husi.ktx.onIoDispatcher
import fr.husi.repository.resolveAndroidRepository
import fr.husi.repository.resolveRepository
import java.io.File
import java.util.UUID

private const val CLIPBOARD_IMAGE_CACHE_DIRECTORY = "clipboard"

actual suspend fun Clipboard.setPlainText(text: String) {
    val clipData = ClipData.newPlainText(null, text)
    setClipEntry(clipData.toClipEntry())
}

actual suspend fun Clipboard.getPlainText(): String? {
    return getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}

actual suspend fun Clipboard.setImage(bitmap: ImageBitmap) {
    val context = resolveAndroidRepository().context
    val imageFile = onIoDispatcher {
        File(
            resolveRepository().cacheDir.resolve(CLIPBOARD_IMAGE_CACHE_DIRECTORY),
            "${UUID.randomUUID()}.png",
        ).also {
            it.parentFile?.mkdirs()
            it.writeBytes(encodeImageBitmapToPng(bitmap))
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", imageFile)
    setClipEntry(ClipData.newUri(context.contentResolver, null, uri).toClipEntry())
}

internal fun clearClipboardImageCache(cacheDir: File) {
    cacheDir.resolve(CLIPBOARD_IMAGE_CACHE_DIRECTORY).deleteRecursively()
}
