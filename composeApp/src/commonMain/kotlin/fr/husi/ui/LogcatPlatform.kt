package fr.husi.ui

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope

@Composable
internal expect fun ShareActionRow(scope: CoroutineScope, showSnackbar: suspend (Exception)->Unit)
