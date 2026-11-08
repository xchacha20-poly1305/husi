package fr.husi.ui.dashboard

import fr.husi.tailscale.TailscaleStatusRepository

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.husi.core.remote.RemoteControlManager
import fr.husi.ui.RouteSettingsUiState
import fr.husi.ui.openconnect.OpenConnectAuthController
import fr.husi.ui.openvpn.OpenVPNAuthController
import fr.husi.ui.remote.RemoteSessionBanner
import fr.husi.ui.remote.RemoteTargetMenuSection
import org.koin.compose.koinInject

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    openConnectController: OpenConnectAuthController,
    openVPNController: OpenVPNAuthController,
    openRouteSettings: (RouteSettingsUiState) -> Unit,
    onOpenRemoteControl: () -> Unit,
    onOpenTailscaleEndpoint: (String) -> Unit,
) {
    val loadPlatformNetworkInfo = rememberLoadPlatformNetworkInfo()
    val remoteControl = koinInject<RemoteControlManager>()
    val viewModel: DashboardViewModel = viewModel {
        DashboardViewModel(loadPlatformNetworkInfo = loadPlatformNetworkInfo, remoteControl = remoteControl)
    }
    val presenter = remember(viewModel) { NativeDashboardPresenter(viewModel) }
    val remoteSession by remoteControl.session.collectAsStateWithLifecycle()
    val targetConnected by remoteControl.targetConnected.collectAsStateWithLifecycle()
    val tailscaleRepository = koinInject<TailscaleStatusRepository>()
    val tailscaleSnapshot by tailscaleRepository.snapshot.collectAsStateWithLifecycle()
    DashboardContent(
        modifier = modifier,
        dashboardViewModel = presenter,
        openConnectController = openConnectController,
        openVPNController = openVPNController,
        onCreateRoute = { fields, connection -> openRouteSettings(createRouteDraft(fields, connection)) },
        targetMenu = { count, dismiss -> RemoteTargetMenuSection(groupIndex = 0, groupCount = count, onManage = onOpenRemoteControl, onDismiss = dismiss) },
        onOpenTailscaleEndpoint = onOpenTailscaleEndpoint,
        tailscaleEndpoints = tailscaleSnapshot.endpoints,
        targetConnected = targetConnected,
        remoteSessionKey = remoteSession?.server?.id,
        remoteBanner = { RemoteSessionBanner(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal), remoteControl = remoteControl) },
    )
}

private class NativeDashboardPresenter(private val viewModel: DashboardViewModel) : DashboardPresenter {
    override val uiState get() = viewModel.uiState
    override val searchTextFieldState get() = viewModel.searchTextFieldState
    override val proxySetSearchTextFieldState get() = viewModel.proxySetSearchTextFieldState
    override val proxyGroupSearchTextFieldState get() = viewModel.proxyGroupSearchTextFieldState
    override suspend fun initialize(isConnected: Boolean) = viewModel.initialize(isConnected)
    override suspend fun resolveProcessInfo(process: String?, uid: Int) = viewModel.resolveProcessInfo(process, uid)
    override fun setDashboardActive(active: Boolean) { viewModel.setDashboardActive(active) }
    override fun togglePause() { viewModel.togglePause() }
    override fun clearSearchQuery() { viewModel.clearSearchQuery() }
    override fun toggleProxySetSearchMode() { viewModel.toggleProxySetSearchMode() }
    override fun clearProxySetGlobalQuery() { viewModel.clearProxySetGlobalQuery() }
    override fun openProxyGroupSearch(id: String) { viewModel.openProxyGroupSearch(id) }
    override fun closeProxyGroupSearch() { viewModel.closeProxyGroupSearch() }
    override fun clearProxySetSearch() { viewModel.clearProxySetSearch() }
    override fun setSortDescending(descending: Boolean) { viewModel.setSortDescending(descending) }
    override fun setSortMode(mode: Int) { viewModel.setSortMode(mode) }
    override fun setProxySetOrder(order: Int) { viewModel.setProxySetOrder(order) }
    override fun setDashboardWidgets(entries: List<DashboardWidgetEntry>) { viewModel.setDashboardWidgets(entries) }
    override fun resetDashboardWidgets() { viewModel.resetDashboardWidgets() }
    override fun setQueryActivate(queryActivate: Boolean) { viewModel.setQueryActivate(queryActivate) }
    override fun setQueryClosed(queryClosed: Boolean) { viewModel.setQueryClosed(queryClosed) }
    override fun selectConnection(uuid: String?) { viewModel.selectConnection(uuid) }
    override fun closeConnection(uuid: String) { viewModel.closeConnection(uuid) }
    override fun selectOutbound(groupName: String, tag: String) { viewModel.selectOutbound(groupName, tag) }
    override fun urlTestForSingle(tag: String) { viewModel.urlTestForSingle(tag) }
    override fun urlTestForGroup(id: String) { viewModel.urlTestForGroup(id) }
    override fun detectIP() { viewModel.detectIP() }
    override fun resetNetwork() { viewModel.resetNetwork() }
    override fun setClashMode(mode: String) { viewModel.setClashMode(mode) }
    override fun setSystemProxyEnabled(enabled: Boolean) { viewModel.setSystemProxyEnabled(enabled) }
}
