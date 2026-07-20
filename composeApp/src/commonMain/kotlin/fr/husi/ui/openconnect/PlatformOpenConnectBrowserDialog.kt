package fr.husi.ui.openconnect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap

@Composable
internal expect fun PlatformOpenConnectBrowserDialog(
    challengeId: String,
    request: OpenConnectBrowserRequestState,
    visible: Boolean,
    onDismiss: () -> Unit,
    onResult: (OpenConnectBrowserResultState) -> Unit,
    onError: (String) -> Unit,
)

@Composable
internal expect fun PlatformOpenConnectBrowserAuthContent(
    request: OpenConnectBrowserRequestState,
    finalUrl: String,
    onFinalUrlChange: (String) -> Unit,
    cookies: SnapshotStateMap<String, String>,
    headers: SnapshotStateMap<String, String>,
    enabled: Boolean,
)
