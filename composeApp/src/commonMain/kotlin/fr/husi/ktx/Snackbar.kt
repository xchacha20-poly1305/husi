package fr.husi.ktx

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult

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
