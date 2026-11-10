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
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import fr.husi.resources.Res
import fr.husi.resources.close
import kotlin.io.encoding.Base64
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun QRCodeDialog(
    url: String,
    name: String,
    onDismiss: () -> Unit,
    showSnackbar: suspend (String) -> Unit,
) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        try {
            image =
                decodeSkiaImage(
                    Base64.decode(toStringValue(qrPng(url).await()).substringAfter(',')),
                )
        } catch (failure: Exception) {
            if (failure is CancellationException) {
                throw failure
            }
            showSnackbar(failure.message ?: failure.toString())
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
        },
    )
}

@JsFun("(value) => husiBrowser.qrPng(value)")
private external fun qrPng(value: String): Promise<JsString>

@JsFun("(value) => value") private external fun toStringValue(value: JsString): String
