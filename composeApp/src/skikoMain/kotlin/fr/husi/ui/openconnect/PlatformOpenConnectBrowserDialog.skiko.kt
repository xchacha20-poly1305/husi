package fr.husi.ui.openconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.platform.LocalUriHandler
import fr.husi.vpn.OpenConnectBrowserRequestState
import fr.husi.vpn.OpenConnectBrowserResultState

@Composable
internal actual fun PlatformOpenConnectBrowserDialog(
    challengeId: String,
    request: OpenConnectBrowserRequestState,
    visible: Boolean,
    onDismiss: () -> Unit,
    onResult: (OpenConnectBrowserResultState) -> Unit,
    onError: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(challengeId, visible) {
        if (visible) {
            runCatching { uriHandler.openUri(request.url) }
                .onFailure { onError(it.message ?: "open browser failed") }
            onDismiss()
        }
    }
}

@Composable
internal actual fun PlatformOpenConnectBrowserAuthContent(
    request: OpenConnectBrowserRequestState,
    finalUrl: String,
    onFinalUrlChange: (String) -> Unit,
    cookies: SnapshotStateMap<String, String>,
    headers: SnapshotStateMap<String, String>,
    enabled: Boolean,
) {
    OpenConnectBrowserAuthContent(
        request = request,
        finalUrl = finalUrl,
        onFinalUrlChange = onFinalUrlChange,
        cookies = cookies,
        headers = headers,
        enabled = enabled,
    )
}
