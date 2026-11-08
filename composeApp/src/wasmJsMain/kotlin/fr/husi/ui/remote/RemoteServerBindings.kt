package fr.husi.ui.remote

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import fr.husi.core.remote.RemoteSessionState
import fr.husi.remote.RemoteState
import fr.husi.ui.LocalNavigator
import fr.husi.ui.RemoteRoute
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun RemoteMenu(groupCount: Int, dismiss: () -> Unit) {
    val remote = koinInject<RemoteState>()
    val navigator = LocalNavigator.current
    RemoteTargetMenuContent(
        servers = remote.servers,
        activeId = remote.selected?.id,
        onSelectServer = { id -> remote.servers.firstOrNull { it.id == id }?.let(remote::connect) },
        groupIndex = 0,
        groupCount = groupCount,
        onManage = { navigator.navigateTo(RemoteRoute.Servers) },
        onDismiss = dismiss,
    )
}

@Composable
internal fun RemoteBanner() {
    val remote = koinInject<RemoteState>()
    remote.selected
        ?.takeIf { remote.connected || remote.connecting }
        ?.let { server ->
            RemoteSessionBannerContent(
                session =
                    RemoteSessionInfo(
                        server.name.ifBlank { server.url },
                        if (remote.connected) RemoteSessionState.CONNECTED
                        else RemoteSessionState.CONNECTING,
                        remote.startedAt,
                    ),
                windowInsets = WindowInsets(0, 0, 0, 0),
                onDisconnect = remote::disconnect,
            )
        }
}

@Composable
internal fun RemoteServersBinding() {
    val remote = koinInject<RemoteState>()
    val navigator = LocalNavigator.current
    RemoteControlContent(
        uiState =
            RemoteControlState(
                remote.servers,
                remote.selected?.id,
                true,
                when {
                    remote.connected -> RemoteSessionState.CONNECTED
                    remote.connecting -> RemoteSessionState.CONNECTING
                    else -> null
                },
            ),
        onBackPress = { navigator.popBackStack() },
        onEditServer = { navigator.navigateTo(RemoteRoute.ServerEdit(it)) },
        onSelectServer = { id ->
            remote.servers.firstOrNull { it.id == id }?.let(remote::connect)
            navigator.navigateToTopLevelRoute(RemoteRoute.Dashboard)
        },
        onDeleteServer = { id -> remote.servers.firstOrNull { it.id == id }?.let { remote.delete(it) } },
    )
}

@Composable
internal fun RemoteSetupBinding() {
    val editor = koinViewModel<RemoteServerEditor>(parameters = { parametersOf(null) })
    val remote = koinInject<RemoteState>()
    RemoteSetupScreen(
        uiState = editor.state,
        nameState = editor.nameState,
        urlState = editor.urlState,
        secretState = editor.secretState,
        onSave = { editor.save(remote::connect) },
        onTestConnection = { editor.testConnection() },
    )
}

@Composable
internal fun RemoteServerEditorBinding(id: Long?) {
    val editor = koinViewModel<RemoteServerEditor>(parameters = { parametersOf(id) })
    val navigator = LocalNavigator.current
    RemoteServerEditContent(
        uiState = editor.state,
        onBackPress = { navigator.popBackStack() },
        onSave = { editor.save { navigator.popBackStack() } },
        onSetName = editor::setName,
        onSetUrl = editor::setUrl,
        onSetSecret = editor::setSecret,
        onTestConnection = { editor.testConnection() },
    )
}
