package fr.husi.compose

import android.content.ClipData
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry

actual suspend fun Clipboard.setPlainText(text: String) {
    val clipData = ClipData.newPlainText(null, text)
    setClipEntry(clipData.toClipEntry())
}

actual suspend fun Clipboard.getPlainText(): String? {
    return getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}