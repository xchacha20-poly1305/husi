package fr.husi.ui

import androidx.compose.runtime.Composable
import fr.husi.utils.LogExport
import kotlinx.coroutines.CoroutineScope

@Composable
internal expect fun ShareActionRow(
    scope: CoroutineScope,
    buildLog: suspend () -> LogExport,
    showSnackbar: suspend (Exception) -> Unit,
)
