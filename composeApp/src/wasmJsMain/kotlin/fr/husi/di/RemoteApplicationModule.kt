@file:OptIn(KoinExperimentalAPI::class)

package fr.husi.di

import fr.husi.compose.material3.PlatformMaterialApi
import fr.husi.compose.material3.standardPlatformMaterialApi
import fr.husi.compose.theme.PlatformThemeApi
import fr.husi.compose.theme.standardPlatformThemeApi
import fr.husi.database.IndexedDbRemoteServerStore
import fr.husi.database.RemoteServerStore
import fr.husi.remote.BrowserClient
import fr.husi.remote.RemoteClient
import fr.husi.remote.RemoteState
import fr.husi.repository.Repository
import fr.husi.repository.WebRepository
import fr.husi.tailscale.RemoteInboxModel
import fr.husi.tailscale.RemotePeerModel
import fr.husi.tailscale.RemoteSshSessions
import fr.husi.tailscale.RemoteUploads
import fr.husi.ui.LibrariesScreen
import fr.husi.ui.LocalNavigator
import fr.husi.ui.MainScreenScope
import fr.husi.ui.RemoteAboutBinding
import fr.husi.ui.RemoteRoute
import fr.husi.ui.SnackbarEmitter
import fr.husi.ui.dashboard.DashboardPage
import fr.husi.ui.dashboard.RemoteDashboardBinding
import fr.husi.ui.dashboard.RemoteDashboardPresenter
import fr.husi.ui.log.RemoteLogBinding
import fr.husi.ui.log.RemoteLogViewModel
import fr.husi.ui.remote.RemoteServerEditor
import fr.husi.ui.remote.RemoteServerEditorBinding
import fr.husi.ui.remote.RemoteServersBinding
import fr.husi.ui.remote.RemoteSetupBinding
import fr.husi.ui.settings.GeneralSettingsScreen
import fr.husi.ui.settings.SettingsScreen
import fr.husi.ui.tailscale.RemoteEndpointBinding
import fr.husi.ui.tailscale.RemoteExitNodeBinding
import fr.husi.ui.tailscale.RemoteInboxBinding
import fr.husi.ui.tailscale.RemotePeerBinding
import fr.husi.ui.tailscale.RemoteSshBinding
import fr.husi.ui.tools.RemoteNetworkBinding
import fr.husi.ui.tools.RemoteNetworkQualityPresenter
import fr.husi.ui.tools.RemoteQualityBinding
import fr.husi.ui.tools.RemoteStunBinding
import fr.husi.ui.tools.RemoteStunPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.dsl.onClose

/** The services that reach the browser; tests replace them with fakes. */
internal val browserPlatformModule = module {
    single<Repository> { WebRepository() }
    single<RemoteClient> { BrowserClient() }
    single<RemoteServerStore> { IndexedDbRemoteServerStore() } onClose { it?.close() }
}

internal val remoteApplicationModule = module {
    single<PlatformMaterialApi> { standardPlatformMaterialApi() }
    single<PlatformThemeApi> { standardPlatformThemeApi() }
    scope<MainScreenScope> {
        scoped { CoroutineScope(SupervisorJob() + Dispatchers.Default) } onClose { it?.cancel() }
        scoped { RemoteState(get(), get(), get()) } onClose { it?.close() }
        scoped { RemoteDashboardPresenter(get(), get()) }
        scopedOf(::SnackbarEmitter)
        scopedOf(::RemoteUploads)
        scopedOf(::RemoteSshSessions) onClose { it?.dispose() }
        viewModel { parameters -> RemoteServerEditor(parameters.getOrNull(), get()) }
        viewModel { RemoteLogViewModel(get()) }
        viewModel { RemoteStunPresenter(get()) }
        viewModel { RemoteNetworkQualityPresenter(get()) }
        viewModel { parameters -> RemotePeerModel(parameters.get(), get()) }
        viewModel { parameters -> RemoteInboxModel(parameters.get(), get()) }

        navigation<RemoteRoute.Setup> { RemoteSetupBinding() }
        navigation<RemoteRoute.Stats> { RemoteDashboardBinding(DashboardPage.Stats) }
        navigation<RemoteRoute.ProxySet> { RemoteDashboardBinding(DashboardPage.ProxySet) }
        navigation<RemoteRoute.Connections> { RemoteDashboardBinding(DashboardPage.Connections) }
        navigation<RemoteRoute.Logs> { RemoteLogBinding() }
        navigation<RemoteRoute.Tools> { RemoteNetworkBinding() }
        navigation<RemoteRoute.Settings> { SettingsScreen() }
        navigation<RemoteRoute.GeneralSettings> { GeneralSettingsScreen() }
        navigation<RemoteRoute.About> { RemoteAboutBinding() }
        navigation<RemoteRoute.Libraries> {
            val navigator = LocalNavigator.current
            LibrariesScreen(onBackPress = { navigator.popBackStack() })
        }
        navigation<RemoteRoute.Servers> { RemoteServersBinding() }
        navigation<RemoteRoute.ServerEdit> { RemoteServerEditorBinding(it.id) }
        navigation<RemoteRoute.Stun> { RemoteStunBinding() }
        navigation<RemoteRoute.NetworkQuality> { RemoteQualityBinding() }
        navigation<RemoteRoute.Endpoint> { RemoteEndpointBinding(it.tag) }
        navigation<RemoteRoute.Peer> { RemotePeerBinding(it.tag, it.id) }
        navigation<RemoteRoute.ExitNode> { RemoteExitNodeBinding(it.tag) }
        navigation<RemoteRoute.Inbox> { RemoteInboxBinding(it.tag) }
        navigation<RemoteRoute.Ssh> { RemoteSshBinding() }
    }
}
