package fr.husi.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.asAwtTransferable
import fr.husi.ktx.Logs
import fr.husi.ktx.onIoDispatcher
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(StringSelection(text)))
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
                DataFlavor.stringFlavor, DataFlavor.imageFlavor -> transferable.getTransferData(flavor)
                else -> continue
            }
        } catch (e: Exception) {
            Logs.e(e)
            continue
        }
        when (data) {
            is String -> return ClipboardContent.Text(data)
            is Image -> return data.toImageBitmap()?.let(ClipboardContent::Image)
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
        graphics.drawImage(this, 0, 0, null)
        graphics.dispose()
    }
}

private suspend fun Image.toImageBitmap(): ImageBitmap? = onIoDispatcher {
    try {
        val buffered = toBufferedImage() ?: return@onIoDispatcher null
        SkiaImage.makeFromEncoded(
            ByteArrayOutputStream().also {
                ImageIO.write(buffered, "png", it)
            }.toByteArray(),
        ).toComposeImageBitmap()
    } catch (e: Exception) {
        Logs.e(e)
        null
    }
}

@OptIn(ExperimentalComposeUiApi::class)
actual suspend fun Clipboard.setImage(bitmap: ImageBitmap) {
    val image = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB).also {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(pixels)
        it.setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)
    }
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
