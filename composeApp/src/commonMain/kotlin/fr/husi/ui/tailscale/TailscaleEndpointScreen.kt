package fr.husi.ui.tailscale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import fr.husi.compose.QRCodeDialog
import fr.husi.resources.Res
import fr.husi.resources.tailscale
import fr.husi.tailscale.TailscaleEndpointState
import fr.husi.ui.LocalSnackbarEmitter
import fr.husi.ui.StringOrRes
import org.jetbrains.compose.resources.stringResource

@Composable
fun TailscaleEndpointContent(
    endpointTag: String,
    endpoint: TailscaleEndpointState?,
    onBackPress: () -> Unit,
    onOpenPeer: (String) -> Unit,
    onOpenExitNode: () -> Unit,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val snackbar = LocalSnackbarEmitter.current
    var showQr by remember(endpointTag) { mutableStateOf(false) }
    TailscaleScreenScaffold(
        title = stringResource(Res.string.tailscale),
        onBackPress = onBackPress,
        modifier = modifier,
        contentModifier = contentModifier,
    ) {
        val current = endpoint ?: return@TailscaleScreenScaffold
        tailscaleEndpointContent(
            current = current,
            onOpenPeer = onOpenPeer,
            onOpenExitNode = onOpenExitNode,
            onOpenInbox = onOpenInbox,
            onOpenAuthUrl = { uriHandler.openUri(current.authUrl) },
            onShowQr = { showQr = true },
        )
    }

    if (showQr) {
        val authUrl = endpoint?.authUrl.orEmpty()
        if (authUrl.isNotEmpty()) {
            QRCodeDialog(
                url = authUrl,
                name = endpointTag,
                onDismiss = { showQr = false },
                showSnackbar = { message -> snackbar.show(StringOrRes.Direct(message)) },
            )
        }
    }
}
