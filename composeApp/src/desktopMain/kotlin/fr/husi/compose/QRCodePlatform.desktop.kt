package fr.husi.compose

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.awt.Desktop
import java.io.File
import java.nio.charset.StandardCharsets

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

        val width = qrBits.width
        val height = qrBits.height
        val bytesPerPixel = 4
        val pixels = ByteArray(width * height * bytesPerPixel)
        var offset = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = if (qrBits.get(x, y)) {
                    0.toByte()
                } else {
                    0xFF.toByte()
                }
                pixels[offset++] = value
                pixels[offset++] = value
                pixels[offset++] = value
                pixels[offset++] = 0xFF.toByte()
            }
        }
        Image.makeRaster(
            ImageInfo.makeN32(width, height, ColorAlphaType.OPAQUE),
            pixels,
            width * bytesPerPixel,
        ).use { it.toComposeImageBitmap() }
    } catch (e: Exception) {
        Logs.e(e)
        null
    }
}

actual fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray {
    return Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        checkNotNull(image.encodeToData()) { "Failed to encode QR code image as PNG" }
            .use { it.bytes }
    }
}

actual suspend fun shareQRCodeImage(
    pngBytes: ByteArray,
    name: String,
) = withContext(Dispatchers.IO) {
    try {
        val cacheDir = PlatformFile(PlatformFile(resolveRepository().cacheDir), "qrcodes")
        cacheDir.createDirectories()
        val platformFile = PlatformFile(cacheDir, "$name.png")
        platformFile.write(pngBytes)

        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(File(resolveRepository().cacheDir, "qrcodes/$name.png"))
        }
    } catch (e: Exception) {
        Logs.e(e)
    }
}
