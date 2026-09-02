package fr.husi.ui.remote

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.remote.RemoteControlManager
import fr.husi.database.RemoteServer
import fr.husi.database.normalizeRemoteServerURL
import fr.husi.ktx.readableMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Outcome of the connection test that runs against the edited server. */
@Immutable
sealed interface RemoteServerTestState {
    /** Nothing to test yet: the address is still empty. */
    data object Idle : RemoteServerTestState

    data object Testing : RemoteServerTestState

    /** The address cannot be parsed, so no request was made. */
    data object InvalidURL : RemoteServerTestState

    data class Success(val version: String) : RemoteServerTestState

    data class Failure(val message: String) : RemoteServerTestState
}

@Immutable
data class RemoteServerEditUiState(
    val name: String = "",
    val url: String = "",
    val secret: String = "",
    val urlError: Boolean = false,
    val test: RemoteServerTestState = RemoteServerTestState.Idle,
    val isNew: Boolean = true,
)

@Stable
class RemoteServerEditViewModel(
    private val serverId: Long,
    private val remoteControl: RemoteControlManager = GlobalContext.get().get(),
) : ViewModel() {

    val uiState: StateFlow<RemoteServerEditUiState>
        field = MutableStateFlow(RemoteServerEditUiState(isNew = serverId <= 0L))

    private var testJob: Job? = null

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
                startTest(Duration.ZERO)
            }
        }
    }

    fun setName(name: String) {
        uiState.update { it.copy(name = name) }
    }

    fun setUrl(url: String) {
        uiState.update { it.copy(url = url, urlError = false) }
        startTest(EDIT_DEBOUNCE)
    }

    fun setSecret(secret: String) {
        uiState.update { it.copy(secret = secret) }
        startTest(EDIT_DEBOUNCE)
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
        startTest(Duration.ZERO)
    }

    /**
     * Replaces the pending test with a new one for the current address and secret.
     * [debounce] lets keystrokes settle before the request leaves.
     */
    private fun startTest(debounce: Duration) {
        testJob?.cancel()
        val state = uiState.value
        if (state.url.isBlank()) {
            uiState.update { it.copy(test = RemoteServerTestState.Idle) }
            return
        }
        val normalized = normalizeRemoteServerURL(state.url)
        if (normalized == null) {
            uiState.update { it.copy(test = RemoteServerTestState.InvalidURL) }
            return
        }
        uiState.update { it.copy(test = RemoteServerTestState.Testing) }
        testJob = viewModelScope.launch {
            delay(debounce)
            val result = remoteControl.testConnection(normalized, state.secret)
            val test = result.fold(
                onSuccess = { version ->
                    RemoteServerTestState.Success(version.ifBlank { UNKNOWN_VERSION })
                },
                onFailure = { error -> RemoteServerTestState.Failure(error.readableMessage) },
            )
            uiState.update { it.copy(test = test) }
        }
    }

    private companion object {
        val EDIT_DEBOUNCE = 500.milliseconds
        const val UNKNOWN_VERSION = "unknown"
    }
}
