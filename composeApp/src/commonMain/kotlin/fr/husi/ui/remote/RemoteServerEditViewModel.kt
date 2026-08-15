package fr.husi.ui.remote

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.RemoteServer
import fr.husi.database.normalizeRemoteServerURL
import fr.husi.ktx.readableMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

@Immutable
data class RemoteServerEditUiState(
    val name: String = "",
    val url: String = "",
    val secret: String = "",
    val urlError: Boolean = false,
    val testing: Boolean = false,
    val testVersion: String? = null,
    val testError: String? = null,
    val isNew: Boolean = true,
)

@Stable
class RemoteServerEditViewModel(
    private val serverId: Long,
    private val remoteControl: RemoteControlManager = GlobalContext.get().get(),
) : ViewModel() {

    val uiState: StateFlow<RemoteServerEditUiState>
        field = MutableStateFlow(RemoteServerEditUiState(isNew = serverId <= 0L))

    init {
        if (serverId > 0L) {
            viewModelScope.launch {
                val server = remoteControl.servers.first().firstOrNull { it.id == serverId }
                    ?: return@launch
                uiState.update {
                    it.copy(
                        name = server.name,
                        url = server.url,
                        secret = server.secret,
                        isNew = false,
                    )
                }
            }
        }
    }

    fun setName(name: String) {
        uiState.update { it.copy(name = name) }
    }

    fun setUrl(url: String) {
        uiState.update { it.copy(url = url, urlError = false) }
    }

    fun setSecret(secret: String) {
        uiState.update { it.copy(secret = secret) }
    }

    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        val normalized = normalizeRemoteServerURL(state.url)
        if (normalized == null) {
            uiState.update { it.copy(urlError = true) }
            return
        }
        val name = state.name.ifBlank { normalized }
        viewModelScope.launch {
            remoteControl.upsertServer(
                RemoteServer(
                    id = if (state.isNew) 0L else serverId,
                    name = name,
                    url = normalized,
                    secret = state.secret,
                ),
            )
            onSaved()
        }
    }

    fun testConnection() {
        val state = uiState.value
        val normalized = normalizeRemoteServerURL(state.url)
        if (normalized == null) {
            uiState.update { it.copy(urlError = true) }
            return
        }
        uiState.update { it.copy(testing = true, testError = null, testVersion = null) }
        viewModelScope.launch {
            val result = remoteControl.testConnection(normalized, state.secret)
            uiState.update { current ->
                result.fold(
                    onSuccess = { version ->
                        current.copy(
                            testing = false,
                            testVersion = version.ifBlank { "unknown" },
                            testError = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            testing = false,
                            testVersion = null,
                            testError = error.readableMessage,
                        )
                    },
                )
            }
        }
    }
}
