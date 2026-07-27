package fr.husi.compose

import androidx.compose.ui.graphics.ImageBitmap

expect fun generateQRCodeBitmap(content: String, size: Int): ImageBitmap?

expect fun encodeImageBitmapToPng(bitmap: ImageBitmap): ByteArray

expect suspend fun shareQRCodeImage(pngBytes: ByteArray, name: String)

expect suspend fun copyQRCodeImage(bitmap: ImageBitmap, name: String)
