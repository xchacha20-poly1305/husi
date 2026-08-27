package fr.husi.utils.appicon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.source
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.Icon
import kotlinx.io.buffered
import kotlinx.io.readByteArray
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
                    val png = IcnsDecoder.extractBestPng(file.readAllBytes()) ?: return null
                    decodeEncodedBytes(png)
                }
                "svg" -> decodeSvgBytes(file.readAllBytes())
                "xpm" -> decodeWithImageIO(file)
                else -> decodeEncodedBytes(file.readAllBytes())
            }
        } catch (_: Exception) {
            null
        }
    }

    fun decodeSwingIcon(icon: Icon): ImageBitmap? {
        return try {
            val width = icon.iconWidth.coerceAtLeast(1)
            val height = icon.iconHeight.coerceAtLeast(1)
            val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = buffered.createGraphics()
            icon.paintIcon(null, graphics, 0, 0)
            graphics.dispose()
            decodeEncodedBytes(buffered.toPngBytes() ?: return null)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSvgBytes(bytes: ByteArray): ImageBitmap? {
        val svg = SVGDOM(Data.makeFromBytes(bytes))
        svg.setContainerSize(SVG_RENDER_SIZE.toFloat(), SVG_RENDER_SIZE.toFloat())
        val surface = Surface.makeRasterN32Premul(SVG_RENDER_SIZE, SVG_RENDER_SIZE)
        try {
            svg.render(surface.canvas)
            return surface.makeImageSnapshot().toComposeImageBitmap()
        } finally {
            surface.close()
        }
    }

    private fun decodeEncodedBytes(bytes: ByteArray): ImageBitmap? {
        return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    }

    private fun decodeWithImageIO(file: PlatformFile): ImageBitmap? {
        val buffered = ImageIO.read(ByteArrayInputStream(file.readAllBytes())) ?: return null
        val png = buffered.toPngBytes() ?: return null
        return decodeEncodedBytes(png)
    }

    private fun PlatformFile.readAllBytes(): ByteArray {
        return source().buffered().use { it.readByteArray() }
    }

    private fun BufferedImage.toPngBytes(): ByteArray? {
        val stream = ByteArrayOutputStream()
        if (!ImageIO.write(this, "png", stream)) return null
        return stream.toByteArray()
    }
}
