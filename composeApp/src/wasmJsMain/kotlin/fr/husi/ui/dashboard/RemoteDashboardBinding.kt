package fr.husi.ui.dashboard

import androidx.compose.runtime.Composable
import fr.husi.remote.RemoteState
import fr.husi.tailscale.toTailscaleEndpoints
import fr.husi.ui.LocalNavigator
import fr.husi.ui.RemoteRoute
import fr.husi.ui.remote.RemoteBanner
import fr.husi.ui.remote.RemoteMenu
import org.koin.compose.koinInject

@Composable
internal fun RemoteDashboardBinding(page: DashboardPage) {
    val remote = koinInject<RemoteState>()
    val presenter = koinInject<RemoteDashboardPresenter>()
    val navigator = LocalNavigator.current
    DashboardContent(
        dashboardViewModel = presenter,
        openConnectController = remote.openConnectActions,
        openVPNController = remote.openVPNActions,
        targetMenu = { count, dismiss -> RemoteMenu(count, dismiss) },
        onOpenTailscaleEndpoint = { navigator.navigateTo(RemoteRoute.Endpoint(it)) },
        tailscaleEndpoints = remote.tailscale.toTailscaleEndpoints(),
        targetConnected = remote.connected,
        remoteSessionKey = remote.selected?.id?.hashCode()?.toLong(),
        remoteBanner = { RemoteBanner() },
        page = page,
    )
}
