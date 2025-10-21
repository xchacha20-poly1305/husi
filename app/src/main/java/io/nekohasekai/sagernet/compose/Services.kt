package io.nekohasekai.sagernet.compose

import android.content.ActivityNotFoundException
import android.content.ClipData
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import io.nekohasekai.sagernet.R

suspend fun Clipboard.setPlainText(text: String) {
    val clipData = ClipData.newPlainText(null, text)
    setClipEntry(clipData.toClipEntry())
}

fun startFilesForResult(
    launcher: ActivityResultLauncher<String>,
    input: String,
    showSnackbar: (message:Int)->Unit,
) {
    try {
        return launcher.launch(input)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
    showSnackbar(R.string.file_manager_missing)
}