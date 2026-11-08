package fr.husi.compose

import androidx.compose.runtime.Composable

@Composable
expect fun QRCodeDialog(url: String, name: String, onDismiss: () -> Unit, showSnackbar: suspend (String) -> Unit)
