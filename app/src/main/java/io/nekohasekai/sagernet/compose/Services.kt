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
import io.nekohasekai.sagernet.ui.StringOrRes
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

class ComposeSnackbarAdapter(
    private val showSnackbar: suspend (message: StringOrRes, actionLabel: StringOrRes) -> SnackbarResult,
    private val scope: CoroutineScope,
) : UndoSnackbarManager.SnackbarAdapter {
    private lateinit var message: StringOrRes
    private lateinit var actionLabel: StringOrRes
    private lateinit var onAction: (() -> Unit)
    private lateinit var onDismiss: (() -> Unit)
    private var lastJob: Job? = null

    override fun setMessage(message: StringOrRes): UndoSnackbarManager.SnackbarAdapter {
        this.message = message
        return this
    }

    override fun setAction(actionLabel: StringOrRes): UndoSnackbarManager.SnackbarAdapter {
        this.actionLabel = actionLabel
        return this
    }

    override fun setOnAction(block: () -> Unit): UndoSnackbarManager.SnackbarAdapter {
        this.onAction = block
        return this
    }

    override fun setOnDismiss(block: () -> Unit): UndoSnackbarManager.SnackbarAdapter {
        this.onDismiss = block
        return this
    }

    override fun show() {
        lastJob = scope.launch {
            val result = showSnackbar(message, actionLabel)
            when (result) {
                SnackbarResult.ActionPerformed -> onAction()
                SnackbarResult.Dismissed -> onDismiss()
            }
        }
    }

    override fun flush() {
        lastJob?.cancel()
    }
}