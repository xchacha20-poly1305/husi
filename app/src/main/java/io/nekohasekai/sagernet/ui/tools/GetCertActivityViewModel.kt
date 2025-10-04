package io.nekohasekai.sagernet.ui.tools

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.currentSocks5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore

@Stable
internal data class GetCertUiState(
    val server: String = "www.microsoft.com",
    val serverName: String = "",
    val protocol: String = "https",
    val format: String = "raw",
    val proxy: String = "",
    val isDoing: Boolean = false,
    val cert: String = "",
)

@Stable
internal sealed interface GetCertUiEvent {
    class Alert(val e: Exception) : GetCertUiEvent
}


@Stable
internal class GetCertActivityViewModel() : ViewModel() {
    private val _uiState = MutableStateFlow(GetCertUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<GetCertUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun initialize() {
        _uiState.update { it.copy(proxy = currentSocks5()?.string ?: "") }
    }

    fun launch() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            getCert(state.server, state.serverName, state.protocol, state.format, state.proxy)
        }
    }

    private suspend fun getCert(
        server: String,
        serverName: String,
        protocol: String,
        format: String,
        proxy: String,
    ) {
        _uiState.update {
            it.copy(isDoing = true, cert = "")
        }
        try {
            val cert = Libcore.getCert(server, serverName, protocol, format, proxy)
            _uiState.update {
                it.copy(cert = cert)
            }
        } catch (e: Exception) {
            Logs.e(e)
            _uiEvent.emit(GetCertUiEvent.Alert(e))
        } finally {
            _uiState.update { it.copy(isDoing = false) }
        }
    }

    fun setServer(server: String) {
        _uiState.update { it.copy(server = server) }
    }

    fun setServerName(serverName: String) {
        _uiState.update { it.copy(serverName = serverName) }
    }

    fun setProtocol(protocol: String) {
        _uiState.update { it.copy(protocol = protocol) }
    }

    fun setFormat(format: String) {
        _uiState.update { it.copy(format = format) }
    }

    fun setProxy(proxy: String) {
        _uiState.update { it.copy(proxy = proxy) }
    }

}
