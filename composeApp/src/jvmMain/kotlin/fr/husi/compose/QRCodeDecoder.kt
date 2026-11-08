package fr.husi.compose

import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import fr.husi.ktx.Logs

private val decodeHints = mapOf(
    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    DecodeHintType.TRY_HARDER to true,
)

/** Decodes the QR code contained in [bitmap], or null if there is none. */
fun decodeQRCode(bitmap: ImageBitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    val source = try {
        val pixels = IntArray(width * height)
        bitmap.readPixels(pixels)
        RGBLuminanceSource(width, height, pixels)
    } catch (e: Exception) {
        Logs.e(e)
        return null
    }

    // HybridBinarizer suits photos, GlobalHistogramBinarizer suits clean screenshots.
    val binarizers = listOf(
        BinaryBitmap(HybridBinarizer(source)),
        BinaryBitmap(GlobalHistogramBinarizer(source)),
    )
    for (binaryBitmap in binarizers) {
        val result = try {
            MultiFormatReader().decode(binaryBitmap, decodeHints)
        } catch (_: Exception) {
            // Not found in this binarization, try the next one.
            continue
        }
        return result.text
    }
    return null
}
