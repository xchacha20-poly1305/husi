package fr.husi.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import fr.husi.compose.material3.Icon
import fr.husi.compose.material3.Text
import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import fr.husi.resources.*
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
fun QRCodeDialog(
    url: String,
    name: String,
    onDismiss: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val clipboard = LocalClipboard.current
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
        generateQRCodeBitmap(url, qrSize)
    }

    val fileSaver = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { file ->
        if (file != null && qrBitmap != null) {
            scope.launch {
                try {
                    file.write(encodeImageBitmapToPng(qrBitmap))
                    showSnackbar(resolveRepository().getString(Res.string.saved_to_download))
                } catch (e: Exception) {
                    Logs.e(e)
                    showSnackbar(resolveRepository().getString(Res.string.error_title))
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.ok))
            }
        },
        icon = {
            Icon(vectorResource(Res.drawable.qr_code), null)
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
                    bitmap = qrBitmap,
                    contentDescription = stringResource(Res.string.share_qr_nfc),
                    modifier = Modifier
                        .size(with(density) { qrSize.toDp() })
                        .platformContextClickable { offset ->
                            touchOffset = offset
                            showMenu = true
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
                        text = { Text(stringResource(Res.string.action_copy)) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                clipboard.setImage(qrBitmap)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                vectorResource(Res.drawable.content_copy),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.save_to_system)) },
                        onClick = {
                            showMenu = false
                            fileSaver.launch(suggestedName = name, defaultExtension = "png")
                        },
                        leadingIcon = {
                            Icon(
                                vectorResource(Res.drawable.download),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.share)) },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                shareQRCodeImage(
                                    encodeImageBitmapToPng(qrBitmap),
                                    name,
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                vectorResource(Res.drawable.share),
                                contentDescription = null,
                            )
                        },
                    )
                }
            } else {
                Text(
                    text = stringResource(Res.string.error_title),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}
