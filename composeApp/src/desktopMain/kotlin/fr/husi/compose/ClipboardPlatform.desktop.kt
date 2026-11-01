package fr.husi.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import fr.husi.ktx.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(StringSelection(text)))
}

/**
 * Use it outside LocalClipboard scope
 */
fun setSystemClipboardPlainText(text: String) = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.getPlainText(): String? {
    return runCatching {
        getClipEntry()?.asAwtTransferable
            ?.takeIf { it.isDataFlavorSupported(DataFlavor.stringFlavor) }
            ?.getTransferData(DataFlavor.stringFlavor) as? String
    }.getOrNull()
}

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.getFirstContent(): ClipboardContent? {
    val transferable = try {
        getClipEntry()?.asAwtTransferable
    } catch (e: Exception) {
        Logs.e(e)
        null
    } ?: return null

    // Flavors are ordered by the provider's preference, so the first supported one is
    // the "first item" of the clipboard.
    for (flavor in transferable.transferDataFlavors) {
        val data = try {
            when (flavor) {
                DataFlavor.stringFlavor, DataFlavor.imageFlavor -> {
                    withContext(Dispatchers.IO) {
                        transferable.getTransferData(flavor)
                    }
                }

                else -> continue
            }
        } catch (e: Exception) {
            Logs.e(e)
            continue
        }
        return when (data) {
            is String -> ClipboardContent.Text(data)
            is Image -> data.toImageBitmap()?.let(ClipboardContent::Image)
            else -> continue
        }
    }
    return null
}

private fun Image.toBufferedImage(): BufferedImage? {
    if (this is BufferedImage) return this
    val width = getWidth(null)
    val height = getHeight(null)
    if (width <= 0 || height <= 0) return null
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
        val graphics = it.createGraphics()
        try {
            graphics.drawImage(this, 0, 0, null)
        } finally {
            graphics.dispose()
        }
    }
}

private suspend fun Image.toImageBitmap(): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val buffered = toBufferedImage() ?: return@withContext null
        buffered.toComposeImageBitmap()
    } catch (e: Exception) {
        Logs.e(e)
        null
    }
}

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setImage(bitmap: ImageBitmap) {
    val image = bitmap.toAwtImage()
    val transferable = object : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            require(isDataFlavorSupported(flavor))
            return image
        }
    }
    setClipEntry(ClipEntry(transferable))
}
