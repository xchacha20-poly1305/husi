package fr.husi.ui.configuration

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import fr.husi.ktx.Logs

class ZxingQRCodeAnalyzer(
    private val onSuccess: ((String) -> Unit),
    private val onFailure: ((Exception) -> Unit),
) : ImageAnalysis.Analyzer {

    private val qrCodeReader = QRCodeReader()
    private var luminance = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        try {
            val width = image.width
            val height = image.height
            copyLuminance(image.planes[0], width, height)
            val source = PlanarYUVLuminanceSource(
                luminance,
                width,
                height,
                0,
                0,
                width,
                height,
                false,
            )
            val result = try {
                qrCodeReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)))
            } catch (_: NotFoundException) {
                try {
                    qrCodeReader.decode(BinaryBitmap(GlobalHistogramBinarizer(source.invert())))
                } catch (_: NotFoundException) {
                    return
                }
            }
            Logs.d("ZxingQRCodeAnalyzer: barcode decode success: ${result.text}")
            onSuccess(result.text)
        } catch (e: Exception) {
            onFailure(e)
        } finally {
            image.close()
        }
    }

    private fun copyLuminance(plane: ImageProxy.PlaneProxy, width: Int, height: Int) {
        val pixelCount = width * height
        if (luminance.size != pixelCount) {
            luminance = ByteArray(pixelCount)
        }
        val pixels = plane.buffer.duplicate()
        val start = pixels.position()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        if (pixelStride == 1 && rowStride == width) {
            pixels.get(luminance)
            return
        }
        for (row in 0 until height) {
            val rowStart = start + row * rowStride
            val destination = row * width
            if (pixelStride == 1) {
                pixels.position(rowStart)
                pixels.get(luminance, destination, width)
            } else {
                for (column in 0 until width) {
                    luminance[destination + column] = pixels.get(rowStart + column * pixelStride)
                }
            }
        }
    }
}
