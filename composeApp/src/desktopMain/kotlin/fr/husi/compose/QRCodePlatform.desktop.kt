package fr.husi.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import fr.husi.ktx.Logs
import fr.husi.ktx.onIoDispatcher
import fr.husi.repository.repo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.write
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO

actual fun generateQRCodeBitmap(content: String, size: Int): ImageBitmap? {
    return try {
        val hints = mutableMapOf<EncodeHintType, Any>()
        val iso88591 = StandardCharsets.ISO_8859_1.newEncoder()
        if (!iso88591.canEncode(content)) {
            hints[EncodeHintType.CHARACTER_SET] = StandardCharsets.UTF_8.name()
        }

        val qrBits = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints,
        )

        val image = BufferedImage(size, size, BufferedImage.TYPE_USHORT_565_RGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                image.setRGB(x, y, if (qrBits.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        Image.makeFromBitmap(
            Bitmap.makeFromImage(
                Image.makeFromEncoded(
                    ByteArrayOutputStream().also {
                        ImageIO.write(image, "png", it)
                    }.toByteArray()
                )
            )
        ).toComposeImageBitmap()
    } catch (e: Exception) {
        Logs.e(e)
        null
    }
}

actual fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.readPixels(pixels)
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, pixels, 0, width)
    val stream = ByteArrayOutputStream()
    ImageIO.write(image, "png", stream)
    return stream.toByteArray()
}

actual suspend fun shareQRCodeImage(
    pngBytes: ByteArray,
    name: String,
) = onIoDispatcher {
    try {
        val cacheDir = PlatformFile(PlatformFile(repo.cacheDir), "qrcodes")
        cacheDir.createDirectories()
        val platformFile = PlatformFile(cacheDir, "$name.png")
        platformFile.write(pngBytes)

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(File(repo.cacheDir, "qrcodes/$name.png"))
        }
    } catch (e: Exception) {
        Logs.e(e)
    }
}
