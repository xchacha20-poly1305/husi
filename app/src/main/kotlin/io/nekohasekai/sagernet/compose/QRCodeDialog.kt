package io.nekohasekai.sagernet.compose

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onIoDispatcher
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

@Composable
fun QRCodeDialog(
    url: String,
    name: String,
    onDismiss: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var touchOffset by remember { mutableStateOf(Offset.Zero) }

    val qrSize = remember(windowInfo.containerSize) {
        val screenWidthPx = windowInfo.containerSize.width
        val screenHeightPx = windowInfo.containerSize.height
        val minDimensionPx = minOf(screenWidthPx, screenHeightPx)
        (minDimensionPx * 0.7f).toInt()
    }

    val qrBitmap = remember(url, qrSize) {
        generateQRCode(url, qrSize)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        icon = {
            Icon(ImageVector.vectorResource(R.drawable.qr_code), null)
        },
        title = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_qr_nfc),
                    modifier = Modifier
                        .size(with(density) { qrSize.toDp() })
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { offset ->
                                    touchOffset = offset
                                    showMenu = true
                                },
                            )
                        },
                )

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    offset = DpOffset(
                        x = with(density) { touchOffset.x.toDp() },
                        y = with(density) { (touchOffset.y - qrSize).toDp() },
                    ),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_to_system)) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                val success = saveQRCodeToGallery(context, qrBitmap, name)
                                showSnackbar(
                                    if (success) {
                                        context.getString(R.string.saved_to_download)
                                    } else {
                                        context.getString(R.string.error_title)
                                    },
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.download),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                shareQRCode(context, qrBitmap, name)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                ImageVector.vectorResource(R.drawable.share),
                                contentDescription = null,
                            )
                        },
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.error_title),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

private fun generateQRCode(content: String, size: Int): Bitmap? {
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
        }
    } catch (e: Exception) {
        Logs.e(e)
        null
    }
}

private suspend fun saveQRCodeToGallery(
    context: Context,
    bitmap: Bitmap,
    name: String,
): Boolean = onIoDispatcher {
    try {
        val filename = "$name.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            val contentUri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            contentUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                true
            } ?: false
        } else {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val imageFile = File(downloadsDir, filename)
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            true
        }
    } catch (e: Exception) {
        Logs.e(e)
        false
    }
}

private suspend fun shareQRCode(
    context: Context,
    bitmap: Bitmap,
    name: String,
) = onIoDispatcher {
    try {
        val cachePath = File(context.cacheDir, "qrcodes")
        cachePath.mkdirs()
        val file = File(cachePath, "$name.png")
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.cache", file)

        val shareIntent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        onMainDispatcher {
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.share),
                ),
            )
        }
    } catch (e: Exception) {
        Logs.e(e)
    }
}
