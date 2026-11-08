package fr.husi.ui.remote

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.remote.RemoteControlManager
import fr.husi.core.remote.RemoteSessionState
import fr.husi.database.RemoteServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Immutable
data class RemoteControlUiState(
    val servers: List<RemoteServer> = emptyList(),
    val activeServerId: Long = RemoteControlManager.LOCAL_TARGET_ID,
    val isRemote: Boolean = false,
    val sessionState: RemoteSessionState? = null,
)

@Stable
class RemoteControlScreenViewModel(
    private val remoteControl: RemoteControlManager = GlobalContext.get().get(),
) : ViewModel() {

    val uiState: StateFlow<RemoteControlUiState>
        field = MutableStateFlow(RemoteControlUiState())

    init {
        viewModelScope.launch {
            combine(remoteControl.servers, remoteControl.session) { servers, session ->
                RemoteControlUiState(
                    servers = servers,
                    activeServerId = session?.server?.id ?: RemoteControlManager.LOCAL_TARGET_ID,
                    isRemote = session != null,
                    sessionState = session?.state,
                )
            }.collect { uiState.value = it }
        }
    }

    suspend fun selectLocal() {
        remoteControl.exitRemote()
    }

    suspend fun selectServer(server: RemoteServer) {
        remoteControl.enterRemote(server)
    }

    suspend fun deleteServer(id: Long) {
        remoteControl.deleteServer(id)
    }
}
