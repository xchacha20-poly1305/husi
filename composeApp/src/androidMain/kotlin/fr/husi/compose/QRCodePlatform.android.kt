package fr.husi.compose

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import fr.husi.ktx.Logs
import fr.husi.ktx.onIoDispatcher
import fr.husi.ktx.onMainDispatcher
import fr.husi.repository.androidRepo
import fr.husi.repository.repo
import fr.husi.resources.*
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.write
import java.io.ByteArrayOutputStream
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

        createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    this[x, y] = if (qrBits.get(x, y)) {
                        Color.BLACK
                    } else {
                        Color.WHITE
                    }
                }
            }
        }.asImageBitmap()
    } catch (e: Exception) {
        Logs.e(e)
        null
    }
}

actual fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

actual suspend fun shareQRCodeImage(
    pngBytes: ByteArray,
    name: String,
) = onIoDispatcher {
    try {
        val context = androidRepo.context
        val cacheDir = PlatformFile(PlatformFile(repo.cacheDir), "qrcodes")
        cacheDir.createDirectories()
        val platformFile = PlatformFile(cacheDir, "$name.png")
        platformFile.write(pngBytes)

        val javaFile = File(repo.cacheDir, "qrcodes/$name.png")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", javaFile)

        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        onMainDispatcher {
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    repo.getString(Res.string.share),
                ),
            )
        }
    } catch (e: Exception) {
        Logs.e(e)
    }
}
