package fr.husi.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun RemoteControlScreen(
    modifier: Modifier = Modifier,
    onBackPress: () -> Unit,
    onEditServer: (Long) -> Unit,
    viewModel: RemoteControlScreenViewModel = viewModel {
        RemoteControlScreenViewModel()
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RemoteControlContent(
        uiState =
            RemoteControlState(
                servers = state.servers,
                activeServerId = state.activeServerId,
                isRemote = state.isRemote,
                sessionState = state.sessionState,
            ),
        onBackPress = onBackPress,
        onEditServer = { onEditServer(it ?: 0L) },
        onSelectServer = { id ->
            state.servers.firstOrNull { it.id == id }?.let { viewModel.selectServer(it) }
        },
        onDeleteServer = { viewModel.deleteServer(it) },
        onSelectLocal = viewModel::selectLocal,
        modifier = modifier,
    )
}
