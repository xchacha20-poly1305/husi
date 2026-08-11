package fr.husi.ui.tools

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.husi.core.CoreClient
import fr.husi.ktx.Logs
import fr.husi.ktx.currentSocks5
import fr.husi.libcore.Libcore
import fr.husi.proto.v1.GetCertMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

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
internal class GetCertScreenViewModel(
    private val coreClient: CoreClient = GlobalContext.get().get(),
) : ViewModel() {

    val uiState: StateFlow<GetCertUiState>
        field = MutableStateFlow(GetCertUiState())

    init {
        initialize()
    }

    fun initialize() {
        uiState.update { it.copy(proxy = currentSocks5()?.string ?: "") }
    }

    fun launch() = viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        getCert(state.server, state.serverName, state.protocol, state.format, state.proxy)
    }

    private suspend fun getCert(
        server: String,
        serverName: String,
        protocol: String,
        format: Format,
        proxy: String,
    ) {
        uiState.update {
            it.copy(isDoing = true, cert = "", formatted = "")
        }
        try {
            val mode = when (protocol) {
                "quic" -> GetCertMode.GET_CERT_MODE_QUIC
                else -> GetCertMode.GET_CERT_MODE_HTTPS
            }
            val cert = coreClient.getCert(server, serverName, mode, proxy)
            uiState.update {
                it.copy(
                    cert = cert,
                    formatted = formatCert(cert, format),
                )
            }
        } catch (e: Exception) {
            Logs.e(e)
            uiState.update { state ->
                state.copy(alert = e)
            }
        } finally {
            uiState.update { it.copy(isDoing = false) }
        }
    }

    fun setServer(server: String) {
        uiState.update { it.copy(server = server) }
    }

    fun setServerName(serverName: String) {
        uiState.update { it.copy(serverName = serverName) }
    }

    fun setProtocol(protocol: String) {
        uiState.update { it.copy(protocol = protocol) }
    }

    fun setFormat(format: Format) = viewModelScope.launch {
        uiState.update { state ->
            state.copy(
                format = format,
                formatted = formatCert(state.cert, format),
            )
        }
    }

    fun setProxy(proxy: String) {
        uiState.update { it.copy(proxy = proxy) }
    }

    private fun formatCert(cert: String, format: Format): String {
        if (cert.isBlank()) return ""
        return when (format) {
            Format.Raw -> ""
            Format.V2RayPem -> Libcore.toV2RayPemHash(cert)
            Format.HysteriaHex -> Libcore.toHysteriaHexSha256(cert)
            Format.SingPublicKeyBase64 -> Libcore.toSingPublicKeySha256(cert)
        }
    }
}
