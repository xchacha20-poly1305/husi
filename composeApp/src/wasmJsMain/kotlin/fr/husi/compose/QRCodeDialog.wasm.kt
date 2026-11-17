@file:OptIn(ExperimentalWasmJsInterop::class)

package fr.husi.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import fr.husi.ktx.Logs
import fr.husi.resources.Res
import fr.husi.resources.close
import fr.husi.resources.error_title
import fr.husi.resources.save_to_system
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlin.io.encoding.Base64
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun QRCodeDialog(
    url: String,
    name: String,
    onDismiss: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val errorMessage = stringResource(Res.string.error_title)
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var pngBytes by remember(url) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(url) {
        try {
            val bytes = Base64.decode(toStringValue(qrPng(url).await()).substringAfter(','))
            image = decodeSkiaImage(bytes)
            pngBytes = bytes
        } catch (failure: Exception) {
            if (failure is CancellationException) {
                throw failure
            }
            Logs.w("Generating QR code failed", failure)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                image?.let { Image(it, name) }
                SelectionContainer { Text(url) }
            }
        },
        dismissButton = {
            TextButton(
                enabled = pngBytes != null,
                onClick = {
                    val bytes = pngBytes ?: return@TextButton
                    scope.launch {
                        try {
                            FileKit.download(bytes, "$name.png")
                        } catch (failure: Exception) {
                            if (failure is CancellationException) throw failure
                            Logs.w("Saving QR code failed", failure)
                            showSnackbar(errorMessage)
                        }
                    }
                },
            ) {
                Text(stringResource(Res.string.save_to_system))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        },
    )
}

@JsFun("(value) => husiBrowser.qrPng(value)")
private external fun qrPng(value: String): Promise<JsString>

@JsFun("(value) => value") private external fun toStringValue(value: JsString): String
