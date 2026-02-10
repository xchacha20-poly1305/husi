package fr.husi.compose

import androidx.compose.runtime.Composable
import fr.husi.repository.repo

@Composable
actual fun rememberVpnServiceLauncher(onFailed: () -> Unit): () -> Unit {
    return { repo.startService() }
}
