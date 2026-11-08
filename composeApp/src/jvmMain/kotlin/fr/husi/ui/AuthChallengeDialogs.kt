package fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fr.husi.ui.openconnect.OpenConnectAuthController
import fr.husi.ui.openconnect.OpenConnectAuthDialog
import fr.husi.ui.openvpn.OpenVPNAuthController
import fr.husi.ui.openvpn.OpenVPNAuthDialog
import org.koin.compose.koinInject

@Composable
fun AuthChallengeDialogs(onDismissed: () -> Unit) {
    val openConnectController = koinInject<OpenConnectAuthController>()
    val pendingOpenConnectAuth by openConnectController.pendingDialogAuth.collectAsState()
    pendingOpenConnectAuth?.let { pending ->
        OpenConnectAuthDialog(
            pending = pending,
            controller = openConnectController,
            onDismissed = onDismissed,
        )
    }

    val openVPNController = koinInject<OpenVPNAuthController>()
    val pendingOpenVPNAuth by openVPNController.pendingDialogAuth.collectAsState()
    pendingOpenVPNAuth?.let { pending ->
        OpenVPNAuthDialog(
            pending = pending,
            controller = openVPNController,
            onDismissed = onDismissed,
        )
    }
}
