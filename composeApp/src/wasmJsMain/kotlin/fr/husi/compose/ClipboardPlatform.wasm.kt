@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)

package fr.husi.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlin.js.toJsString
import kotlin.js.toList
import kotlinx.coroutines.await
import org.w3c.fetch.Response

private const val PLAIN_TEXT_MIME_TYPE = "text/plain"

actual suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry.withPlainText(text))
}

actual suspend fun Clipboard.getPlainText(): String? {
    val entry = getClipEntry() ?: return null
    for (item in entry.clipboardItems.toList()) {
        if (item.types.toList().none { it.toString() == PLAIN_TEXT_MIME_TYPE }) {
            continue
        }
        val content = item.getType(PLAIN_TEXT_MIME_TYPE.toJsString()).await()
        // The browser bindings lack Blob.text(); Response decodes the blob without a request.
        return Response(content).text().await().toString()
    }
    return null
}
