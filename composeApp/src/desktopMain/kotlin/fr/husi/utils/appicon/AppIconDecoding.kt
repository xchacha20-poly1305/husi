package fr.husi.utils.appicon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.util.fastCoerceAtLeast
import fr.husi.ktx.inputStream
import fr.husi.ktx.readBytesBlocking
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.Icon
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.Image as SkiaImage

internal object AppIconDecoding {
    private const val SVG_RENDER_SIZE = 128

    fun decodeFile(file: PlatformFile): ImageBitmap? {
        return try {
            when (file.extension.lowercase()) {
                "icns" -> {
                    val png = IcnsDecoder.extractBestPng(file.readBytesBlocking()) ?: return null
                    decodeEncodedBytes(png)
                }
                "svg" -> decodeSvgBytes(file.readBytesBlocking())
                "xpm" -> decodeWithImageIO(file)
                else -> decodeEncodedBytes(file.readBytesBlocking())
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeSwingIcon(icon: Icon): ImageBitmap? {
        return try {
            val width = icon.iconWidth.fastCoerceAtLeast(1)
            val height = icon.iconHeight.fastCoerceAtLeast(1)
            val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = buffered.createGraphics()
            try {
                icon.paintIcon(null, graphics, 0, 0)
            } finally {
                graphics.dispose()
            }
            buffered.toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSvgBytes(bytes: ByteArray): ImageBitmap {
        val svg = SVGDOM(Data.makeFromBytes(bytes))
        svg.setContainerSize(SVG_RENDER_SIZE.toFloat(), SVG_RENDER_SIZE.toFloat())
        val surface = Surface.makeRasterN32Premul(SVG_RENDER_SIZE, SVG_RENDER_SIZE)
        surface.use { surface ->
            svg.render(surface.canvas)
            return surface.makeImageSnapshot().toComposeImageBitmap()
        }
    }

    private fun decodeEncodedBytes(bytes: ByteArray): ImageBitmap {
        return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    private fun decodeWithImageIO(file: PlatformFile): ImageBitmap? {
        val buffered = file.inputStream().use(ImageIO::read) ?: return null
        return buffered.toComposeImageBitmap()
    }
}
