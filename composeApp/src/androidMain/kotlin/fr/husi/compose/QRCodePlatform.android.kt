package fr.husi.compose

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import fr.husi.ktx.Logs
import fr.husi.ktx.shareUri
import fr.husi.repository.resolveAndroidRepository
import fr.husi.repository.resolveRepository
import fr.husi.resources.*
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.write
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
) = withContext(Dispatchers.IO) {
    try {
        val context = resolveAndroidRepository().context
        val platformFile = resolveRepository().cacheDir / "qrcodes" / "$name.png"
        platformFile.parent()?.createDirectories()
        platformFile.write(pngBytes)
        val uri = shareUri(context, platformFile)

        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        withContext(Dispatchers.Main) {
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    resolveRepository().getString(Res.string.share),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    } catch (e: Exception) {
        Logs.e(e)
    }
}
