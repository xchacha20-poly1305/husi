package io.nekohasekai.sagernet.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.currentSocks5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore

@Immutable
internal data class GetCertUiState(
    val server: String = "www.microsoft.com",
    val serverName: String = "",
    val protocol: String = "https",
    val format: Format = Format.Raw,
    val proxy: String = "",
    val isDoing: Boolean = false,
    val cert: String = "",
    val formatted: String = "",
    val alert: Exception? = null,
)

@Immutable
internal enum class Format(val display: String) {
    Raw(""),
    V2RayPem("V2Ray"),
    HysteriaHex("Hysteria"),
    SingPublicKeyBase64("Public Key");

    override fun toString(): String {
        return display
    }
}

@Stable
internal class GetCertActivityViewModel() : ViewModel() {

    private val _uiState = MutableStateFlow(GetCertUiState())
    val uiState = _uiState.asStateFlow()

    fun initialize() {
        _uiState.update { it.copy(proxy = currentSocks5()?.string ?: "") }
    }

    fun launch() = viewModelScope.launch(Dispatchers.IO) {
        val state = _uiState.value
        getCert(state.server, state.serverName, state.protocol, state.format, state.proxy)
    }

    private suspend fun getCert(
        server: String,
        serverName: String,
        protocol: String,
        format: Format,
        proxy: String,
    ) {
        _uiState.update {
            it.copy(isDoing = true, cert = "", formatted = "")
        }
        try {
            val cert = Libcore.getCert(server, serverName, protocol, proxy)
            _uiState.update {
                it.copy(
                    cert = cert,
                    formatted = formatCert(cert, format),
                )
            }
        } catch (e: Exception) {
            Logs.e(e)
            _uiState.update { state ->
                state.copy(alert = e)
            }
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

    fun setFormat(format: Format) = viewModelScope.launch {
        _uiState.update { state ->
            state.copy(
                format = format,
                formatted = formatCert(state.cert, format),
            )
        }
    }

    fun setProxy(proxy: String) {
        _uiState.update { it.copy(proxy = proxy) }
    }

    private fun formatCert(cert: String, format: Format): String {
        return when (format) {
            Format.Raw -> ""
            Format.V2RayPem -> Libcore.toV2RayPemHash(cert)
            Format.HysteriaHex -> Libcore.toHysteriaHexSha256(cert)
            Format.SingPublicKeyBase64 -> Libcore.toSingPublicKeySha256(cert)
        }
    }
}
