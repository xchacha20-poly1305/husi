package fr.husi.utils.appicon

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.Icon
import org.jetbrains.skia.Data
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.Image as SkiaImage

internal object AppIconDecoding {
    private const val SVG_RENDER_SIZE = 128

    fun decodeFile(file: File): ImageBitmap? {
        return try {
            when (file.extension.lowercase()) {
                "icns" -> {
                    val png = IcnsDecoder.extractBestPng(file.readBytes()) ?: return null
                    decodeEncodedBytes(png)
                }
                "svg" -> decodeSvgBytes(file.readBytes())
                "xpm" -> decodeWithImageIO(file)
                else -> decodeEncodedBytes(file.readBytes())
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

    private fun decodeWithImageIO(file: File): ImageBitmap? {
        val buffered = ImageIO.read(file) ?: return null
        val png = buffered.toPngBytes() ?: return null
        return decodeEncodedBytes(png)
    }

    private fun BufferedImage.toPngBytes(): ByteArray? {
        val stream = ByteArrayOutputStream()
        if (!ImageIO.write(this, "png", stream)) return null
        return stream.toByteArray()
    }
}
