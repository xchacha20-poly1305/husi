package fr.husi.ui.log

import androidx.compose.runtime.Composable
import fr.husi.utils.LogExport
import kotlinx.coroutines.CoroutineScope

@Composable
actual fun ShareActionRow(
    scope: CoroutineScope,
    buildLog: suspend () -> LogExport,
    showSnackbar: suspend (Exception) -> Unit,
) {
}
