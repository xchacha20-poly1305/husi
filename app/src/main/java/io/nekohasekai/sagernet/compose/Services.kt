package io.nekohasekai.sagernet.compose

import android.content.ActivityNotFoundException
import android.content.ClipData
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
    showSnackbar: (message: Int) -> Unit,
) {
    try {
        return launcher.launch(input)
    } catch (_: ActivityNotFoundException) {
    } catch (_: SecurityException) {
    }
    showSnackbar(R.string.file_manager_missing)
}

suspend fun SnackbarHostState.showAndDismissOld(
    message: String,
    actionLabel: String? = null,
    withDismissAction: Boolean = false,
    duration: SnackbarDuration = if (actionLabel == null) {
        SnackbarDuration.Short
    } else {
        SnackbarDuration.Indefinite
    },
): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(message, actionLabel, withDismissAction, duration)
}
