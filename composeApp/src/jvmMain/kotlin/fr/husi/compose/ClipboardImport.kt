package fr.husi.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.Clipboard

sealed interface ClipboardContent {
    data class Text(val text: String) : ClipboardContent

    data class Image(val bitmap: ImageBitmap) : ClipboardContent
}

/**
 * Reads the first item of the clipboard. Plain text is returned as is, an image is returned so
 * that the caller can try to decode a QR code from it.
 */
expect suspend fun Clipboard.getFirstContent(): ClipboardContent?

expect suspend fun Clipboard.setImage(bitmap: ImageBitmap)
