package fr.husi.compose

import androidx.compose.runtime.Composable

@Composable
expect fun rememberVpnServiceLauncher(onFailed: () -> Unit): () -> Unit
